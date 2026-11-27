"""Audit workflow decisions with identifiers and controlled metadata."""

from typing import Literal

from psycopg import Connection
from psycopg.rows import DictRow

from wa_knowledge_rag.store.repositories import audit


def record(
    connection: Connection[DictRow],
    action: Literal["ingest", "reject", "submit", "approve"],
    *,
    subject: str,
    version: int | None = None,
    role: str | None = None,
    subject_kind: Literal["piece", "parameter"] = "piece",
    is_test: bool | None = None,
) -> None:
    # Missing subjects and roles are represented by fixed markers, never by raw input.
    audit.insert(
        connection,
        action=action,
        subject=subject,
        version=version,
        role=role or "sistema",
        detail={"subject_kind": subject_kind, "is_test": is_test},
    )
