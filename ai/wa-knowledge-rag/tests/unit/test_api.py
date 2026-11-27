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
from wa_knowledge_rag.retrieval import service

CONTRACT = Path(__file__).resolve().parents[4] / "shared/contracts/wa-knowledge-rag"
CORRELATION = {"X-Correlation-Id": "request-synthetic"}
AUTH = {"Authorization": "Bearer consumer-fixture"}
REQUEST = {"query": "Consulta de prueba"}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv(
        "KB_CONSUMERS",
        json.dumps(
            [
                {
                    "id": "public-fixture",
                    "token_sha256": sha256(b"consumer-fixture").hexdigest().upper(),
                    "allow_internal": False,
                }
            ]
        ),
    )
    settings = Settings(database_url=SecretStr("dbname=synthetic"))
    embedder = Mock()
    embedder.embed_query.return_value = [1.0]
    with TestClient(create_app(settings, embedder=embedder)) as client:
        yield client, embedder


def assert_error(response, status, code, correlation_id):
    assert response.status_code == status
    payload = response.json()
    schema = json.loads((CONTRACT / "error.v1.schema.json").read_text())
    Draft202012Validator(schema).validate(payload)
    assert payload["code"] == code
    assert payload["correlationId"] == response.headers["X-Correlation-Id"] == correlation_id
    assert "private detail" not in response.text


@pytest.mark.parametrize(
    ("headers", "body", "status", "code", "correlation"),
    [
        (CORRELATION, REQUEST, 401, "UNAUTHENTICATED", CORRELATION["X-Correlation-Id"]),
        (
            CORRELATION | {"Authorization": "Bearer unknown-fixture"},
            REQUEST,
            401,
            "UNAUTHENTICATED",
            CORRELATION["X-Correlation-Id"],
        ),
        (
            CORRELATION | AUTH,
            REQUEST | {"audience": "interno"},
            403,
            "AUDIENCE_FORBIDDEN",
            CORRELATION["X-Correlation-Id"],
        ),
        (AUTH, REQUEST, 400, "VALIDATION_ERROR", ""),
        (CORRELATION | AUTH, REQUEST | {"topK": "5"}, 400, "VALIDATION_ERROR", CORRELATION["X-Correlation-Id"]),
        (CORRELATION | AUTH, {"query": ""}, 400, "VALIDATION_ERROR", CORRELATION["X-Correlation-Id"]),
        (CORRELATION | AUTH, REQUEST | {"filters": None}, 400, "VALIDATION_ERROR", CORRELATION["X-Correlation-Id"]),
        (
            CORRELATION | AUTH,
            REQUEST | {"filters": {"family": ["otra"]}},
            400,
            "VALIDATION_ERROR",
            CORRELATION["X-Correlation-Id"],
        ),
    ],
)
def test_rejected_before_retrieval(client, headers, body, status, code, correlation):
    http, embedder = client
    response = http.post("/v1/retrieve", headers=headers, json=body)
    assert_error(response, status, code, correlation)
    if status == 401:
        assert response.headers["WWW-Authenticate"] == "Bearer"
    embedder.embed_query.assert_not_called()


def test_accepts_integral_json_number_for_top_k(client, monkeypatch):
    http, _ = client
    body = REQUEST | {"topK": 5.0}
    schema = json.loads((CONTRACT / "retrieval-request.v1.schema.json").read_text())
    Draft202012Validator(schema).validate(body)
    result = json.loads((CONTRACT / "examples/response-sin-respuesta.json").read_text())
    result["correlationId"] = CORRELATION["X-Correlation-Id"]
    retrieve = Mock(return_value=result)
    monkeypatch.setattr("wa_knowledge_rag.api.app.retrieve", retrieve)
    monkeypatch.setattr("wa_knowledge_rag.api.app.transaction", MagicMock())
    monkeypatch.setattr("wa_knowledge_rag.api.app.query_log", Mock())

    response = http.post("/v1/retrieve", headers=AUTH | CORRELATION, json=body)

    assert response.status_code == 200, response.json()
    assert retrieve.call_args.kwargs["top_k"] == 5
    assert type(retrieve.call_args.kwargs["top_k"]) is int


@pytest.mark.parametrize(
    ("error", "status", "code", "embedding_errors"),
    [
        (EmbeddingProviderUnavailable(), 503, "KNOWLEDGE_UNAVAILABLE", 1),
        (psycopg.OperationalError("private detail"), 503, "KNOWLEDGE_UNAVAILABLE", 0),
        (RuntimeError("private detail"), 500, "INTERNAL_ERROR", 0),
    ],
)
def test_dependency_and_internal_errors(client, monkeypatch, error, status, code, embedding_errors):
    http, embedder = client
    if isinstance(error, EmbeddingProviderUnavailable):
        embedder.embed_query.side_effect = error
    else:
        monkeypatch.setattr(service, "transaction", Mock(side_effect=error))

    response = http.post("/v1/retrieve", headers=CORRELATION | AUTH, json=REQUEST)
    assert_error(response, status, code, CORRELATION["X-Correlation-Id"])
    # Keep the scrape offline while exercising the failure handling of the gauges.
    monkeypatch.setattr("wa_knowledge_rag.api.status.transaction", Mock(side_effect=psycopg.OperationalError()))
    assert f"kb_embedding_errors_total {embedding_errors}.0" in http.get("/metrics").text


@pytest.mark.parametrize("value", ["[invalid", '[{"id":"x","token_sha256":"fixture","allow_internal":true}]'])
def test_invalid_configuration_does_not_expose_values(value):
    with pytest.raises(ValueError, match=r"^KB_CONSUMERS tiene un formato inválido\.$"):
        create_app(Settings(consumers=SecretStr(value)))


def test_unconfigured_app_is_closed():
    with TestClient(create_app(Settings(database_url=None, consumers=None))) as client:
        assert client.get("/health").status_code == 503
        response = client.post("/v1/retrieve", headers=AUTH | CORRELATION, json=REQUEST)
        assert_error(response, 401, "UNAUTHENTICATED", CORRELATION["X-Correlation-Id"])
