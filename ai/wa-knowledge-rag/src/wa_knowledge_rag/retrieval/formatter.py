"""Serialización del contrato v1: citas obligatorias y ausencia explícita de evidencia."""

from collections.abc import Sequence
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.retrieval.scorer import confidence, normalized_score
from wa_knowledge_rag.store.models import SearchHit


class Citation(BaseModel):
    source_id: UUID
    title: str = Field(min_length=1)
    section: str | None
    version: int = Field(gt=0)
    source_uri: str | None


class Result(BaseModel):
    content: str = Field(min_length=1)
    citation: Citation
    score: float = Field(ge=0, le=1, allow_inf_nan=False)


class RetrievalResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)
    results: list[Result]
    confidence: Literal["high", "medium"]
    version: str = Field(min_length=1)
    correlation_id: str = Field(alias="correlationId", min_length=1)


class InsufficientResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)
    results: list[Result] = Field(default_factory=list, max_length=0)
    confidence: Literal["low"] = "low"
    reason: Literal["sin_respuesta_suficiente"] = "sin_respuesta_suficiente"
    correlation_id: str = Field(alias="correlationId", min_length=1)


def format_results(
    hits: Sequence[SearchHit], *, settings: Settings, kb_version: str, correlation_id: str
) -> RetrievalResponse | InsufficientResponse:
    """Ordena candidatos y omite también fragmentos individuales de baja confianza."""
    scored = sorted(((normalized_score(hit.score), hit) for hit in hits), key=lambda item: item[0], reverse=True)
    level = confidence(scored[0][0], settings) if scored else "low"
    if level == "low":
        return InsufficientResponse(correlationId=correlation_id)
    results = [
        Result(
            content=hit.content,
            citation=Citation(
                source_id=hit.source_id,
                title=hit.title,
                section=hit.section,
                version=hit.version,
                source_uri=hit.source_uri,
            ),
            score=score,
        )
        for score, hit in scored
        if confidence(score, settings) != "low"
    ]
    return RetrievalResponse(results=results, confidence=level, version=kb_version, correlationId=correlation_id)
