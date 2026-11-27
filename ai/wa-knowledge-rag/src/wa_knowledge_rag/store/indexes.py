"""Shared PostgreSQL readiness checks for the retrieval indexes."""

from psycopg import Connection
from psycopg.rows import DictRow


def indexes_ready(connection: Connection[DictRow]) -> bool:
    row = connection.execute(
        """
        SELECT count(*) = 2 AS ready
        FROM pg_index
        WHERE indexrelid IN (to_regclass('kb_chunk_embedding_hnsw'), to_regclass('kb_chunk_tsv_gin'))
          AND indisvalid AND indisready
        """
    ).fetchone()
    return row is not None and bool(row["ready"])
