"""FastAPI entry point for the knowledge retrieval service."""

from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from time import perf_counter
from typing import Annotated

import psycopg
import structlog
import uvicorn
from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from prometheus_client import CONTENT_TYPE_LATEST

from wa_knowledge_rag.api.errors import ApiError, ErrorResponse, error_response
from wa_knowledge_rag.api.schemas import RetrieveRequest, RetrieveResponse
from wa_knowledge_rag.api.status import Metrics, health
from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.content import Audience
from wa_knowledge_rag.ingestion.embeddings import Embedder, EmbeddingProviderUnavailable, GoogleEmbedder
from wa_knowledge_rag.observability.logging import configure_logging, correlation_context, get_correlation_id
from wa_knowledge_rag.retrieval.service import KnowledgeUnavailable, RetrievalFilters, retrieve
from wa_knowledge_rag.security.consumers import Consumer, authenticate, load_consumers
from wa_knowledge_rag.security.pii import mask_pii
from wa_knowledge_rag.store.database import transaction
from wa_knowledge_rag.store.repositories import query_log

_CORRELATION_HEADER = "X-Correlation-Id"
logger = structlog.get_logger()


def create_app(settings: Settings | None = None, *, embedder: Embedder | None = None) -> FastAPI:
    settings = settings if settings is not None else Settings()
    consumers = load_consumers(settings.consumers)
    metrics = Metrics()
    active_embedder = embedder

    @asynccontextmanager
    async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
        nonlocal active_embedder
        configure_logging()
        owned = GoogleEmbedder(settings) if embedder is None and settings.google_api_key else None
        active_embedder = owned or embedder
        try:
            yield
        finally:
            if owned is not None:
                owned.close()

    app = FastAPI(lifespan=lifespan)

    @app.middleware("http")
    async def correlate(request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
        correlation_id = request.headers.get(_CORRELATION_HEADER, "")
        with correlation_context(correlation_id):
            try:
                if request.url.path == "/v1/retrieve" and request.method == "POST" and not correlation_id.strip():
                    raise ApiError("VALIDATION_ERROR")
                response = await call_next(request)
            except ApiError as error:
                response = error_response(error.code, correlation_id)
            except Exception:
                logger.error("kb_request_failed")
                response = error_response("INTERNAL_ERROR", correlation_id)
        if correlation_id or (request.url.path == "/v1/retrieve" and request.method == "POST"):
            response.headers[_CORRELATION_HEADER] = correlation_id
        return response

    @app.exception_handler(RequestValidationError)
    async def invalid_request(_request: Request, _error: RequestValidationError) -> JSONResponse:
        return error_response("VALIDATION_ERROR", get_correlation_id() or "")

    def require_consumer(request: Request) -> Consumer:
        consumer = authenticate(request.headers.get("Authorization", ""), consumers)
        if consumer is None:
            raise ApiError("UNAUTHENTICATED")
        return consumer

    @app.post(
        "/v1/retrieve",
        response_model=RetrieveResponse,
        responses={code: {"model": ErrorResponse} for code in (400, 401, 403, 503, 500)},
    )
    def retrieve_endpoint(
        body: RetrieveRequest, consumer: Annotated[Consumer, Depends(require_consumer)]
    ) -> RetrieveResponse:
        if body.audience == Audience.INTERNAL and not consumer.allow_internal:
            raise ApiError("AUDIENCE_FORBIDDEN")

        correlation_id = get_correlation_id() or ""
        filters: RetrievalFilters = {}
        if "family" in body.filters.model_fields_set:
            filters["family"] = [family.value for family in body.filters.family]
        if "type" in body.filters.model_fields_set:
            filters["type"] = [piece_type.value for piece_type in body.filters.type]

        started = perf_counter()
        with metrics.latency.time():
            try:
                if active_embedder is None:
                    raise EmbeddingProviderUnavailable()
                data = retrieve(
                    settings,
                    active_embedder,
                    body.query,
                    correlation_id=correlation_id,
                    filters=filters,
                    audience=body.audience.value,
                    top_k=body.topK,
                )
                response = RetrieveResponse.model_validate(data)
                latency_ms = (perf_counter() - started) * 1000
                cited = [f"{item.citation.pieceId}@{item.citation.version}" for item in response.results]
                with transaction(settings, query=True) as connection:
                    query_log.insert(
                        connection,
                        correlation_id=correlation_id,
                        consumer_id=consumer.id,
                        release_no=int(response.knowledgeVersion.removeprefix("kv-")),
                        cited=cited,
                        level=response.confidence.level,
                        top_score=response.confidence.score,
                        latency_ms=latency_ms,
                        query_len=len(body.query),
                    )
            except (KnowledgeUnavailable, EmbeddingProviderUnavailable, psycopg.Error) as error:
                if isinstance(error, EmbeddingProviderUnavailable) or isinstance(
                    error.__cause__, EmbeddingProviderUnavailable
                ):
                    metrics.embedding_errors.inc()
                logger.warning("kb_retrieve_unavailable", consumerId=consumer.id)
                raise ApiError("KNOWLEDGE_UNAVAILABLE") from None

        metrics.retrieved.labels(response.confidence.level).inc()
        logger.info(
            "kb_retrieve",
            consumerId=consumer.id,
            knowledgeVersion=response.knowledgeVersion,
            cited=cited,
            level=response.confidence.level,
            topScore=response.confidence.score,
            latencyMs=latency_ms,
            query=mask_pii(body.query),
        )
        return response

    @app.get("/health")
    def health_endpoint() -> JSONResponse:
        status, payload = health(settings)
        return JSONResponse(payload, status_code=status)

    @app.get("/metrics")
    def metrics_endpoint() -> Response:
        return Response(metrics.render(settings), headers={"Content-Type": CONTENT_TYPE_LATEST})

    return app


def main() -> None:
    settings = Settings()
    uvicorn.run(create_app(settings), host="0.0.0.0", port=settings.port)  # noqa: S104 - container HTTP listener
