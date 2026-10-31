"""Entrada acotada y composición de retrieval sin registrar contenido del cliente."""

from collections.abc import AsyncIterator
from typing import Protocol

import structlog
from google import genai
from google.genai import types
from pydantic import BaseModel, ConfigDict, Field

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.retrieval.formatter import InsufficientResponse, RetrievalResponse
from wa_knowledge_rag.retrieval.hybrid import HybridRetriever
from wa_knowledge_rag.store.db import connect
from wa_knowledge_rag.store.models import Audience
from wa_knowledge_rag.store.repo import KnowledgeRepository

Response = RetrievalResponse | InsufficientResponse


class RetrievalRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True, strict=True)
    query: str = Field(min_length=1, max_length=10000)
    audience: Audience
    correlationId: str = Field(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9._:-]+$")
    top_k: int = Field(default=5, ge=1, le=100)


class RetrievalService(Protocol):
    def retrieve(self, request: RetrievalRequest) -> Response: ...


class DatabaseRetrievalService:
    def retrieve(self, request: RetrievalRequest) -> Response:
        settings = Settings()
        if settings.google_api_key is None:
            raise RuntimeError("Proveedor no configurado")
        with genai.Client(
            api_key=settings.google_api_key.get_secret_value(), http_options=types.HttpOptions(timeout=10000)
        ) as client:
            embedded = client.models.embed_content(
                model=settings.embedding_model,
                contents=request.query,
                config=types.EmbedContentConfig(
                    output_dimensionality=settings.embedding_dimensions, task_type="RETRIEVAL_QUERY"
                ),
            )
        vectors = embedded.embeddings or []
        if len(vectors) != 1:
            raise RuntimeError("Embedding no disponible")
        with connect(settings) as connection:
            # Identifica las revisiones aprobadas en la misma transacción de lectura.
            connection.commit()
            connection.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            row = connection.execute(
                "SELECT md5(COALESCE(string_agg(id::text || ':' || version::text, ',' ORDER BY id), '')) AS version "
                "FROM kb_source WHERE status = 'approved'"
            ).fetchone()
            if row is None:
                raise RuntimeError("Versión no disponible")
            return HybridRetriever(KnowledgeRepository(connection), settings).retrieve(
                request.query,
                vectors[0].values or [],
                audience=request.audience,
                correlation_id=request.correlationId,
                kb_version=f"kb-{row['version']}",
                top_k=request.top_k,
                for_client=request.audience == "public",
            )


def get_service() -> RetrievalService:
    return DatabaseRetrievalService()


async def request_context(request: RetrievalRequest) -> AsyncIterator[RetrievalRequest]:
    tokens = structlog.contextvars.bind_contextvars(correlationId=request.correlationId)
    try:
        yield request
    finally:
        structlog.contextvars.reset_contextvars(**tokens)
