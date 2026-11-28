"""Validate sources and register immutable versions for review."""

import re
from datetime import UTC, date, datetime, time, timedelta
from pathlib import Path

from psycopg import Connection
from psycopg.rows import DictRow

from wa_knowledge_rag.audit import record
from wa_knowledge_rag.content import load_piece
from wa_knowledge_rag.ingestion.params import load_parameters
from wa_knowledge_rag.ingestion.validate import validate_piece
from wa_knowledge_rag.security.pii import contains_instruction, detect_pii, is_phone
from wa_knowledge_rag.store.repositories import parameter_versions, piece_versions
from wa_knowledge_rag.workflow.approvals import latest_version, lock_subject
from wa_knowledge_rag.workflow.states import Status

CONTENT = Path("content")
_SLUG = re.compile(r"[a-z0-9]+(?:[-_][a-z0-9]+)*")


def ingest_piece(
    connection: Connection[DictRow], path: Path, *, proposal: bool, max_chars: int, changed_only: bool = False
) -> tuple[tuple[str, ...], str | None]:
    subject = "desconocido"
    version = None
    try:
        piece = load_piece(path)
        if not _SLUG.fullmatch(piece.id) or detect_pii(piece.id):
            raise ValueError("El id debe ser un slug sin datos personales")
        subject = piece.id
        lock_subject(connection, "piece", subject)
        current = latest_version(connection, "piece", subject)
        version = current["version"] if current else None
        warnings = validate_piece(piece, load_parameters(), max_chars_whatsapp=max_chars)
        valid_from = datetime.combine(piece.valid_from, time.min, UTC)
        valid_to = (
            datetime.combine(piece.valid_to + timedelta(days=1), time.min, UTC)
            if piece.valid_to and piece.valid_to != date.max
            else None
        )
    except (OSError, UnicodeError):
        record(connection, "reject", subject=subject, version=version)
        return (), "No se pudo leer el archivo de contenido o de parámetros"
    except ValueError as error:
        record(connection, "reject", subject=subject, version=version)
        return (), str(error)

    content_hash = piece.content_hash
    if not current or current["content_hash"] != content_hash:
        version = 1 if version is None else version + 1
        connection.execute("INSERT INTO kb_piece (piece_id) VALUES (%s) ON CONFLICT DO NOTHING", (subject,))
        piece_versions.insert(
            connection,
            piece_id=subject,
            version=version,
            title=piece.title,
            type=piece.type,
            family=piece.family,
            audience=piece.audience,
            owner_area=piece.owner_area,
            language=piece.language,
            status=Status.PROPOSAL if proposal else Status.DRAFT,
            body_template=piece.text,
            source_refs=piece.source_refs,
            section=piece.section,
            content_hash=content_hash,
            valid_from=valid_from,
            valid_to=valid_to,
        )
    elif changed_only:
        return (), None
    record(connection, "ingest", subject=subject, version=version)
    return warnings, None


def ingest_parameters(connection: Connection[DictRow], *, changed_only: bool = False) -> list[str]:
    try:
        candidates = load_parameters()
    except (OSError, UnicodeError, ValueError):
        record(connection, "reject", subject="desconocido", subject_kind="parameter")
        return ["No se pudo cargar el juego de parámetros"]

    errors = []
    for name, candidate in sorted(candidates.items()):
        if detect_pii(name):
            record(connection, "reject", subject="desconocido", subject_kind="parameter")
            errors.append("Nombre de parámetro inválido")
            continue
        lock_subject(connection, "parameter", name)
        current = latest_version(connection, "parameter", name)
        version: int = current["version"] if current else 0
        value = candidate.value
        # A configured bank support number is eligible for review. Publication still requires approval.
        if (
            not value.strip()
            or "{{" in value
            or contains_instruction(value)
            or detect_pii(value, approved_values={value} if is_phone(value) else ())
        ):
            record(connection, "reject", subject=name, version=version or None, subject_kind="parameter")
            errors.append("Valor de parámetro inválido")
            continue
        if not current or (current["value"], current["is_test"]) != (value, candidate.is_test):
            version += 1
            parameter_versions.insert(
                connection,
                name=name,
                version=version,
                status=Status.IN_REVIEW,
                value=value,
                is_test=candidate.is_test,
            )
        elif changed_only:
            continue
        record(connection, "ingest", subject=name, version=version, subject_kind="parameter")
    return errors


def inventory(connection: Connection[DictRow], *, family: str | None, status: str | None) -> list[DictRow]:
    return connection.execute(
        """
        SELECT piece_id, version, family, status FROM kb_piece_version
        WHERE (%(family)s::text IS NULL OR family = %(family)s)
          AND (%(status)s::text IS NULL OR status = %(status)s)
        ORDER BY piece_id, version DESC
        """,
        {"family": family, "status": status},
    ).fetchall()
