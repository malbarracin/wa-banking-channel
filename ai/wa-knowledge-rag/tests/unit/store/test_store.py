"""Pruebas sin red del acceso a datos y sus límites."""

from unittest.mock import MagicMock, patch
from uuid import UUID

import pytest

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.store.db import connect, migrate
from wa_knowledge_rag.store.models import Chunk, Source
from wa_knowledge_rag.store.repo import KnowledgeRepository

VECTOR = [1.0] + [0.0] * 767


def test_search_parameters_and_mapping():
    db = MagicMock()
    db.execute.return_value.fetchall.return_value = [
        dict(
            id=UUID(int=1),
            source_id=UUID(int=2),
            title="Guía",
            version=2,
            content="Horario",
            section=None,
            source_uri=None,
            score=0.02,
        )
    ]
    result = KnowledgeRepository(db).search("horario ' ; DROP TABLE kb_source", VECTOR, audience="public", version=2)
    assert result[0].source_id == UUID(int=2)
    sql, params = db.execute.call_args.args
    assert "DROP TABLE" not in sql
    assert params["version"] == 2
    assert params["audience"] == "public"
    assert "s.status = 'approved'" in sql
    assert "c.version = s.version" in sql
    assert "newer.version > s.version" in sql


@pytest.mark.parametrize(
    "changes",
    [
        {"audience": "other"},
        {"query": ""},
        {"query": "a" * 10001},
        {"top_k": 0},
        {"top_k": 101},
        {"version": 0},
        {"embedding": [1.0]},
        {"embedding": [float("nan")] * 768},
        {"embedding": [0.0] * 768},
    ],
)
def test_invalid_search_does_not_reach_db(changes):
    db = MagicMock()
    args = dict(query="horario", embedding=VECTOR, audience="public") | changes
    with pytest.raises(ValueError):
        KnowledgeRepository(db).search(**args)
    db.execute.assert_not_called()


def test_empty_internal_search():
    db = MagicMock()
    db.execute.return_value.fetchall.return_value = []
    assert KnowledgeRepository(db).search("guía", VECTOR, audience="internal") == []
    assert db.execute.call_args.args[1]["version"] is None


def test_migrations_once_and_transaction():
    db = MagicMock()
    db.execute.return_value.fetchone.return_value = None
    migrate(db)
    statements = [call.args[0] for call in db.execute.call_args_list]
    assert any("vector(768)" in sql and "to_tsvector('spanish', NEW.content)" in sql for sql in statements)
    assert any("vector_cosine_ops" in sql and "USING gin (tsv)" in sql for sql in statements)
    assert any("INSERT INTO kb_migration" in sql for sql in statements)
    db.reset_mock()
    db.execute.return_value.fetchone.return_value = {"version": "001_knowledge.sql"}
    migrate(db)
    assert not any("CREATE EXTENSION" in call.args[0] for call in db.execute.call_args_list)
    db.transaction.assert_called_once()


@pytest.mark.parametrize(
    "settings", [Settings(), Settings(database_url="postgresql://synthetic", embedding_dimensions=2)]
)
def test_invalid_connection_settings(settings):
    with pytest.raises(ValueError), connect(settings):
        pytest.fail("No debe abrir conexión")


def test_connection_registers_adapter_and_migrates():
    with (
        patch("wa_knowledge_rag.store.db.psycopg.connect") as opening,
        patch("wa_knowledge_rag.store.db.register_vector") as register,
        patch("wa_knowledge_rag.store.db.migrate") as migration,
    ):
        db = opening.return_value.__enter__.return_value
        with connect(Settings(database_url="postgresql://synthetic"), apply_migrations=True) as connection:
            assert connection is db
        migration.assert_called_once_with(db)
        register.assert_called_once_with(db)
        opening.return_value.__exit__.assert_called_once()


def test_entities():
    source = Source(
        id=UUID(int=1),
        title="Guía",
        owner_area="Banco",
        audience="public",
        version=1,
        approved_by="Compliance",
        approved_at="2026-01-01T00:00:00Z",
        status="approved",
    )
    chunk = Chunk(id=UUID(int=2), source_id=source.id, version=1, ordinal=0, content="Datos")
    assert source.metadata == chunk.metadata == {}
    with pytest.raises(ValueError):
        Chunk(id=UUID(int=3), source_id=source.id, version=1, ordinal=0, content="Datos", embedding=[1.0])
