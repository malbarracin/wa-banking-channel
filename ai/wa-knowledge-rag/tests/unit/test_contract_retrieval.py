"""Contract tests: live API responses against published retrieval v1 schemas."""

from __future__ import annotations

import json
from hashlib import sha256
from pathlib import Path
from unittest.mock import MagicMock, Mock

import psycopg
import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator
from pydantic import SecretStr

from wa_knowledge_rag.api.app import create_app
from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embeddings import EmbeddingProviderUnavailable

CONTRACT = Path(__file__).resolve().parents[4] / "shared/contracts/wa-knowledge-rag"
CORRELATION = "contrato-v1-fixture"
HEADERS = {"Authorization": "Bearer contract-fixture", "X-Correlation-Id": CORRELATION}
REQUEST = {"query": "Consulta de contrato"}


def _schema(name: str) -> Draft202012Validator:
    return Draft202012Validator(json.loads((CONTRACT / name).read_text()))


def _example(name: str) -> dict:
    return json.loads((CONTRACT / "examples" / name).read_text())


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> tuple[TestClient, Mock]:
    monkeypatch.setenv(
        "KB_CONSUMERS",
        json.dumps(
            [
                {
                    "id": "contract-public",
                    "token_sha256": sha256(b"contract-fixture").hexdigest().upper(),
                    "allow_internal": False,
                }
            ]
        ),
    )
    settings = Settings(database_url=SecretStr("dbname=contract"))
    embedder = Mock()
    embedder.embed_query.return_value = [1.0]
    with TestClient(create_app(settings, embedder=embedder)) as http:
        yield http, embedder


def _stub_retrieve(monkeypatch: pytest.MonkeyPatch, example_name: str) -> dict:
    body = _example(example_name)
    body["correlationId"] = CORRELATION
    monkeypatch.setattr("wa_knowledge_rag.api.app.retrieve", Mock(return_value=body))
    monkeypatch.setattr("wa_knowledge_rag.api.app.transaction", MagicMock())
    monkeypatch.setattr("wa_knowledge_rag.api.app.query_log", Mock())
    return body


@pytest.mark.parametrize(
    ("example_name", "level", "suggestion"),
    [
        ("response-alta.json", "alta", "responder"),
        ("response-baja.json", "baja", "aclarar"),
        ("response-sin-respuesta.json", "sin_respuesta", "derivar"),
    ],
)
def test_success_responses_match_published_schema(
    client: tuple[TestClient, Mock],
    monkeypatch: pytest.MonkeyPatch,
    example_name: str,
    level: str,
    suggestion: str,
) -> None:
    http, _ = client
    expected = _stub_retrieve(monkeypatch, example_name)

    response = http.post("/v1/retrieve", headers=HEADERS, json=REQUEST)

    assert response.status_code == 200
    payload = response.json()
    _schema("retrieval-response.v1.schema.json").validate(payload)
    assert payload["correlationId"] == response.headers["X-Correlation-Id"] == CORRELATION
    assert payload["confidence"]["level"] == level
    assert payload["confidence"]["suggestion"] == suggestion
    assert payload["knowledgeVersion"] == expected["knowledgeVersion"]
    if level == "sin_respuesta":
        assert payload["results"] == []
    else:
        assert len(payload["results"]) >= 1


def test_baja_derivar_matches_published_schema(
    client: tuple[TestClient, Mock], monkeypatch: pytest.MonkeyPatch
) -> None:
    http, _ = client
    body = _example("response-baja.json")
    body["correlationId"] = CORRELATION
    body["confidence"] = {"level": "baja", "score": 0.55, "suggestion": "derivar"}
    body["results"] = body["results"][:1]
    monkeypatch.setattr("wa_knowledge_rag.api.app.retrieve", Mock(return_value=body))
    monkeypatch.setattr("wa_knowledge_rag.api.app.transaction", MagicMock())
    monkeypatch.setattr("wa_knowledge_rag.api.app.query_log", Mock())

    response = http.post("/v1/retrieve", headers=HEADERS, json=REQUEST)

    assert response.status_code == 200
    payload = response.json()
    _schema("retrieval-response.v1.schema.json").validate(payload)
    assert payload["confidence"] == {"level": "baja", "score": 0.55, "suggestion": "derivar"}


