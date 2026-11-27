"""Schema and persistence checks with a real PostgreSQL/pgvector instance."""

import os
import subprocess
import sys
from collections.abc import Iterator
from datetime import UTC, datetime
from pathlib import Path

import psycopg
import pytest
from pydantic import SecretStr
from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import LogMessageWaitStrategy

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.store.database import transaction
from wa_knowledge_rag.store.repositories import (
    approvals,
    audit,
    chunks,
    parameter_versions,
    piece_versions,
    pieces,
    query_log,
    release_items,
    releases,
)

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def postgres() -> Iterator[Settings]:
    # Disposable test instance with no host port fixed or real credentials.
    with (
        DockerContainer("pgvector/pgvector:pg16")
        .with_env("POSTGRES_HOST_AUTH_METHOD", "trust")
        .with_exposed_ports(5432)
        .waiting_for(LogMessageWaitStrategy("database system is ready to accept connections", times=2))
    ) as container:
        host = container.get_container_host_ip()
        port = container.get_exposed_port(5432)
        yield Settings(database_url=SecretStr(f"host={host} port={port} dbname=postgres user=postgres"))


@pytest.fixture(scope="module")
def migrated(postgres: Settings) -> Settings:
    assert postgres.database_url is not None
    env = os.environ | {
        "KB_DATABASE_URL": postgres.database_url.get_secret_value(),
        "KB_EMBEDDING_DIMENSIONS": "768",
    }
    cli = Path(sys.executable).with_name("kb")
    for _ in range(2):
        # The entry point is installed beside this interpreter and takes fixed arguments.
        result = subprocess.run(  # noqa: S603
            [cli, "db", "migrate"], env=env, capture_output=True, check=False, timeout=30
        )
        assert result.returncode == 0, result.stderr.decode()
    return postgres


def test_migrations_and_indexes(migrated: Settings) -> None:
    with transaction(migrated, query=True) as connection:
        assert connection.execute("SHOW statement_timeout").fetchone() == {"statement_timeout": "2s"}
        assert connection.execute("SHOW hnsw.ef_search").fetchone() == {"hnsw.ef_search": "40"}
        assert connection.execute("SELECT name FROM kb_schema_migration").fetchall() == [{"name": "0001_init.sql"}]
        assert connection.execute("SELECT extname FROM pg_extension WHERE extname = 'vector'").fetchone()
        tables = connection.execute("SELECT tablename FROM pg_tables WHERE schemaname = 'public'").fetchall()
        assert {row["tablename"] for row in tables} == {
            "kb_schema_migration",
            "kb_piece",
            "kb_piece_version",
            "kb_approval",
            "kb_parameter_version",
            "kb_release",
            "kb_release_item",
            "kb_chunk",
            "kb_audit",
            "kb_query_log",
        }
        indexes = connection.execute("SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'kb_chunk'")
        definitions = {row["indexname"]: row["indexdef"] for row in indexes}
        hnsw = definitions["kb_chunk_embedding_hnsw"]
        assert "USING hnsw (embedding vector_cosine_ops)" in hnsw
        assert "m='16'" in hnsw and "ef_construction='64'" in hnsw
        assert "USING gin (tsv)" in definitions["kb_chunk_tsv_gin"]
        assert "USING btree (family, type, audience)" in definitions["kb_chunk_filters"]
        assert "USING btree (piece_id, version, rendered_hash)" in definitions["kb_chunk_rendered"]
        embedding_type = connection.execute(
            """
            SELECT format_type(atttypid, atttypmod) AS data_type
            FROM pg_attribute
            WHERE attrelid = 'public.kb_chunk'::regclass AND attname = 'embedding'
            """
        ).fetchone()
        assert embedding_type == {"data_type": "vector(768)"}
        generated = connection.execute(
            """
            SELECT is_generated, generation_expression FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'kb_chunk' AND column_name = 'tsv'
            """
        ).fetchone()
        assert generated is not None
        assert generated["is_generated"] == "ALWAYS"
        assert "to_tsvector('spanish'::regconfig" in generated["generation_expression"]


