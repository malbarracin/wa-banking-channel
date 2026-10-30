"""Regresiones de aceptación con proveedores y base aislados mediante dobles."""

from datetime import UTC, datetime
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID

import pytest

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.chunker import chunk
from wa_knowledge_rag.ingestion.embedder import Embedder
from wa_knowledge_rag.ingestion.pipeline import ingest
from wa_knowledge_rag.ingestion.validator import IngestionError, Source


def approved_source(**updates):
    return Source(
        **{
            "source_id": UUID(int=10),
            "title": "Guía de atención",
            "content": "Atención en días hábiles.",
            "owner_area": "Negocio",
            "version": 2,
            "status": "approved",
            "approved_by": "compliance-role",
            "approved_at": datetime(2026, 1, 1, tzinfo=UTC),
            **updates,
        }
    )


def test_overlap_preserves_order_and_section_boundaries():
    words = [f"palabra{i}" for i in range(960)]
    source = approved_source(content="# Primera\n" + " ".join(words) + "\n# Segunda\nFinal.")
    chunks = chunk(source)
    assert [item.content.split() for item in chunks[:3]] == [words[:500], words[450:950], words[900:]]
    assert [item.section for item in chunks] == ["Primera", "Primera", "Primera", "Segunda"]
    assert [item.ordinal for item in chunks] == [0, 1, 2, 3]
    assert all(item.version == 2 and item.source_id == source.source_id for item in chunks)


@pytest.mark.parametrize(
    "updates,code",
    [({"status": status}, "APPROVAL_REQUIRED") for status in ("draft", "rejected", "retired")]
    + [
        ({"content": "Documento 12.345.678"}, "PII_DETECTED"),
        ({"owner_area": "Teléfono +54 11 5555 1234"}, "PII_DETECTED"),
        *[
            ({"content": content}, "PII_DETECTED")
            for content in (
                "Nombre: Persona Prueba\nSaldo: 200",
                "Nombre: Persona Prueba\r\nSaldo: 200",
                "Nombre: Persona Prueba\n\nCuenta: 200",
                "Persona Prueba\nSaldo: 200",
                "Persona Prueba\r\nTarjeta: 200",
            )
        ],
        ({"approved_by": None}, "APPROVAL_TRACE_REQUIRED"),
    ],
)
async def test_pipeline_rejects_before_external_calls(updates, code):
    provider = MagicMock()
    provider.embed = AsyncMock()
    connection = MagicMock()
    with pytest.raises(IngestionError, match=code):
        await ingest(approved_source(**updates), Embedder(provider, Settings()), connection, UUID(int=20))
    provider.embed.assert_not_awaited()
    assert connection.mock_calls == []


@pytest.mark.parametrize("vectors", [[], [[1.0] * 768] * 2, [[0.0] * 768], [[float("nan")] * 768]])
async def test_invalid_embeddings_never_reach_database(vectors):
    provider = MagicMock()
    provider.embed = AsyncMock(return_value=vectors)
    connection = MagicMock()
    with pytest.raises(IngestionError, match="INVALID_EMBEDDING"):
        await ingest(approved_source(), Embedder(provider, Settings()), connection, UUID(int=20))
    assert connection.mock_calls == []
