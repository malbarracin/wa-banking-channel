"""Entidades del store; no interpretan el contenido recuperado como instrucciones."""

from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field

Audience = Literal["public", "internal"]


class Source(BaseModel):
    id: UUID
    title: str
    owner_area: str
    audience: Audience
    version: int = Field(gt=0)
    approved_by: str
    approved_at: datetime
    status: Literal["draft", "approved", "retired"]
    source_uri: str | None = None
    metadata: dict[str, object] = Field(default_factory=dict)


class Chunk(BaseModel):
    id: UUID
    source_id: UUID
    version: int = Field(gt=0)
    ordinal: int = Field(ge=0)
    content: str
    section: str | None = None
    metadata: dict[str, object] = Field(default_factory=dict)
    embedding: list[float] | None = Field(default=None, min_length=768, max_length=768)


class SearchHit(BaseModel):
    """Candidato con procedencia y puntajes crudos para la capa de retrieval."""

    id: UUID
    source_id: UUID
    title: str
    version: int
    content: str
    section: str | None
    source_uri: str | None
    score: float
