"""Conexión sin efectos al importar y migraciones transaccionales serializadas."""

from collections.abc import Iterator
from contextlib import contextmanager
from importlib.resources import files

import psycopg
from pgvector.psycopg import register_vector
from psycopg.rows import DictRow, dict_row

from wa_knowledge_rag.config import Settings


def migrate(connection: psycopg.Connection[DictRow]) -> None:
    """Aplica cada SQL una sola vez; rollback completo ante cualquier fallo."""
    with connection.transaction():
        connection.execute("SELECT pg_advisory_xact_lock(810200136)")
        connection.execute("CREATE TABLE IF NOT EXISTS kb_migration (version TEXT PRIMARY KEY)")
        for resource in sorted(files(__package__).joinpath("migrations").iterdir(), key=lambda item: item.name):
            if resource.name.endswith(".sql"):
                applied = connection.execute(
                    "SELECT version FROM kb_migration WHERE version = %s", (resource.name,)
                ).fetchone()
                if applied is None:
                    connection.execute(resource.read_text(encoding="utf-8"))
                    connection.execute("INSERT INTO kb_migration (version) VALUES (%s)", (resource.name,))


@contextmanager
def connect(settings: Settings, *, apply_migrations: bool = False) -> Iterator[psycopg.Connection[DictRow]]:
    """El contexto confirma o revierte la transacción y siempre cierra la conexión."""
    if settings.database_url is None:
        raise ValueError("DATABASE_URL es requerida")
    if settings.embedding_dimensions != 768:
        raise ValueError("El esquema requiere embeddings de dimensión 768")
    with psycopg.connect(
        settings.database_url.get_secret_value(),
        row_factory=dict_row,
        connect_timeout=10,
        options="-c statement_timeout=10000",
    ) as connection:
        if apply_migrations:
            migrate(connection)
        register_vector(connection)
        yield connection
