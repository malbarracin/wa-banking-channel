"""Contrato SQL real. Ejecutar con Docker: uv run pytest -m integration -q."""

from uuid import uuid4

import pytest
from testcontainers.postgres import PostgresContainer

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.store.db import connect, migrate
from wa_knowledge_rag.store.repo import KnowledgeRepository

pytestmark = pytest.mark.integration
VECTOR = [1.0] + [0.0] * 767


@pytest.fixture(scope="module")
def database_url():
    import docker
    from docker.errors import DockerException

    try:
        client = docker.from_env()
        client.ping()
        client.close()
    except DockerException:
        pytest.skip("Docker no disponible; ejecutar uv run pytest -m integration -q con Docker activo")
    with PostgresContainer("pgvector/pgvector:pg17", driver="psycopg") as postgres:
        yield postgres.get_connection_url().replace("postgresql+psycopg://", "postgresql://")


def test_schema_filters_ranking_and_trigger(database_url):
    with connect(Settings(database_url=database_url), apply_migrations=True) as db:
        migrate(db)
        indexes = db.execute("SELECT indexname FROM pg_indexes WHERE tablename IN ('kb_chunk', 'kb_source')").fetchall()
        assert {row["indexname"] for row in indexes} >= {
            "idx_kb_chunk_embedding_hnsw",
            "idx_kb_chunk_tsv_gin",
            "idx_kb_chunk_source_version",
            "idx_kb_source_audience_status",
        }
        chunks = []
        for title, audience, status, version, chunk_version in [
            ("Guía", "public", "approved", 1, 1),
            ("Guía", "public", "approved", 2, 2),
            ("Borrador", "public", "draft", 1, 1),
            ("Retirado", "public", "retired", 1, 1),
            ("Interno", "internal", "approved", 1, 1),
            ("Desfasado", "public", "approved", 3, 2),
        ]:
            source_id, chunk_id = uuid4(), uuid4()
            db.execute(
                """INSERT INTO kb_source (id,title,owner_area,audience,version,approved_by,approved_at,status)
                VALUES (%s,%s,'Banco',%s,%s,'Compliance','2026-01-01',%s)""",
                (source_id, title, audience, version, status),
            )
            db.execute(
                """INSERT INTO kb_chunk (id,source_id,version,ordinal,content,embedding)
                VALUES (%s,%s,%s,0,'Horarios de atención',%s::vector)""",
                (chunk_id, source_id, chunk_version, str(VECTOR)),
            )
            chunks.append(chunk_id)
        repo = KnowledgeRepository(db)
        assert [hit.id for hit in repo.search("horarios", VECTOR, audience="public")] == [chunks[1]]
        assert repo.search("horarios", VECTOR, audience="public", version=1) == []
        assert [hit.id for hit in repo.search("horarios", VECTOR, audience="internal")] == [chunks[4]]
        assert repo.search("horarios", VECTOR, audience="public", version=2)[0].score > 1 / 61
        db.execute("UPDATE kb_chunk SET content='Tarjetas bloqueadas' WHERE id=%s", (chunks[1],))
        row = db.execute(
            "SELECT tsv = to_tsvector('spanish', content) AS matches FROM kb_chunk WHERE id=%s", (chunks[1],)
        ).fetchone()
        assert row["matches"]
        assert db.execute("SELECT count(*) AS count FROM kb_migration").fetchone()["count"] == 1
