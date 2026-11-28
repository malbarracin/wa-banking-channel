"""Check the required public FAQs against drafts or the active release."""

from pathlib import Path

import yaml
from pydantic import TypeAdapter, ValidationError

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.content import Audience, Family, NonBlank, PieceType, load_piece
from wa_knowledge_rag.store.database import transaction

_MANIFEST = TypeAdapter(dict[Family, dict[NonBlank, NonBlank]])
_PUBLISHED = """
    SELECT p.family, p.piece_id
    FROM kb_release r
    JOIN kb_release_item i USING (release_no)
    JOIN kb_piece_version p USING (piece_id, version)
    WHERE r.is_active AND p.type = 'faq' AND p.audience = 'publico'
      AND p.status IN ('aprobada', 'publicada')
      AND p.valid_from <= now() AND (p.valid_to IS NULL OR p.valid_to > now())
"""


def _required(content: Path) -> dict[Family, dict[str, str]]:
    try:
        manifest = _MANIFEST.validate_python(yaml.safe_load((content / "coverage.yaml").read_text(encoding="utf-8")))
    except (OSError, yaml.YAMLError, ValidationError):
        raise ValueError("No se pudo leer un manifiesto content/coverage.yaml válido.") from None

    if set(manifest) != set(Family) or not all(manifest.values()):
        raise ValueError("coverage.yaml debe declarar temas para las cuatro familias.")
    identifiers = [piece_id for topics in manifest.values() for piece_id in topics.values()]
    if len(identifiers) != len(set(identifiers)):
        raise ValueError("coverage.yaml debe asignar una pieza distinta a cada tema.")
    return manifest


def _drafts(content: Path) -> set[tuple[str, str]]:
    available: set[tuple[str, str]] = set()
    identifiers: set[str] = set()
    for family in Family:
        for path in sorted((content / family).rglob("*.md")):
            try:
                piece = load_piece(path)
            except (ValueError, OSError) as error:
                raise ValueError(f"No se pudo cargar {path.name}: {error}") from None
            if piece.id in identifiers:
                raise ValueError(f"Identificador de pieza duplicado: {piece.id}.")
            identifiers.add(piece.id)
            if piece.family == family and piece.type == PieceType.FAQ and piece.audience == Audience.PUBLIC:
                available.add((family, piece.id))
    return available


def _published(settings: Settings) -> set[tuple[str, str]]:
    with transaction(settings, query=True) as connection:
        return {(row["family"], row["piece_id"]) for row in connection.execute(_PUBLISHED)}


def check_coverage(content: Path, settings: Settings, *, published: bool) -> list[str]:
    """Return a diagnostic per required topic without an eligible FAQ."""
    required = _required(content)
    available = _published(settings) if published else _drafts(content)
    scope = "la publicación activa" if published else "los archivos fuente"
    return [
        f"Falta la FAQ {piece_id} para {family}/{topic} en {scope}."
        for family, topics in required.items()
        for topic, piece_id in topics.items()
        if (family, piece_id) not in available
    ]
