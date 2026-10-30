"""Integración opcional: transacción real sobre una DB aislada con pgvector."""

from datetime import UTC, datetime
from uuid import UUID

import psycopg
import pytest
from psycopg.rows import dict_row
from testcontainers.postgres import PostgresContainer

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embedder import Embedder
from wa_knowledge_rag.ingestion.pipeline import ingest
from wa_knowledge_rag.ingestion.validator import IngestionError, Source
from wa_knowledge_rag.store.db import migrate
from wa_knowledge_rag.store.repo import KnowledgeRepository


class Provider:
    async def embed(self, texts, model, dimensions):
        return [[1.0] * dimensions for _ in texts]


@pytest.mark.integration
async def test_atomic_publication():
    # Disponibilidad antes del contenedor; fallos de migración/producto nunca se omiten.
    import docker
    from docker.errors import DockerException

    try:
        docker.from_env().ping()
    except (DockerException, OSError):
        pytest.skip("Docker no disponible: levantar Docker Desktop y ejecutar pytest -m integration")
    with PostgresContainer("pgvector/pgvector:pg17") as postgres:
        url = postgres.get_connection_url().replace("postgresql+psycopg2://", "postgresql://")
        with psycopg.connect(url, autocommit=True) as connection:
            migrate(connection)
            source = Source(
                source_id=UUID(int=1),
                title="Horarios",
                content="Atención de lunes a viernes.",
                owner_area="Negocio",
                version=1,
                status="approved",
                approved_by="compliance-role",
                approved_at=datetime(2026, 1, 1, tzinfo=UTC),
                audience="public",
            )
            embedder = Embedder(Provider(), Settings())
            await ingest(source, embedder, connection, UUID(int=2))
            await ingest(source.model_copy(update={"version": 2}), embedder, connection, UUID(int=2))
            # Retrieval uses the same packaged schema, with authoritative logical citations.
            with psycopg.connect(url, autocommit=True, row_factory=dict_row) as retrieval_db:
                repo = KnowledgeRepository(retrieval_db)
                hits = repo.search("atención", [1.0] * 768, audience="public")
                assert len(hits) == 1
                assert hits[0].source_id == source.source_id
                assert hits[0].version == 2
                assert hits[0].source_uri is None
                assert repo.search("atención", [1.0] * 768, audience="public", version=1) == []
            # Different sources may share a title; renaming a revision preserves its identity.
            other = source.model_copy(update={"source_id": UUID(int=3), "version": 5})
            await ingest(other, embedder, connection, UUID(int=2))
            renamed = source.model_copy(update={"version": 3, "title": "Nueva guía"})
            await ingest(renamed, embedder, connection, UUID(int=2))
            with psycopg.connect(url, autocommit=True, row_factory=dict_row) as retrieval_db:
                hits = KnowledgeRepository(retrieval_db).search("atención", [1.0] * 768, audience="public")
                assert {(hit.source_id, hit.version) for hit in hits} == {(source.source_id, 3), (other.source_id, 5)}
            connection.execute("DELETE FROM kb_chunk WHERE version IN (3,5)")
            connection.execute("DELETE FROM kb_source WHERE version IN (3,5)")
            connection.execute("UPDATE kb_source SET status='approved' WHERE version=2")
            assert connection.execute("SELECT version,status FROM kb_source ORDER BY version").fetchall() == [
                (1, "retired"),
                (2, "approved"),
            ]
            with pytest.raises(IngestionError):
                await ingest(source, embedder, connection, UUID(int=2))
            # Fuerza fallo al insertar chunks, después del retiro/insert de la fuente.
            connection.execute("ALTER TABLE kb_chunk ADD CONSTRAINT fail_new CHECK (version < 3)")
            with pytest.raises(psycopg.errors.CheckViolation):
                await ingest(source.model_copy(update={"version": 3}), embedder, connection, UUID(int=2))
            assert connection.execute("SELECT version FROM kb_source WHERE status='approved'").fetchall() == [(2,)]
            assert connection.execute("SELECT count(*) FROM kb_source").fetchone() == (2,)
