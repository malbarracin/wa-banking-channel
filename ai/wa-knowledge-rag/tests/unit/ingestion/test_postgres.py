"""Integración opcional: transacción real sobre una DB aislada con pgvector."""

from datetime import UTC, datetime
from pathlib import Path
from uuid import UUID

import psycopg
import pytest
from testcontainers.postgres import PostgresContainer

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embedder import Embedder
from wa_knowledge_rag.ingestion.pipeline import ingest
from wa_knowledge_rag.ingestion.validator import IngestionError, Source


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
            migration = Path(__file__).parents[3] / "migrations/001_ingestion.sql"
            connection.execute(migration.read_text(encoding="utf-8"))
            source = Source(
                source_id=UUID(int=1),
                title="Horarios",
                content="Atención de lunes a viernes.",
                owner_area="Negocio",
                version=1,
                status="approved",
                approved_by="compliance-role",
                approved_at=datetime(2026, 1, 1, tzinfo=UTC),
            )
            embedder = Embedder(Provider(), Settings())
            await ingest(source, embedder, connection, UUID(int=2))
            await ingest(source.model_copy(update={"version": 2}), embedder, connection, UUID(int=2))
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
