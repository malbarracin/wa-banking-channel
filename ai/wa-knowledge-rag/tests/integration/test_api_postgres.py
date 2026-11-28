import json
from datetime import UTC, datetime
from hashlib import sha256
from pathlib import Path
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator
from pydantic import SecretStr

from wa_knowledge_rag.api.app import create_app
from wa_knowledge_rag.store.database import transaction
from wa_knowledge_rag.store.repositories import (
    approvals,
    chunks,
    piece_versions,
    pieces,
    query_log,
    release_items,
    releases,
)

pytestmark = pytest.mark.integration
CONTRACT = Path(__file__).resolve().parents[4] / "shared/contracts/wa-knowledge-rag"
AUTH = {"Authorization": "Bearer internal-fixture", "X-Correlation-Id": "request-fixture"}
VECTOR = [1.0] + [0.0] * 767


def test_query_health_audit_and_metrics(migrated, capsys):
    consumers = [
        {"id": "internal-test", "token_sha256": sha256(b"internal-fixture").hexdigest(), "allow_internal": True}
    ]
    settings = migrated.model_copy(update={"consumers": SecretStr(json.dumps(consumers))})
    embedder = Mock()
    embedder.embed_query.return_value = VECTOR
    with TestClient(create_app(settings, embedder=embedder)) as client:
        health = client.get("/health")
        assert health.status_code == 503
        assert health.json()["status"] == "degraded"

        piece_id = "internal-piece-fixture"
        approved_at = datetime(2024, 2, 1, tzinfo=UTC)
        with transaction(migrated) as connection:
            active = releases.insert(connection, reason="publish", is_active=True)["release_no"]
            pieces.insert(connection, piece_id=piece_id)
            piece_versions.insert(
                connection,
                piece_id=piece_id,
                version=1,
                title="Guía sintética",
                type="guia",
                family="seguros",
                audience="interno",
                owner_area="area-de-prueba",
                status="publicada",
                body_template="Cobertura aprobada de prueba",
                source_refs=[],
                content_hash=piece_id,
                valid_from=approved_at,
            )
            for role in ("area_duena", "compliance"):
                approvals.insert(
                    connection, piece_id=piece_id, version=1, role=role, approved_at=approved_at, is_test=True
                )
            release_items.insert(connection, release_no=active, piece_id=piece_id, version=1, rendered_hash=piece_id)
            chunks.insert(
                connection,
                piece_id=piece_id,
                version=1,
                rendered_hash=piece_id,
                chunk_no=0,
                section=None,
                title="Guía sintética",
                text="Cobertura aprobada de prueba",
                family="seguros",
                audience="interno",
                type="guia",
                embedding=VECTOR,
                embedding_model=migrated.embedding_model,
            )

        health = client.get("/health")
        assert health.status_code == 200
        assert health.json() == {"status": "ok", "database": "ok", "index": "ok", "knowledgeVersion": f"kv-{active}"}

        query = "Cobertura con DNI 99991234"
        response = client.post("/v1/retrieve", headers=AUTH, json={"query": query, "audience": "interno"})
        assert response.status_code == 200
        body = response.json()
        schema = json.loads((CONTRACT / "retrieval-response.v1.schema.json").read_text())
        Draft202012Validator(schema).validate(body)
        assert body["correlationId"] == response.headers["X-Correlation-Id"] == AUTH["X-Correlation-Id"]
        assert body["results"][0]["publishable"] is False
        assert body["results"][0]["citation"]["pieceId"] == piece_id
        assert body["results"][0]["citation"]["approvedAt"] == "2024-02-01T00:00:00Z"
        assert body["results"][0]["text"] == "Cobertura aprobada de prueba"
        embedder.embed_query.assert_called_once_with("Cobertura con DNI ***1234")

        with transaction(migrated) as connection:
            [record] = query_log.find(connection, correlation_id=AUTH["X-Correlation-Id"])
        assert set(record) == {
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
        assert record["consumer_id"] == "internal-test"
        assert record["release_no"] == active
        assert record["cited"] == [f"{piece_id}@1"]
        assert record["level"] == "alta"
        assert record["top_score"] == 1
        assert record["latency_ms"] >= 0
        assert record["query_len"] == len(query)
        [event] = [json.loads(line) for line in capsys.readouterr().out.splitlines() if '"kb_retrieve"' in line]
        assert event["query"] == "Cobertura con DNI ***1234"
        assert event["correlationId"] == AUTH["X-Correlation-Id"]

        public = client.post("/v1/retrieve", headers=AUTH, json={"query": "Cobertura"})
        assert public.status_code == 200
        assert public.json()["confidence"]["level"] == "sin_respuesta"
        assert public.json()["results"] == []

        metrics = client.get("/metrics")
        assert metrics.status_code == 200
        assert "kb_retrieve_latency_seconds_count 2.0" in metrics.text
        assert 'kb_retrieve_total{level="alta"} 1.0' in metrics.text
        assert 'kb_retrieve_total{level="sin_respuesta"} 1.0' in metrics.text
        assert f"kb_active_release {active}.0" in metrics.text
        assert 'kb_published_pieces{audience="interno",family="seguros"} 1.0' in metrics.text

        with transaction(migrated) as connection:
            connection.execute("DROP INDEX kb_chunk_tsv_gin")
        degraded = client.get("/health")
        assert degraded.status_code == 503
        assert degraded.json()["index"] == "unavailable"
        unavailable = client.post("/v1/retrieve", headers=AUTH, json={"query": "Cobertura"})
        assert unavailable.status_code == 503
        assert unavailable.json()["code"] == "KNOWLEDGE_UNAVAILABLE"
        with transaction(migrated) as connection:
            connection.execute("DROP EXTENSION vector CASCADE")
        degraded = client.get("/health")
        assert degraded.status_code == 503
        assert degraded.json()["status"] == "degraded"
        assert degraded.json()["database"] == "ok"
