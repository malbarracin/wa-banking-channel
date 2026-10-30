"""Orquesta KNN coseno + ts_rank español y RRF implementados por el store."""

from collections.abc import Sequence
from typing import Protocol

import structlog

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.retrieval.formatter import InsufficientResponse, RetrievalResponse, format_results
from wa_knowledge_rag.store.models import Audience, SearchHit


class SearchRepository(Protocol):
    def search(
        self, query: str, embedding: Sequence[float], *, audience: Audience, version: int | None = None, top_k: int = 5
    ) -> list[SearchHit]: ...


class HybridRetriever:
    """No genera texto ni llama al modelo; internal requiere uso de razonamiento explícito."""

    def __init__(self, repository: SearchRepository, settings: Settings) -> None:
        self.repository = repository
        self.settings = settings

    def retrieve(
        self,
        query: str,
        embedding: Sequence[float],
        *,
        audience: Audience,
        correlation_id: str,
        kb_version: str,
        top_k: int | None = None,
        for_client: bool = True,
    ) -> RetrievalResponse | InsufficientResponse:
        """Cliente: solo public. Razonamiento: una audiencia exacta por consulta.

        correlation_id se propaga sin registrar consulta, fragmentos ni embeddings.
        kb_version es el identificador de publicación proporcionado por el llamador.
        """
        if audience not in ("public", "internal"):
            raise ValueError("Audiencia inválida")
        if not correlation_id.strip() or not kb_version.strip():
            raise ValueError("Contexto de consulta inválido")
        if for_client and audience != "public":
            return InsufficientResponse(correlationId=correlation_id)
        hits = self.repository.search(
            query, embedding, audience=audience, top_k=self.settings.rag_top_k if top_k is None else top_k
        )
        response = format_results(hits, settings=self.settings, kb_version=kb_version, correlation_id=correlation_id)
        structlog.get_logger().info(
            "knowledge_retrieved",
            correlationId=correlation_id,
            audience=audience,
            confidence=response.confidence,
            result_count=len(response.results),
        )
        return response
