"""Split rendered knowledge pieces into citation-ready chunks."""

import re
from dataclasses import dataclass
from typing import Literal

type PieceType = Literal["faq", "mensaje", "guia", "politica"]

_MAX_CHARS = 1200
_OVERLAP = 150
_SECTION = re.compile(r"^ {0,3}##[ \t]+([^\r\n]+?)(?:[ \t]+#+)?[ \t]*\r?$", re.MULTILINE)


@dataclass(frozen=True, slots=True)
class Chunk:
    title: str
    text: str
    section: str | None

    @property
    def embedding_text(self) -> str:
        return f"{self.title}\n{self.text}"


def chunk_piece(piece_type: PieceType, title: str, text: str) -> list[Chunk]:
    """Use rendered titles and bodies, preserving the source order of sections."""
    if piece_type in ("faq", "mensaje"):
        return [Chunk(title, text, None)]

    chunks: list[Chunk] = []
    section = None
    start = 0
    for heading in _SECTION.finditer(text):
        chunks.extend(_split_section(title, section, text[start : heading.start()]))
        section = heading[1].strip()
        start = heading.end()
    chunks.extend(_split_section(title, section, text[start:]))
    return chunks


def _split_section(title: str, section: str | None, text: str) -> list[Chunk]:
    text = text.strip()
    chunks = []
    start = 0
    while start < len(text):
        end = min(start + _MAX_CHARS, len(text))
        chunks.append(Chunk(title, text[start:end], section))
        if end == len(text):
            break
        start = end - _OVERLAP
    return chunks
