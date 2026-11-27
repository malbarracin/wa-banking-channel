"""Build and activate immutable snapshots of approved knowledge."""

import json
from collections.abc import Callable
from datetime import datetime
from hashlib import sha256
from typing import Literal

from pgvector import Vector
from psycopg import Connection
from psycopg.rows import DictRow

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.chunking import chunk_piece
from wa_knowledge_rag.ingestion.embeddings import Embedder
from wa_knowledge_rag.ingestion.params import parameter_names, render_parameters
from wa_knowledge_rag.security.pii import contains_instruction, detect_pii, is_phone
from wa_knowledge_rag.store.repositories import (
    approvals,
    audit,
    chunks,
    piece_versions,
    release_items,
    releases,
)
from wa_knowledge_rag.workflow.approvals import SubjectKind, latest_version, load_policy, lock_subject
from wa_knowledge_rag.workflow.states import Status

type Reason = Literal["publish", "sync", "expire", "retire", "rollback"]
_ITEM_FIELDS = ("piece_id", "version", "rendered_hash", "parameter_versions", "approval_kind", "approved_at")


def lock_releases(connection: Connection[DictRow]) -> None:
    connection.execute("SELECT pg_advisory_xact_lock(hashtextextended('kb-workflow:releases', 0))")


def _rows(connection: Connection[DictRow], release_no: int | None) -> dict[str, DictRow]:
    return {row["piece_id"]: row for row in release_items.find(connection, release_no=release_no)}


def _active(connection: Connection[DictRow]) -> int | None:
    active = releases.find(connection, is_active=True)
    return active[0]["release_no"] if active else None


def _activate(connection: Connection[DictRow], parent: int | None, items: dict[str, DictRow], reason: Reason) -> int:
    if parent is not None:
        releases.set_active(connection, release_no=parent, is_active=False)
    number: int = releases.insert(connection, parent_release_no=parent, reason=reason, is_active=True)["release_no"]
    for item in items.values():
        release_items.insert(connection, release_no=number, **{key: item[key] for key in _ITEM_FIELDS})
        piece_versions.set_piece_status(
            connection, piece_id=item["piece_id"], version=item["version"], status=Status.PUBLISHED
        )
    return number


def _review(
    connection: Connection[DictRow], kind: SubjectKind, row: DictRow, roles: set[str], real: bool
) -> tuple[bool, datetime]:
    subject = row["piece_id"] if kind == "piece" else row["name"]
    key = "piece_id" if kind == "piece" else "parameter_name"
    records = approvals.find(connection, subject_kind=kind, version=row["version"], **{key: subject})
    approved_at: dict[str, datetime] = {}
    real_at: dict[str, datetime] = {}
    for approval in sorted(records, key=lambda item: item["approved_at"]):
        approved_at[approval["role"]] = approval["approved_at"]
        if not approval["is_test"]:
            real_at[approval["role"]] = approval["approved_at"]
    if missing := roles - approved_at.keys():
        raise ValueError(f"{subject}: falta aprobación de {', '.join(sorted(missing))}")
    test = bool(roles - real_at.keys()) or (kind == "parameter" and row["is_test"])
    if real and test:
        raise ValueError(f"{subject}: el entorno real rechaza aprobaciones o valores de prueba")
    dates = approved_at if test else real_at
    return test, max(dates[role] for role in roles)


def _parameters(connection: Connection[DictRow], name: str) -> list[DictRow]:
    return connection.execute(
        "SELECT * FROM kb_parameter_version WHERE name = %s AND status = %s ORDER BY version DESC",
        (name, Status.APPROVED),
    ).fetchall()


def _valid(connection: Connection[DictRow], version: DictRow) -> bool:
    row = connection.execute(
        "SELECT %s::timestamptz <= statement_timestamp() AND "
        "(%s::timestamptz IS NULL OR %s::timestamptz > statement_timestamp()) AS valid",
        (version["valid_from"], version["valid_to"], version["valid_to"]),
    ).fetchone()
    return bool(row and row["valid"])


def _version(connection: Connection[DictRow], item: DictRow) -> DictRow:
    return piece_versions.find(connection, piece_id=item["piece_id"], version=item["version"])[0]


def _check_text(text: str, allowed: set[str]) -> None:
    if detect_pii(text, approved_values=allowed):
        raise ValueError("El contenido renderizado contiene datos personales")
    if contains_instruction(text):
        raise ValueError("El contenido renderizado contiene un patrón de instrucción")


