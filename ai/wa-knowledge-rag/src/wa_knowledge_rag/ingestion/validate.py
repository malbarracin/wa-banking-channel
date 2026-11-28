"""Validate draft knowledge before it enters the approval workflow."""

from collections.abc import Collection

from wa_knowledge_rag.content import PieceSource
from wa_knowledge_rag.ingestion.params import validate_parameters
from wa_knowledge_rag.security.pii import contains_instruction, detect_pii


def validate_piece(
    piece: PieceSource, known_parameters: Collection[str], *, max_chars_whatsapp: int
) -> tuple[str, ...]:
    """Reject unsafe content and return non-blocking editorial notices."""
    for value in (piece.text, piece.id, piece.title, piece.owner_area, *piece.source_refs, piece.section or ""):
        if findings := detect_pii(value):
            kinds = ", ".join(sorted({finding.kind for finding in findings}))
            raise ValueError(f"La pieza contiene datos personales ({kinds})")
        if contains_instruction(value):
            raise ValueError("La pieza contiene un patrón de instrucción")

    validate_parameters(piece.text, known_parameters)
    if len(piece.text) > max_chars_whatsapp:
        return (f"El texto supera KB_MAX_CHARS_WHATSAPP ({max_chars_whatsapp})",)
    return ()
