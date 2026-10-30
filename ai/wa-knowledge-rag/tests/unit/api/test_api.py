"""Contrato HTTP con retrieval real y proveedores sintéticos, sin red."""

from contextlib import contextmanager
from unittest.mock import Mock, patch
from uuid import UUID

import pytest
from fastapi.testclient import TestClient
from structlog.contextvars import get_contextvars
from structlog.testing import capture_logs

from wa_knowledge_rag.api import create_app, main
from wa_knowledge_rag.api.deps import DatabaseRetrievalService, RetrievalRequest, get_service
from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.retrieval.hybrid import HybridRetriever
from wa_knowledge_rag.retrieval.scorer import RRF_MAX
from wa_knowledge_rag.store.models import SearchHit

BODY = {"query": "Requisitos de tarjeta", "audience": "public", "correlationId": "test-138"}


@pytest.fixture
def backend():
    hit = SearchHit(
        id=UUID(int=1),
        source_id=UUID(int=2),
        title="Guía",
        version=1,
        content="Contenido aprobado",
        section=None,
        source_uri=None,
        score=RRF_MAX,
    )
    repository = Mock()
    repository.search.return_value = [hit]
    retriever = HybridRetriever(repository, Settings(rag_confidence_high_min=0.9, rag_confidence_medium_min=0.6))

    class Service:
        def retrieve(self, request):
            assert get_contextvars()["correlationId"] == request.correlationId
            return retriever.retrieve(
                request.query,
                [1.0] * 768,
                audience=request.audience,
                correlation_id=request.correlationId,
                kb_version="kb-test",
                top_k=request.top_k,
                for_client=request.audience == "public",
            )

    app = create_app()
    app.dependency_overrides[get_service] = Service
    with TestClient(app) as client:
        yield client, repository


def test_health():
    with TestClient(create_app()) as client:
        assert client.get("/health").json() == {"status": "ok"}


@pytest.mark.parametrize("audience", ["public", "internal"])
def test_success_and_audience(backend, audience):
    client, repository = backend
    with capture_logs() as logs:
        response = client.post("/retrieval", json={**BODY, "audience": audience})
    assert response.status_code == 200
    body = response.json()
    assert body["confidence"] == "high"
    assert body["version"] == "kb-test"
    assert body["correlationId"] == BODY["correlationId"]
    assert body["results"][0]["citation"]["source_id"] == str(UUID(int=2))
    assert body["results"][0]["score"] == 1
    assert repository.search.call_args.kwargs == {"audience": audience, "top_k": 5}
    assert all(log["correlationId"] == BODY["correlationId"] for log in logs)
    assert BODY["query"] not in str(logs)


def test_low(backend):
    client, repository = backend
    repository.search.return_value = []
    assert client.post("/retrieval", json=BODY).json() == {
        "results": [],
        "confidence": "low",
        "reason": "sin_respuesta_suficiente",
        "correlationId": "test-138",
    }


@pytest.mark.parametrize("field", ["query", "audience", "correlationId"])
def test_required(backend, field):
    client, repository = backend
    body = {key: value for key, value in BODY.items() if key != field}
    response = client.post("/retrieval", json=body)
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "CONSULTA_INVALIDA"
    assert response.json()["correlationId"]
    repository.search.assert_not_called()


@pytest.mark.parametrize(
    "change",
    [
        {"query": " "},
        {"query": "a" * 10001},
        {"audience": "other"},
        {"top_k": 0},
        {"top_k": 101},
        {"top_k": True},
        {"top_k": "5"},
        {"correlationId": "unsafe\nvalue"},
        {"unexpected": "value"},
    ],
)
def test_invalid(backend, change):
    client, repository = backend
    response = client.post("/retrieval", json={**BODY, **change})
    assert response.status_code == 400
    assert "unsafe" not in response.text
    repository.search.assert_not_called()


def test_malformed_json(backend):
    client, _ = backend
    assert client.post("/retrieval", content="{").json()["error"]["code"] == "CONSULTA_INVALIDA"


def test_unavailable(backend):
    client, repository = backend
    repository.search.side_effect = RuntimeError("secret query and connection string")
    with capture_logs() as logs:
        response = client.post("/retrieval", json=BODY)
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "SERVICIO_NO_DISPONIBLE"
    assert response.json()["correlationId"] == "test-138"
    assert "secret" not in response.text + str(logs)


def test_top_k(backend):
    client, repository = backend
    client.post("/retrieval", json={**BODY, "top_k": 7})
    assert repository.search.call_args.kwargs["top_k"] == 7


def test_default_service_without_configuration():
    with TestClient(create_app()) as client:
        assert client.post("/retrieval", json=BODY).status_code == 503


def test_database_service():
    settings = Settings(google_api_key="synthetic-test-key")
    connection = Mock()
    connection.execute.return_value.fetchone.return_value = {"version": "snapshot"}
    connection.execute.return_value.fetchall.return_value = []
    provider = Mock()
    provider.models.embed_content.return_value.embeddings = [Mock(values=[1.0] * 768)]

    @contextmanager
    def fake_connect(settings):
        yield connection

    with (
        patch("wa_knowledge_rag.api.deps.Settings", return_value=settings),
        patch("wa_knowledge_rag.api.deps.connect", fake_connect),
        patch("wa_knowledge_rag.api.deps.genai.Client") as client,
    ):
        client.return_value.__enter__.return_value = provider
        response = DatabaseRetrievalService().retrieve(RetrievalRequest(**BODY))
    assert response.confidence == "low"
    assert provider.models.embed_content.call_args.kwargs["config"].task_type == "RETRIEVAL_QUERY"
    connection.commit.assert_called_once()


def test_main():
    with patch("wa_knowledge_rag.api.uvicorn.run") as run:
        main()
    assert run.call_args.kwargs["port"] == 8102


def test_module_entrypoint():
    import runpy

    with patch("wa_knowledge_rag.api.main") as start:
        runpy.run_module("wa_knowledge_rag.api", run_name="__main__")
    start.assert_called_once()


@pytest.mark.parametrize("body", [None, [], "consulta", 42, {**BODY, "query": None}])
def test_invalid_body_is_business_error(backend, body):
    client, repository = backend
    response = client.post("/retrieval", json=body)
    assert response.status_code == 400
    assert response.json()["error"] == {
        "code": "CONSULTA_INVALIDA",
        "message": "Revisá los datos de la consulta.",
    }
    assert response.json()["correlationId"]
    repository.search.assert_not_called()


def test_invalid_query_is_not_logged(backend):
    client, repository = backend
    query = "Documento sintético 12345678"
    with capture_logs() as logs:
        response = client.post("/retrieval", json={**BODY, "query": query, "top_k": 0})
    assert response.status_code == 400
    assert response.json()["correlationId"] == BODY["correlationId"]
    assert logs
    assert all(log["correlationId"] == BODY["correlationId"] for log in logs)
    assert query not in response.text + str(logs)
    repository.search.assert_not_called()


def test_correlation_after_failed_request(backend):
    client, repository = backend
    repository.search.side_effect = RuntimeError("synthetic failure")
    assert client.post("/retrieval", json=BODY).status_code == 503
    repository.search.side_effect = None
    with capture_logs() as logs:
        response = client.post("/retrieval", json={**BODY, "correlationId": "next-request"})
    assert response.status_code == 200
    assert response.json()["correlationId"] == "next-request"
    assert logs
    assert all(log["correlationId"] == "next-request" for log in logs)
    assert "correlationId" not in get_contextvars()