def _render(
    connection: Connection[DictRow],
    piece: DictRow,
    policy: dict[str, set[str]],
    settings: Settings,
    embedder: Callable[[], Embedder],
) -> DictRow:
    subject = piece["piece_id"]
    real = settings.environment == "real"
    test, approved_at = _review(connection, "piece", piece, policy[piece["type"]], real)
    template = piece["body_template"]
    values: dict[str, str] = {}
    versions: dict[str, int] = {}
    for name in parameter_names(template):
        parameters = _parameters(connection, name)
        if not parameters:
            raise ValueError(f"Parámetro sin valor aprobado: {name}")
        parameter = parameters[0]
        parameter_test, _ = _review(connection, "parameter", parameter, policy["parameter"], real)
        values[name] = parameter["value"]
        versions[name] = parameter["version"]
        test |= parameter_test

    body = render_parameters(template, values)
    title = piece["title"]
    section = piece["section"]
    if any("{{" in text for text in (title, section or "")):
        raise ValueError("Solo se admiten parámetros en el cuerpo de la pieza")
    allowed = {value for value in values.values() if is_phone(value)}
    for text, exceptions in ((body, allowed), (title, set()), (section or "", set())):
        _check_text(text, exceptions)
    fragments = chunk_piece(piece["type"], title, body)
    if not fragments:
        raise ValueError("La pieza no tiene fragmentos publicables")
    rendered_hash = sha256(json.dumps([piece["type"], title, body], ensure_ascii=False).encode()).hexdigest()
    identity = {"piece_id": subject, "version": piece["version"], "rendered_hash": rendered_hash}
    indexed = connection.execute(
        """
        SELECT embedding_model FROM kb_chunk WHERE piece_id = %s AND version = %s AND rendered_hash = %s LIMIT 1
        """,
        (subject, piece["version"], rendered_hash),
    ).fetchone()
    if indexed is None:
        previous = connection.execute(
            """
            SELECT DISTINCT ON (chunk_no) embedding FROM kb_chunk
            WHERE piece_id = %s AND rendered_hash = %s AND embedding_model = %s
            ORDER BY chunk_no, version DESC
            """,
            (subject, rendered_hash, settings.embedding_model),
        ).fetchall()
        vectors: list[list[float] | Vector] = (
            [row["embedding"] for row in previous]
            if len(previous) == len(fragments)
            else list(embedder().embed_documents([fragment.embedding_text for fragment in fragments]))
        )
        for ordinal, (fragment, embedding) in enumerate(zip(fragments, vectors, strict=True)):
            chunks.insert(
                connection,
                **identity,
                chunk_no=ordinal,
                title=fragment.title,
                text=fragment.text,
                section=fragment.section or section,
                embedding=embedding,
                embedding_model=settings.embedding_model,
                family=piece["family"],
                type=piece["type"],
                audience=piece["audience"],
            )
    elif indexed["embedding_model"] != settings.embedding_model:
        raise ValueError("El modelo del índice cambió y requiere reindexación")
    return {
        **identity,
        "parameter_versions": versions,
        "approval_kind": "prueba" if test else "real",
        "approved_at": approved_at,
    }


def _snapshot_approval(connection: Connection[DictRow], item: DictRow, policy: dict[str, set[str]], real: bool) -> None:
    version = _version(connection, item)
    lock_subject(connection, "piece", version["piece_id"])
    _review(connection, "piece", version, policy[version["type"]], real)
    references = item["parameter_versions"]
    if set(references) != set(parameter_names(version["body_template"])):
        raise ValueError("La publicación histórica carece de referencias verificables de parámetros")
    for name, number in references.items():
        rows = [row for row in _parameters(connection, name) if row["version"] == number]
        if not rows:
            raise ValueError(f"Parámetro sin valor aprobado: {name}")
        _review(connection, "parameter", rows[0], policy["parameter"], real)
    if real and item["approval_kind"] == "prueba":
        raise ValueError("El entorno real rechaza publicaciones históricas de prueba")


def _candidate(
    connection: Connection[DictRow], subject: str, previous: DictRow | None, *, explicit: bool
) -> DictRow | None:
    latest = latest_version(connection, "piece", subject)
    if explicit:
        if latest is None or latest["status"] == Status.RETIRED:
            raise ValueError("No existe una versión publicable de la pieza")
        return latest
    pending = connection.execute(
        """
        SELECT * FROM kb_piece_version
        WHERE piece_id = %(subject)s AND status = %(status)s
          AND version > coalesce(%(previous)s, (
              SELECT max(version) FROM kb_release_item WHERE piece_id = %(subject)s
          ), 0)
        ORDER BY version DESC
        """,
        {"subject": subject, "status": Status.APPROVED, "previous": previous["version"] if previous else None},
    ).fetchall()
    for row in pending:
        if _valid(connection, row):
            return row
    return _version(connection, previous) if previous is not None else None


