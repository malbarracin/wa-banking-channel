"""Secciones Markdown con ventanas de tokens léxicos (aproximación sin modelo)."""

import re
from dataclasses import dataclass
from uuid import UUID

from .validator import Source


@dataclass(frozen=True)
class Chunk:
    source_id: UUID
    version: int
    title: str
    section: str
    ordinal: int
    content: str
    audience: str


def chunk(source: Source, size: int = 500, overlap: int = 50) -> list[Chunk]:
    if not 0 <= overlap < size:
        raise ValueError("INVALID_WINDOW")
    parts = re.split(r"(?m)^#{1,6}\s+(.+?)\s*$", source.content)
    sections = [(source.title, parts[0])]
    sections.extend(zip(parts[1::2], parts[2::2], strict=True))
    result: list[Chunk] = []
    for section, body in sections:
        tokens = list(re.finditer(r"\w+|[^\w\s]", body))
        for start in range(0, len(tokens), size - overlap):
            end = min(start + size, len(tokens))
            result.append(
                Chunk(
                    source.source_id,
                    source.version,
                    source.title,
                    section,
                    len(result),
                    body[tokens[start].start() : tokens[end - 1].end()],
                    source.audience,
                )
            )
            if end == len(tokens):
                break
    if not result:
        raise ValueError("EMPTY_CONTENT")
    return result
