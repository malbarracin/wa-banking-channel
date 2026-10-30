"""Golden set con PostgreSQL/pgvector real: uv run pytest -m integration tests/golden -q."""

import json
from pathlib import Path
from uuid import UUID

import pytest
from testcontainers.postgres import PostgresContainer

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.retrieval.hybrid import HybridRetriever
from wa_knowledge_rag.store.db import connect
from wa_knowledge_rag.store.repo import KnowledgeRepository

pytestmark = pytest.mark.integration
CORPUS = json.loads(Path(__file__).with_name("retrieval.json").read_text(encoding="utf-8"))


def vector(axis):
    values = [0.0] * 768
    values[axis] = 1.0
    return values


@pytest.fixture(scope="module")
def repository():
    import docker
    from docker.errors import DockerException

    try:
        client = docker.from_env()
        client.ping()
        client.close()
    except DockerException:
        pytest.skip("Docker no disponible; ejecutar uv run pytest -m integration tests/golden -q")
    with PostgresContainer("pgvector/pgvector:pg17", driver="psycopg") as postgres:
        url = postgres.get_connection_url().replace("postgresql+psycopg://", "postgresql://")
        with connect(Settings(database_url=url), apply_migrations=True) as db:
            for source in CORPUS["sources"]:
                revision = UUID(int=source["key"] + 100)
                db.execute(
                    """INSERT INTO kb_source(id,title,owner_area,audience,version,approved_by,approved_at,status)
                    VALUES (%s,%s,'Banco',%s,1,'Compliance','2026-01-01','approved')""",
                    (revision, source["title"], source["audience"]),
                )
                db.execute(
                    """INSERT INTO kb_chunk(id,source_id,version,ordinal,content,embedding,metadata)
                    VALUES (%s,%s,1,0,%s,%s::vector,%s::jsonb)""",
                    (
                        UUID(int=source["key"]),
                        revision,
                        source["content"],
                        str(vector(source["key"])),
                        json.dumps({"source_id": str(UUID(int=source["key"] + 1000))}),
                    ),
                )
            yield KnowledgeRepository(db)


def retriever(repository):
    return HybridRetriever(repository, Settings(rag_confidence_high_min=0.9, rag_confidence_medium_min=0.6))


def test_recall_at_five(repository, record_property):
    recalls = []
    for case in CORPUS["queries"]:
        result = retriever(repository).retrieve(
            case["query"], vector(case["axis"]), audience="public", correlation_id="golden", kb_version="synthetic-1"
        )
        retrieved = {item.citation.source_id.int - 1000 for item in result.results[:5]}
        expected = set(case["expected"])
        recalls.append(len(retrieved & expected) / len(expected))
        assert result.confidence == "high"
    recall = sum(recalls) / len(recalls)
    record_property("recall@5", recall)
    assert recall >= 0.8


def test_rrf_combines_disagreeing_vector_and_spanish_text_rankings(repository):
    # Vector: tarjetas primero, seguros tercero por desempate de UUID.
    # Texto español: «seguro» recupera «seguros» como primer y único resultado.
    hits = repository.search("seguro", vector(1), audience="public")
    assert hits[0].id == UUID(int=3)
    assert hits[0].score == pytest.approx(1 / 63 + 1 / 61)
    assert hits[1].id == UUID(int=1)
    assert hits[1].score == pytest.approx(1 / 61)
    assert {hit.id for hit in hits} == {UUID(int=key) for key in range(1, 6)}


@pytest.mark.parametrize("query", CORPUS["outside"])
def test_outside_and_internal_never_answer_client(repository, query):
    result = retriever(repository).retrieve(
        query, vector(6), audience="public", correlation_id="golden", kb_version="synthetic-1"
    )
    assert result.confidence == "low"
    assert result.results == []
    assert result.reason == "sin_respuesta_suficiente"


def test_internal_requires_reasoning(repository):
    result = retriever(repository).retrieve(
        "escalamiento interno",
        vector(6),
        audience="internal",
        for_client=False,
        correlation_id="golden",
        kb_version="synthetic-1",
    )
    assert result.confidence == "high"
    assert result.results[0].citation.source_id == UUID(int=1006)
