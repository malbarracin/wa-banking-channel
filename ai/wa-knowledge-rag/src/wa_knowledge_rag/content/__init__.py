"""Markdown source loading and version fingerprints."""

import hashlib
import json
import re
from datetime import date
from enum import StrEnum
from pathlib import Path
from typing import Annotated, Literal

import yaml
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, ValidationError, ValidationInfo, field_validator

_FENCE = re.compile(r"^---[ \t]*$", re.MULTILINE)
NonBlank = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class PieceType(StrEnum):
    FAQ = "faq"
    GUIDE = "guia"
    POLICY = "politica"
    MESSAGE = "mensaje"


class Family(StrEnum):
    ACCOUNTS = "cuentas"
    CARDS = "tarjetas"
    INSURANCE = "seguros"
    CHANNEL = "canal"


class Audience(StrEnum):
    PUBLIC = "publico"
    INTERNAL = "interno"


class PieceSource(BaseModel):
    """Author-supplied metadata and body, with no publication state."""

    model_config = ConfigDict(extra="forbid", frozen=True, hide_input_in_errors=True)

    id: NonBlank
    title: NonBlank
    type: PieceType
    family: Family
    audience: Audience
    owner_area: NonBlank
    source_refs: Annotated[tuple[NonBlank, ...], Field(min_length=1)]
    valid_from: date
    valid_to: date | None = None
    section: NonBlank | None = None
    language: Literal["es"] = "es"
    text: Annotated[str, Field(pattern=r"\S")]

    @field_validator("valid_to")
    @classmethod
    def validate_dates(cls, value: date | None, info: ValidationInfo) -> date | None:
        valid_from = info.data.get("valid_from")
        if value is not None and isinstance(valid_from, date) and value < valid_from:
            raise ValueError("valid_to debe ser igual o posterior a valid_from")
        return value

    @property
    def content_hash(self) -> str:
        """Fingerprint the body and normalized metadata independent of YAML formatting."""
        canonical = json.dumps(self.model_dump(mode="json"), sort_keys=True, ensure_ascii=False)
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def load_piece(path: Path) -> PieceSource:
    """Read a UTF-8 Markdown piece with a leading YAML frontmatter block."""
    if path.suffix != ".md":
        raise ValueError("La pieza debe ser un archivo .md")
    opening, *blocks = _FENCE.split(path.read_text(encoding="utf-8-sig"), maxsplit=2)
    if opening or len(blocks) != 2:
        raise ValueError("La pieza requiere frontmatter YAML delimitado por ---")

    frontmatter, body = blocks
    try:
        metadata: object = yaml.safe_load(frontmatter)
    except yaml.YAMLError:
        raise ValueError("Frontmatter YAML inválido") from None
    if not isinstance(metadata, dict):
        raise ValueError("El frontmatter debe contener un mapa de metadatos")

    try:
        return PieceSource.model_validate({**metadata, "text": body.removeprefix("\n")})
    except ValidationError as error:
        fields = ", ".join(
            f"{item['loc'][0] if item['loc'] and item['type'] != 'extra_forbidden' else 'frontmatter'} ({item['type']})"
            for item in error.errors(include_input=False)
        )
        raise ValueError(f"Metadatos o texto inválidos: {fields}") from None
