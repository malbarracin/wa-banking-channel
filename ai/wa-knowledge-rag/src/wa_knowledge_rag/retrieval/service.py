"""Retrieve approved fragments and citations for an authorized consumer."""

from typing import Literal, TypedDict

import psycopg
from psycopg.rows import DictRow

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embeddings import Embedder, EmbeddingProviderUnavailable
from wa_knowledge_rag.retrieval.confidence import Confidence, assess_confidence
from wa_knowledge_rag.retrieval.hybrid import search
from wa_knowledge_rag.security.pii import mask_pii
from wa_knowledge_rag.store.database import transaction


class KnowledgeUnavailable(RuntimeError):
    """The active knowledge release or one of its dependencies is unavailable."""

    code = "KNOWLEDGE_UNAVAILABLE"

    def __init__(self) -> None:
        super().__init__("Base de conocimiento no disponible")


class RetrievalFilters(TypedDict, total=False):
    family: list[str]
    type: list[str]


class Citation(TypedDict):
    pieceId: str
    title: str
    type: str
    family: str
    audience: str
    version: int
    section: str | None
    ownerArea: str
    approvedAt: str
    approvalKind: Literal["real", "prueba"]


class Result(TypedDict):
    text: str
    score: float
    publishable: bool
    citation: Citation


class RetrievalResponse(TypedDict):
    correlationId: str
    knowledgeVersion: str
    confidence: Confidence
    results: list[Result]


def _result(row: DictRow) -> Result:
    return {
        "text": row["text"],
        "score": row["score"],
        "publishable": row["audience"] == "publico",
        "citation": {
            "pieceId": row["piece_id"],
            "title": row["title"],
            "type": row["type"],
            "family": row["family"],
            "audience": row["audience"],
            "version": row["version"],
            "section": row["section"],
            "ownerArea": row["owner_area"],
            "approvedAt": row["approved_at"].isoformat(),
            "approvalKind": "prueba" if row["is_test"] else "real",
        },
    }


def retrieve(
    settings: Settings,
    embedder: Embedder,
    query: str,
    *,
    correlation_id: str,
    filters: RetrievalFilters | None = None,
    audience: Literal["publico", "interno"] = "publico",
    top_k: int = 5,
) -> RetrievalResponse:
    """Run retrieval after the API has validated the request and audience permission."""
    if settings.database_url is None:
        raise KnowledgeUnavailable()

    masked_query = mask_pii(query)
    filters = filters or {}
    try:
        embedding = embedder.embed_query(masked_query)
        if not any(embedding):
            raise KnowledgeUnavailable()
        with transaction(settings, query=True) as connection:
            rows = search(
                connection,
                query=masked_query,
                embedding=embedding,
                model=settings.embedding_model,
                internal=audience == "interno",
                families=filters.get("family"),
                types=filters.get("type"),
                lexical_bonus=settings.lexical_bonus,
                top_k=top_k,
            )
    except (psycopg.Error, EmbeddingProviderUnavailable) as error:
        raise KnowledgeUnavailable() from error

    if not rows:
        raise KnowledgeUnavailable()

    results = [_result(row) for row in rows if row["piece_id"] is not None]
    confidence = assess_confidence(
        [result["score"] for result in results],
        min_relevance=settings.min_relevance,
        confidence_threshold=settings.confidence_threshold,
        ambiguity_margin=settings.ambiguity_margin,
    )
    return {
        "correlationId": correlation_id,
        "knowledgeVersion": f"kv-{rows[0]['release_no']}",
        "confidence": confidence,
        "results": [] if confidence["level"] == "sin_respuesta" else results,
    }
