"""Database connections and transaction boundaries."""

from collections.abc import Iterator
from contextlib import contextmanager

import psycopg
from pgvector.psycopg import register_vector
from psycopg.rows import DictRow, dict_row

from wa_knowledge_rag.config import Settings

EMBEDDING_DIMENSIONS = 768


def database_url(settings: Settings) -> str:
    if settings.embedding_dimensions != EMBEDDING_DIMENSIONS:
        raise ValueError(f"El esquema requiere KB_EMBEDDING_DIMENSIONS={EMBEDDING_DIMENSIONS}.")
    if settings.database_url is None:
        raise ValueError("Configurá KB_DATABASE_URL.")
    return settings.database_url.get_secret_value()


@contextmanager
def transaction(settings: Settings, *, query: bool = False) -> Iterator[psycopg.Connection[DictRow]]:
    """Share one transaction across repositories, with rollback on failure."""
    with psycopg.connect(database_url(settings), row_factory=dict_row) as connection:
        if query:
            connection.execute("SET LOCAL statement_timeout = '2s'")
        register_vector(connection)
        if query:
            connection.execute("SET LOCAL hnsw.ef_search = 40")
        yield connection