def publish(
    connection: Connection[DictRow],
    settings: Settings,
    embedder: Callable[[], Embedder],
    *,
    subject: str | None = None,
    sync: bool = False,
) -> tuple[int | None, str | None]:
    lock_releases(connection)
    parent = _active(connection)
    current = _rows(connection, parent)
    action: Reason = "sync" if sync else "publish"
    try:
        with connection.transaction():
            policy = load_policy(settings.approval_policy_path)
            subjects = (
                [subject]
                if subject is not None
                else [row["piece_id"] for row in connection.execute("SELECT piece_id FROM kb_piece ORDER BY piece_id")]
            )
            for identifier in sorted(set(subjects) | current.keys()):
                lock_subject(connection, "piece", identifier)
            candidates = [
                candidate
                for identifier in subjects
                if (
                    candidate := _candidate(
                        connection, identifier, current.get(identifier), explicit=subject is not None
                    )
                )
                is not None
            ]
            names = {name for piece in candidates for name in parameter_names(piece["body_template"])}
            for name in sorted(names):
                lock_subject(connection, "parameter", name)
            items = current.copy()
            expired = {
                identifier
                for identifier, item in current.items()
                if sync and not _valid(connection, _version(connection, item))
            }
            for identifier in expired:
                del items[identifier]
            rendered = False
            for piece in candidates:
                if not _valid(connection, piece):
                    if subject is not None:
                        raise ValueError("La pieza está fuera de vigencia")
                    continue
                item = _render(connection, piece, policy, settings, embedder)
                if any(item[key] != items.get(piece["piece_id"], {}).get(key) for key in _ITEM_FIELDS):
                    items[piece["piece_id"]] = item
                    rendered = True
            if settings.environment == "real":
                for item in items.values():
                    _snapshot_approval(connection, item, policy, True)
            reason: Reason = "expire" if expired else action
            number = _activate(connection, parent, items, reason) if rendered or expired else parent
            safe_subject = candidates[0]["piece_id"] if subject is not None else "release"
            audit.insert(connection, action=action, subject=safe_subject, release_no=number)
    except ValueError as error:
        audit.insert(connection, action="reject", subject="release", release_no=parent, detail={"operation": action})
        return parent, str(error)
    return number, None


def restore_or_retire(
    connection: Connection[DictRow],
    settings: Settings,
    *,
    retire: bool = False,
    subject: str | None = None,
    target_release: int | None = None,
    target_version: int | None = None,
) -> tuple[int | None, str | None]:
    lock_releases(connection)
    parent = _active(connection)
    items = _rows(connection, parent)
    action: Reason = "retire" if retire else "rollback"
    try:
        if parent is None:
            raise ValueError("No hay una publicación activa")
        if retire:
            if subject is None or subject not in items:
                raise ValueError("La pieza no está en la publicación activa")
            lock_subject(connection, "piece", subject)
            removed = items.pop(subject)
        elif subject is not None:
            row = connection.execute(
                """
                SELECT * FROM kb_release_item WHERE piece_id = %s AND version = %s
                ORDER BY release_no DESC LIMIT 1
                """,
                (subject, target_version),
            ).fetchone()
            if row is None or not _valid(connection, _version(connection, row)):
                raise ValueError("La versión solicitada no tiene una publicación vigente")
            items[subject] = row
        else:
            if target_release is None:
                target_release = releases.find(connection, release_no=parent)[0]["parent_release_no"]
            if (
                target_release is None
                or target_release >= parent
                or not releases.find(connection, release_no=target_release)
            ):
                raise ValueError("No existe la publicación anterior solicitada")
            items = {
                identifier: item
                for identifier, item in _rows(connection, target_release).items()
                if _valid(connection, _version(connection, item))
            }
        policy = load_policy(settings.approval_policy_path)
        for item in items.values():
            _snapshot_approval(connection, item, policy, settings.environment == "real")
    except ValueError as error:
        audit.insert(connection, action="reject", subject="release", release_no=parent, detail={"operation": action})
        return parent, str(error)
    if retire:
        piece_versions.set_piece_status(
            connection, piece_id=removed["piece_id"], version=removed["version"], status=Status.RETIRED
        )
    number = _activate(connection, parent, items, action)
    audit.insert(connection, action=action, subject=subject or "release", release_no=number)
    return number, None


def verify_pii(connection: Connection[DictRow]) -> str | None:
    """Scan published fragments with the parameter versions recorded for each rendering."""
    for item in connection.execute(
        "SELECT DISTINCT piece_id, version, rendered_hash, parameter_versions FROM kb_release_item"
    ):
        allowed = set()
        for name, version in item["parameter_versions"].items():
            for parameter in _parameters(connection, name):
                if parameter["version"] == version and is_phone(parameter["value"]):
                    allowed.add(parameter["value"])
        for chunk in chunks.find(
            connection, piece_id=item["piece_id"], version=item["version"], rendered_hash=item["rendered_hash"]
        ):
            if (
                detect_pii(chunk["text"], approved_values=allowed)
                or detect_pii(chunk["title"])
                or detect_pii(chunk["section"] or "", approved_values=allowed)
            ):
                return "Se encontraron datos personales en contenido publicado"
    return None
