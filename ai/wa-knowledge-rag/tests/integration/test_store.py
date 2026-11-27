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

from wa_knowledge_rag.cli import main
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
        assert {row["name"] for row in connection.execute("SELECT name FROM kb_schema_migration")} == {
            "0001_init.sql",
            "0002_piece_section.sql",
        }
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


@pytest.fixture
def kb(migrated: Settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]):
    assert migrated.database_url is not None
    params = tmp_path / "content/parameters"
    params.mkdir(parents=True)
    (params / "parametros.yaml").write_text("{}\n", encoding="utf-8")
    policy = Path(__file__).resolve().parents[2] / "config/approval-policy.yaml"
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("KB_DATABASE_URL", migrated.database_url.get_secret_value())
    monkeypatch.setenv("KB_EMBEDDING_DIMENSIONS", "768")
    monkeypatch.setenv("KB_APPROVAL_POLICY_PATH", str(policy))
    monkeypatch.setenv("KB_ENVIRONMENT", "test")

    def run(*args: str, ok: bool = True, **settings: str):
        with monkeypatch.context() as command:
            command.setattr(sys, "argv", ["kb", *args])
            for key, value in settings.items():
                command.setenv(key, value)
            if ok:
                main()
            else:
                with pytest.raises(SystemExit) as failure:
                    main()
                assert failure.value.code == 1
        return capsys.readouterr()

    return run


def write_piece(path: Path, subject: str, body: str = "Consultá la guía de prueba.") -> None:
    path.write_text(
        f"""---
id: {subject}
title: Guía sintética
type: faq
family: canal
audience: publico
owner_area: soporte
source_refs: [H9]
valid_from: 2026-11-27
valid_to: 2026-11-27
section: Canal
---
{body}
""",
        encoding="utf-8",
    )


def test_proposal_review_and_default_policy(kb, tmp_path: Path, migrated: Settings) -> None:
    subject = "propuesta-prueba"
    folder = tmp_path / "content/canal"
    folder.mkdir()
    write_piece(folder / "propuesta.md", subject)
    kb("ingest", "--proposal")
    assert kb("list", "--family", "canal", "--status", "propuesta").out == f"{subject}\t1\tcanal\tpropuesta\n"
    assert kb("list", "--family", "cuentas", "--status", "propuesta").out == ""
    assert "en revisión" in kb("approve", subject, "--role", "area_duena", ok=False).err
    kb("submit", subject)
    kb("approve", subject, "--role", "area_duena", "--test")
    kb("approve", subject, "--role", "area_duena", "--test")
    with transaction(migrated) as connection:
        assert piece_versions.find(connection, piece_id=subject)[0]["status"] == "en_revision"
        assert len(approvals.find(connection, piece_id=subject)) == 1
    kb("approve", subject, "--role", "compliance", "--test")

    with transaction(migrated) as connection:
        rows = approvals.find(connection, piece_id=subject)
        assert {row["role"] for row in rows} == {"area_duena", "compliance"}
        assert all(row["is_test"] and row["version"] == 1 and row["approved_at"].tzinfo for row in rows)
        piece = piece_versions.find(connection, piece_id=subject)[0]
        assert piece["status"] == "aprobada"
        assert piece["section"] == "Canal"
        assert piece["valid_to"] == datetime(2026, 11, 28, tzinfo=UTC)
    assert "Transición inválida" in kb("submit", subject, ok=False).err


def test_ingest_versions_and_audit(kb, tmp_path: Path, migrated: Settings) -> None:
    subject = "versiones-prueba"
    path = tmp_path / "versiones.md"
    write_piece(path, subject)
    kb("ingest", str(path))
    kb("ingest", str(path))
    write_piece(path, subject, "Texto nuevo de la guía sintética.")
    kb("ingest", str(path))
    pii = "persona@example.invalid"
    write_piece(path, subject, pii)
    rejection = kb("ingest", str(path), ok=False)
    assert "datos personales" in rejection.err
    assert pii not in rejection.err
    kb("submit", subject)
    kb("approve", subject, "--role", "area_duena", "--test")
    kb("approve", subject, "--role", pii, ok=False)
    with transaction(migrated) as connection:
        rows = sorted(piece_versions.find(connection, piece_id=subject), key=lambda row: row["version"])
        assert [(row["version"], row["status"]) for row in rows] == [(1, "borrador"), (2, "en_revision")]
        assert "Consultá" in rows[0]["body_template"]
        assert "Texto nuevo" in rows[1]["body_template"]
        events = sorted(audit.find(connection, subject=subject), key=lambda row: row["id"])
        assert [(row["action"], row["version"], row["role"]) for row in events] == [
            ("ingest", 1, "sistema"),
            ("ingest", 1, "sistema"),
            ("ingest", 2, "sistema"),
            ("reject", 2, "sistema"),
            ("submit", 2, "sistema"),
            ("approve", 2, "area_duena"),
            ("reject", 2, "sistema"),
        ]
        assert all(row["at"].tzinfo for row in events)
        assert pii not in repr(events)


def test_configuration_changes_required_roles_and_real_rejects_test(kb, tmp_path: Path, migrated: Settings) -> None:
    subject = "politica-prueba"
    path = tmp_path / "politica.md"
    write_piece(path, subject)
    kb("ingest", str(path))
    kb("submit", subject)
    policy = tmp_path / "approval-policy.yaml"
    policy.write_text(
        "faq: [soporte]\nguia: [compliance]\npolitica: [compliance]\nmensaje: [compliance]\nparameter: [compliance]\n",
        encoding="utf-8",
    )
    env = {"KB_APPROVAL_POLICY_PATH": str(policy), "KB_ENVIRONMENT": "real"}
    assert "fuera de la política" in kb("approve", subject, "--role", "area_duena", ok=False, **env).err
    assert "aprobaciones de prueba" in kb("approve", subject, "--role", "soporte", "--test", ok=False, **env).err
    with transaction(migrated) as connection:
        assert approvals.find(connection, piece_id=subject) == []
    kb("approve", subject, "--role", "soporte", **env)
    with transaction(migrated) as connection:
        assert piece_versions.find(connection, piece_id=subject)[0]["status"] == "aprobada"
        assert approvals.find(connection, piece_id=subject)[0]["is_test"] is False


def test_parameter_versions_and_approval(kb, tmp_path: Path, migrated: Settings) -> None:
    name = "horario_prueba"
    path = tmp_path / "content/parameters/parametros.yaml"
    path.write_text(f'{name}: {{value: "Turno de la mañana", is_test: true}}\n', encoding="utf-8")
    kb("params", "ingest")
    kb("params", "ingest")
    kb("params", "approve", name, "--role", "area_duena", "--test")
    with transaction(migrated) as connection:
        assert parameter_versions.find(connection, name=name)[0]["status"] == "en_revision"
    kb("params", "approve", name, "--role", "compliance", "--test")
    assert (
        "parámetros de prueba"
        in kb("params", "approve", name, "--role", "compliance", ok=False, KB_ENVIRONMENT="real").err
    )
    path.write_text(f'{name}: {{value: "Turno de la tarde", is_test: false}}\n', encoding="utf-8")
    kb("params", "ingest")
    with transaction(migrated) as connection:
        rows = sorted(parameter_versions.find(connection, name=name), key=lambda row: row["version"])
        assert [(row["version"], row["status"]) for row in rows] == [(1, "aprobada"), (2, "en_revision")]
        assert rows[0]["approved_at"].tzinfo and rows[1]["approved_at"] is None
        assert len(approvals.find(connection, parameter_name=name, version=1)) == 2
        assert approvals.find(connection, parameter_name=name, version=2) == []
