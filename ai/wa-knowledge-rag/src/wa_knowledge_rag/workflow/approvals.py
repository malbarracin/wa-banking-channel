"""Approval policy and transactional review of piece and parameter versions."""

import re
from pathlib import Path
from typing import Literal

import yaml
from psycopg import Connection, sql
from psycopg.rows import DictRow

from wa_knowledge_rag.audit import record
from wa_knowledge_rag.content import PieceType
from wa_knowledge_rag.security.pii import detect_pii
from wa_knowledge_rag.store.repositories import approvals, piece_versions
from wa_knowledge_rag.workflow.states import Status, require_transition

SubjectKind = Literal["piece", "parameter"]
_ROLES = re.compile(r"[a-z][a-z0-9_]{0,63}")


def load_policy(path: Path) -> dict[str, set[str]]:
    try:
        data: object = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, yaml.YAMLError):
        raise ValueError("No se pudo leer la política de aprobación") from None

    if not isinstance(data, dict) or set(data) != {*(item.value for item in PieceType), "parameter"}:
        raise ValueError("La política debe definir los roles de cada tipo de pieza y de parameter")

    policy: dict[str, set[str]] = {}
    for kind, roles in data.items():
        if (
            not isinstance(roles, list)
            or not roles
            or any(not isinstance(role, str) or not _ROLES.fullmatch(role) or detect_pii(role) for role in roles)
        ):
            raise ValueError("La política contiene una lista de roles inválida")
        if len(set(roles)) != len(roles):
            raise ValueError("La política contiene roles duplicados")
        policy[kind] = set(roles)
    return policy


def lock_subject(connection: Connection[DictRow], kind: SubjectKind, subject: str) -> None:
    """Serialize version creation and review, including the first version."""
    connection.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s, 0))", (f"kb-workflow:{kind}:{subject}",))


def latest_version(connection: Connection[DictRow], kind: SubjectKind, subject: str) -> DictRow | None:
    table, key = ("kb_piece_version", "piece_id") if kind == "piece" else ("kb_parameter_version", "name")
    statement = sql.SQL("SELECT * FROM {} WHERE {} = %s ORDER BY version DESC LIMIT 1").format(
        sql.Identifier(table), sql.Identifier(key)
    )
    return connection.execute(statement, (subject,)).fetchone()


def submit(connection: Connection[DictRow], subject: str) -> str | None:
    lock_subject(connection, "piece", subject)
    current = latest_version(connection, "piece", subject)
    if current is None:
        record(connection, "reject", subject="desconocido")
        return "Pieza inexistente"
    version: int = current["version"]
    try:
        require_transition(Status(current["status"]), Status.IN_REVIEW)
    except ValueError as error:
        record(connection, "reject", subject=subject, version=version)
        return str(error)

    piece_versions.set_piece_status(connection, piece_id=subject, version=version, status=Status.IN_REVIEW)
    record(connection, "submit", subject=subject, version=version)
    return None


def approve(
    connection: Connection[DictRow],
    kind: SubjectKind,
    subject: str,
    *,
    role: str,
    is_test: bool,
    real: bool,
    policy_path: Path,
) -> str | None:
    lock_subject(connection, kind, subject)
    current = latest_version(connection, kind, subject)
    try:
        policy = load_policy(policy_path)
    except ValueError as policy_error:
        record(
            connection,
            "reject",
            subject=subject if current is not None else "desconocido",
            version=current["version"] if current is not None else None,
            subject_kind=kind,
        )
        return str(policy_error)

    if current is None:
        record(connection, "reject", subject="desconocido", subject_kind=kind)
        return "Versión inexistente"

    version: int = current["version"]
    roles = policy[current["type"] if kind == "piece" else "parameter"]
    allowed_role = role if role in roles else None
    status = Status(current["status"])
    error = None
    if allowed_role is None:
        error = "Rol fuera de la política de aprobación"
    elif is_test and real:
        error = "El entorno real rechaza aprobaciones de prueba"
    elif kind == "parameter" and real and current["is_test"]:
        error = "El entorno real rechaza parámetros de prueba"
    elif status not in {Status.IN_REVIEW, Status.APPROVED}:
        error = "La versión debe estar en revisión para recibir aprobaciones"

    if error:
        record(connection, "reject", subject=subject, version=version, role=allowed_role, subject_kind=kind)
        return error

    key = "piece_id" if kind == "piece" else "parameter_name"
    identity = {key: subject, "subject_kind": kind, "version": version}
    existing = approvals.find(connection, **identity)
    if not any(row["role"] == role and row["is_test"] == is_test for row in existing):
        existing.append(approvals.insert(connection, **identity, role=role, is_test=is_test))
    fulfilled = {row["role"] for row in existing if not real or not row["is_test"]}

    if status == Status.IN_REVIEW and roles <= fulfilled:
        require_transition(status, Status.APPROVED)
        if kind == "piece":
            piece_versions.set_piece_status(connection, piece_id=subject, version=version, status=Status.APPROVED)
        else:
            connection.execute(
                "UPDATE kb_parameter_version SET status = %s, approved_at = now() WHERE name = %s AND version = %s",
                (Status.APPROVED, subject, version),
            )
    record(connection, "approve", subject=subject, version=version, role=role, subject_kind=kind, is_test=is_test)
    return None
