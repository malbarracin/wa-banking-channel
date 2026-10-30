"""Contrato SQL real. Ejecutar con Docker: uv run pytest -m integration -q."""

from importlib.resources import files
from uuid import uuid4

import psycopg
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
        columns = db.execute(
            """SELECT attname, format_type(atttypid, atttypmod) AS type
            FROM pg_attribute WHERE attrelid = 'kb_chunk'::regclass
            AND attname IN ('embedding', 'tsv')"""
        ).fetchall()
        assert {row["attname"]: row["type"] for row in columns} == {"embedding": "vector(768)", "tsv": "tsvector"}
        definitions = db.execute("SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'kb_chunk'").fetchall()
        definitions = {row["indexname"]: row["indexdef"] for row in definitions}
        assert "USING hnsw (embedding vector_cosine_ops)" in definitions["idx_kb_chunk_embedding_hnsw"]
        assert "USING gin (tsv)" in definitions["idx_kb_chunk_tsv_gin"]
        chunks = []
        for title, audience, status, version, chunk_version in [
            ("Guía", "public", "retired", 1, 1),
            ("Guía", "public", "approved", 2, 2),
            ("Borrador", "public", "draft", 1, 1),
            ("Retirado", "public", "retired", 1, 1),
            ("Interno", "internal", "approved", 1, 1),
            ("Desfasado", "public", "approved", 3, 2),
        ]:
            source_id, chunk_id = uuid4(), uuid4()
            db.execute(
                """INSERT INTO kb_source (id,source_id,title,owner_area,audience,version,approved_by,approved_at,status)
                VALUES (%s,%s,%s,'Banco',%s,%s,'Compliance','2026-01-01',%s)""",
                (source_id, source_id, title, audience, version, status),
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
        # La rama textual debe recuperar contenido sin vector; la vectorial, sin coincidencia textual.
        assert [hit.id for hit in repo.search("inexistente", VECTOR, audience="public")] == [chunks[1]]
        db.execute("UPDATE kb_chunk SET embedding=NULL WHERE id=%s", (chunks[1],))
        assert [hit.id for hit in repo.search("horarios", VECTOR, audience="public")] == [chunks[1]]
        assert repo.search("inexistente", VECTOR, audience="public") == []
        db.execute("UPDATE kb_chunk SET content='Tarjetas bloqueadas' WHERE id=%s", (chunks[1],))
        row = db.execute(
            "SELECT tsv = to_tsvector('spanish', content) AS matches FROM kb_chunk WHERE id=%s", (chunks[1],)
        ).fetchone()
        assert row["matches"]
        assert db.execute("SELECT count(*) AS count FROM kb_migration").fetchone()["count"] == 2


def test_upgrade_preserves_legacy_revisions(database_url):
    # Separate schema so this also works alongside the fresh-database test.
    with psycopg.connect(database_url, autocommit=True) as db:
        db.execute("CREATE SCHEMA legacy_upgrade")
        db.execute("SET search_path TO legacy_upgrade, public")
        migration = files("wa_knowledge_rag.store").joinpath("migrations/001_knowledge.sql")
        db.execute(migration.read_text(encoding="utf-8"))
        db.execute("CREATE TABLE kb_migration (version TEXT PRIMARY KEY)")
        db.execute("INSERT INTO kb_migration VALUES ('001_knowledge.sql')")
        revisions = [uuid4(), uuid4()]
        for version, revision in enumerate(revisions, start=1):
            db.execute(
                """INSERT INTO kb_source
                (id,title,owner_area,audience,version,approved_by,approved_at,status)
                VALUES (%s,'Legado','Banco','public',%s,'Compliance','2026-01-01','approved')""",
                (revision, version),
            )
        migrate(db)
        migrate(db)
        assert db.execute("SELECT source_id, status FROM kb_source ORDER BY version").fetchall() == [
            (revisions[0], "retired"),
            (revisions[0], "approved"),
        ]
        with pytest.raises(psycopg.errors.UniqueViolation):
            db.execute("UPDATE kb_source SET status='approved' WHERE version=1")