@pytest.mark.parametrize(
    ("headers", "body", "status", "code", "correlation"),
    [
        (HEADERS | {"Authorization": "Bearer"}, REQUEST, 401, "UNAUTHENTICATED", CORRELATION),
        ({"X-Correlation-Id": CORRELATION}, REQUEST, 401, "UNAUTHENTICATED", CORRELATION),
        (HEADERS, REQUEST | {"audience": "interno"}, 403, "AUDIENCE_FORBIDDEN", CORRELATION),
        (
            {"Authorization": HEADERS["Authorization"]},
            REQUEST,
            400,
            "VALIDATION_ERROR",
            "",
        ),
        (HEADERS, {"query": ""}, 400, "VALIDATION_ERROR", CORRELATION),
        (
            HEADERS,
            REQUEST | {"filters": {"family": ["desconocida"]}},
            400,
            "VALIDATION_ERROR",
            CORRELATION,
        ),
    ],
)
def test_error_responses_match_published_schema(
    client: tuple[TestClient, Mock],
    headers: dict[str, str],
    body: dict,
    status: int,
    code: str,
    correlation: str,
) -> None:
    http, embedder = client
    response = http.post("/v1/retrieve", headers=headers, json=body)

    assert response.status_code == status
    payload = response.json()
    _schema("error.v1.schema.json").validate(payload)
    assert payload["code"] == code
    assert payload["correlationId"] == response.headers.get("X-Correlation-Id", "") == correlation
    assert "private" not in response.text.lower()
    embedder.embed_query.assert_not_called()


def test_unavailable_matches_published_schema(client: tuple[TestClient, Mock], monkeypatch: pytest.MonkeyPatch) -> None:
    http, embedder = client
    embedder.embed_query.side_effect = EmbeddingProviderUnavailable()

    response = http.post("/v1/retrieve", headers=HEADERS, json=REQUEST)

    assert response.status_code == 503
    payload = response.json()
    _schema("error.v1.schema.json").validate(payload)
    assert payload == {
        "code": "KNOWLEDGE_UNAVAILABLE",
        "message": "La base de conocimiento no está disponible.",
        "correlationId": CORRELATION,
    }
    assert response.headers["X-Correlation-Id"] == CORRELATION


def test_database_failure_is_knowledge_unavailable(
    client: tuple[TestClient, Mock], monkeypatch: pytest.MonkeyPatch
) -> None:
    http, _ = client
    monkeypatch.setattr(
        "wa_knowledge_rag.retrieval.service.transaction",
        Mock(side_effect=psycopg.OperationalError("private detail")),
    )

    response = http.post("/v1/retrieve", headers=HEADERS, json=REQUEST)

    assert response.status_code == 503
    payload = response.json()
    _schema("error.v1.schema.json").validate(payload)
    assert payload["code"] == "KNOWLEDGE_UNAVAILABLE"
    assert "private detail" not in response.text


def test_health_shape_matches_contract(client: tuple[TestClient, Mock]) -> None:
    http, _ = client
    response = http.get("/health")

    assert response.status_code == 503
    payload = response.json()
    assert set(payload) == {"status", "database", "index", "knowledgeVersion"}
    assert payload["status"] == "degraded"
    assert payload["database"] in {"ok", "unavailable"}
    assert payload["index"] in {"ok", "unavailable"}
    assert payload["knowledgeVersion"] is None


def test_published_examples_validate_against_schemas() -> None:
    _schema("retrieval-request.v1.schema.json").validate(_example("request.json"))
    for name in ("response-alta.json", "response-baja.json", "response-sin-respuesta.json"):
        _schema("retrieval-response.v1.schema.json").validate(_example(name))
    _schema("error.v1.schema.json").validate(_example("error.json"))


def test_default_thresholds_match_contract() -> None:
    settings = Settings()
    assert settings.confidence_threshold == 0.70
    assert settings.min_relevance == 0.45
    assert settings.ambiguity_margin == 0.05
    assert settings.lexical_bonus == 0.05
    assert settings.port == 8102


def test_internal_error_matches_published_schema(
    client: tuple[TestClient, Mock], monkeypatch: pytest.MonkeyPatch
) -> None:
    http, _ = client
    monkeypatch.setattr("wa_knowledge_rag.api.app.retrieve", Mock(side_effect=RuntimeError("private detail")))

    response = http.post("/v1/retrieve", headers=HEADERS, json=REQUEST)

    assert response.status_code == 500
    payload = response.json()
    _schema("error.v1.schema.json").validate(payload)
    assert payload["code"] == "INTERNAL_ERROR"
    assert payload["correlationId"] == response.headers["X-Correlation-Id"] == CORRELATION
    assert "private detail" not in response.text