def test_only_one_active_release_and_atomic_rollback(migrated: Settings) -> None:
    with (
        pytest.raises(psycopg.errors.UniqueViolation, match="kb_release_one_active"),
        transaction(migrated) as connection,
    ):
        release = releases.insert(connection, reason="publish", is_active=True)
        releases.insert(connection, reason="sync", parent_release_no=release["release_no"], is_active=True)
    with transaction(migrated) as connection:
        assert releases.find(connection, release_no=release["release_no"]) == []


def test_query_log_persists_only_metadata(migrated: Settings) -> None:
    with transaction(migrated) as connection:
        columns = connection.execute(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'kb_query_log'
            """
        ).fetchall()
        assert {row["column_name"] for row in columns} == {
            "at",
            "correlation_id",
            "consumer_id",
            "release_no",
            "cited",
            "level",
            "top_score",
            "latency_ms",
            "query_len",
        }
        query_log.insert(
            connection,
            correlation_id="corr-test",
            consumer_id="test",
            cited=[],
            level="sin_respuesta",
            latency_ms=12,
            query_len=25,
        )
        assert query_log.find(connection, correlation_id="corr-test")[0]["cited"] == []


def test_repositories_share_one_transaction(migrated: Settings) -> None:
    piece_id = "pieza-de-prueba"
    with transaction(migrated) as connection:
        pieces.insert(connection, piece_id=piece_id)
        piece_versions.insert(
            connection,
            piece_id=piece_id,
            version=1,
            title="Tarjetas de prueba",
            type="faq",
            family="tarjetas",
            audience="publico",
            owner_area="soporte",
            status="borrador",
            body_template="Contenido sintético de prueba.",
            source_refs=["fuente-de-prueba"],
            content_hash="origen",
            valid_from=datetime(2025, 1, 1, tzinfo=UTC),
        )
        parameter_versions.insert(
            connection, name="parametro_prueba", version=1, value="prueba", is_test=True, status="aprobada"
        )
        approvals.insert(connection, piece_id=piece_id, version=1, role="area_duena", is_test=True)
        approvals.insert(
            connection,
            subject_kind="parameter",
            parameter_name="parametro_prueba",
            version=1,
            role="compliance",
            is_test=True,
        )
        assert piece_versions.set_piece_status(connection, piece_id=piece_id, version=1, status="aprobada")
        release = releases.insert(connection, reason="publish")
        release_no = release["release_no"]
        release_items.insert(connection, release_no=release_no, piece_id=piece_id, version=1, rendered_hash="resuelto")
        embedding = [1.0] + [0.0] * 767
        chunk = chunks.insert(
            connection,
            piece_id=piece_id,
            version=1,
            rendered_hash="resuelto",
            chunk_no=0,
            title="Tarjetas de prueba",
            text="Contenido sintético",
            embedding=embedding,
            embedding_model="gemini-embedding-001",
            family="tarjetas",
            type="faq",
            audience="publico",
        )
        assert chunk["embedding"].to_list() == embedding
        assert "'tarjet'" in chunk["tsv"]
        assert releases.set_active(connection, release_no=release_no, is_active=True)
        audit.insert(
            connection, action="publish", subject=piece_id, version=1, release_no=release_no, detail={"synthetic": True}
        )
        # Leave a historical release so other tests can independently check the active-release constraint.
        assert releases.set_active(connection, release_no=release_no, is_active=False)

    with transaction(migrated) as connection:
        assert pieces.find(connection, piece_id=piece_id)
        assert piece_versions.find(connection, piece_id=piece_id)[0]["source_refs"] == ["fuente-de-prueba"]
        assert parameter_versions.find(connection, name="parametro_prueba")[0]["value"] == "prueba"
        assert approvals.find(connection, piece_id=piece_id)[0]["role"] == "area_duena"
        assert approvals.find(connection, parameter_name="parametro_prueba")[0]["role"] == "compliance"
        assert release_items.find(connection, release_no=release_no)[0]["rendered_hash"] == "resuelto"
        assert chunks.find(connection, piece_id=piece_id, section=None)[0]["id"] == chunk["id"]
        assert audit.find(connection, release_no=release_no)[0]["detail"] == {"synthetic": True}
