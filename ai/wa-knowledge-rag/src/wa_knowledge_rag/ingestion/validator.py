"""Validación previa a cualquier llamada al proveedor o escritura."""

import re
from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field


class Source(BaseModel):
    source_id: UUID
    title: str = Field(min_length=1, max_length=500)
    content: str = Field(min_length=1, max_length=1_000_000)
    owner_area: str = Field(min_length=1)
    version: int = Field(gt=0)
    status: str
    approved_by: str | None = None
    approved_at: datetime | None = None
    audience: Literal["public", "internal"] = "internal"


class IngestionError(ValueError):
    """Código estable, nunca incluye el texto rechazado."""


_PII = re.compile(
    r"(?<!\d)\d{7,22}(?!\d)|\b\d{1,3}(?:\.\d{3}){2}\b"
    r"|\+?\d[\d ()-]{8,}\d"
    r"|\b(?:nombre|titular|cliente)\s*[:=].{1,120}\b(?:saldo|cuenta|tarjeta|dni|documento)\b"
    r"|(?-i:\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+).{0,80}"
    r"\b(?:saldo|cuenta|tarjeta|dni|documento)\s*[:=]?\s*\$?\d",
    re.IGNORECASE | re.DOTALL,
)
_INJECTION = re.compile(
    r"(?:ignor[aáe]|olvid[aáe]|ignore|disregard).{0,50}(?:instrucciones|instructions|prompt|reglas)"
    r"|(?:system|assistant)\s*:|<\|(?:system|im_start)\|>",
    re.IGNORECASE,
)


def validate(source: Source) -> None:
    if source.status != "approved":
        raise IngestionError("APPROVAL_REQUIRED")
    if not source.approved_by or not source.approved_by.strip() or source.approved_at is None:
        raise IngestionError("APPROVAL_TRACE_REQUIRED")
    if source.approved_at.utcoffset() is None:
        raise IngestionError("APPROVAL_TIMEZONE_REQUIRED")
    # Se inspeccionan también los metadatos que podrían terminar en citas.
    text = "\n".join((source.title, source.content, source.owner_area))
    if _PII.search(text):
        raise IngestionError("PII_DETECTED")
    if _INJECTION.search(text):
        raise IngestionError("INSTRUCTIONS_DETECTED")
