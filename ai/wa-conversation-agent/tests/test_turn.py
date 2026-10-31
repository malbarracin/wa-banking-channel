"""Ejemplos de H9TurnClient, sin red ni modelo real."""

from collections.abc import Iterator
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient
from google.adk.agents import Agent
from google.adk.models.base_llm import BaseLlm
from google.adk.models.llm_response import LlmResponse
from google.adk.sessions import InMemorySessionService
from google.genai import types

from wa_conversation_agent.config import Settings
from wa_conversation_agent.idempotency import MemoryIdempotencyStore
from wa_conversation_agent.models import TurnRequest, TurnResponse
from wa_conversation_agent.server import TurnUnavailable, create_app, main
from wa_conversation_agent.turn import TurnProcessor

TOKEN = "synthetic-service-token"  # noqa: S105 — credencial sintética exclusiva del test, sin acceso real.
PAYLOAD = {
    "correlationId": "7aa50f54-a81c-45d7-912f-aabdf69a07a2",
    "conversationId": "conversation-test",
    "messageId": "message-test",
    "customerAddress": "synthetic-address",
    "body": "Hola",
    "context": {"technicalContext": {"state": "INITIAL"}, "history": []},
}
HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "X-Correlation-Id": str(PAYLOAD["correlationId"]),
    "X-Conversation-Id": "conversation-test",
    "X-Message-Id": "message-test",
    "X-Idempotency-Key": "message-test",
}


class _StubModel(BaseLlm):
    async def generate_content_async(self, llm_request, stream=False):
        yield LlmResponse(
            content=types.Content(
                role="model",
                parts=[types.Part(text="Hola, soy el asistente del banco por WhatsApp.")],
            )
        )


def _stub_processor() -> TurnProcessor:
    return TurnProcessor.from_settings(
        Settings(service_token=TOKEN),
        agent=Agent(name="stub", model=_StubModel(model="fake"), tools=[], instruction="Responde breve."),
        session_service=InMemorySessionService(),
        memory_service=None,
        idempotency=MemoryIdempotencyStore(),
    )


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> Iterator[TestClient]:
    monkeypatch.setenv("AGENT_SERVICE_TOKEN", TOKEN)
    with TestClient(create_app(Settings(service_token=TOKEN), processor=_stub_processor())) as test_client:
        yield test_client


def test_java_client_example(client: TestClient) -> None:
    assert TurnRequest.model_validate(PAYLOAD).model_dump(mode="json") == PAYLOAD
    response = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    assert response.status_code == 200
    result = TurnResponse.model_validate(response.json())
    for key in ("correlationId", "conversationId", "messageId"):
        assert response.json()[key] == PAYLOAD[key]
    assert result.reply.body
    assert result.escalate is False
    assert result.toolsUsed == []
    assert set(response.json()) == {"correlationId", "conversationId", "messageId", "reply", "escalate", "toolsUsed"}


@pytest.mark.parametrize("authorization", [None, "Bearer wrong", "Basic wrong", "Bearer"])
def test_unauthorized(client: TestClient, authorization: str | None) -> None:
    headers = {key: value for key, value in HEADERS.items() if key != "Authorization"}
    if authorization is not None:
        headers["Authorization"] = authorization
    response = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=headers)
    assert response.status_code == 401
    assert response.json() == {"detail": "No autorizado"}
    assert TOKEN not in response.text


def test_empty_configured_token() -> None:
    with TestClient(create_app(Settings(service_token=""), processor=_stub_processor())) as client:
        assert client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS).status_code == 401


@pytest.mark.parametrize("field", list(PAYLOAD))
@pytest.mark.parametrize("value", [None])
def test_invalid_fields(client: TestClient, field: str, value: object) -> None:
    payload = {**PAYLOAD, field: value}
    response = client.post("/api/v1/agent/turn", json=payload, headers=HEADERS)
    assert response.status_code == 400
    assert response.json() == {"detail": "Revisá los datos del mensaje para continuar."}


@pytest.mark.parametrize("field", [field for field in PAYLOAD if field != "body"])
@pytest.mark.parametrize("value", ["", "   "])
def test_empty_required_fields(client: TestClient, field: str, value: str) -> None:
    response = client.post("/api/v1/agent/turn", json={**PAYLOAD, field: value}, headers=HEADERS)
    assert response.status_code == 400
    assert response.json() == {"detail": "Revisá los datos del mensaje para continuar."}


@pytest.mark.parametrize("body", ["", "   "])
def test_empty_body_matches_java_contract(client: TestClient, body: str) -> None:
    payload = {**PAYLOAD, "body": body}
    assert TurnRequest.model_validate(payload).body == body
    response = client.post("/api/v1/agent/turn", json=payload, headers=HEADERS)
    assert response.status_code == 200
    for key in ("correlationId", "conversationId", "messageId"):
        assert response.json()[key] == payload[key]


@pytest.mark.parametrize("field", list(PAYLOAD))
def test_missing_fields(client: TestClient, field: str) -> None:
    payload = {key: value for key, value in PAYLOAD.items() if key != field}
    assert client.post("/api/v1/agent/turn", json=payload, headers=HEADERS).status_code == 400


@pytest.mark.parametrize("context", [{}, {"technicalContext": {}, "history": "private-data"}])
def test_invalid_context(client: TestClient, context: object) -> None:
    response = client.post("/api/v1/agent/turn", json={**PAYLOAD, "context": context}, headers=HEADERS)
    assert response.status_code == 400
    assert "private-data" not in response.text


@pytest.mark.parametrize("header", [key for key in HEADERS if key != "Authorization"])
def test_missing_header(client: TestClient, header: str) -> None:
    headers = {key: value for key, value in HEADERS.items() if key != header}
    assert client.post("/api/v1/agent/turn", json=PAYLOAD, headers=headers).status_code == 400


def test_unavailable(client: TestClient) -> None:
    async def fail(_payload: TurnRequest, _key: str) -> TurnResponse:
        raise TurnUnavailable("private failure")

    with patch.object(client.app.state.processor, "process", side_effect=fail):
        response = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    assert response.status_code == 503
    assert "private failure" not in response.text


def test_server_port(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_PORT", "8103")
    with patch("wa_conversation_agent.server.uvicorn.run") as run:
        main()
    assert run.call_args.kwargs["port"] == 8103


def test_nested_context_and_history(client: TestClient) -> None:
    payload = {
        **PAYLOAD,
        "context": {
            "technicalContext": {"state": "ACTIVE", "flags": [True, None, 2], "session": {"verified": False}},
            "history": [{"role": "user", "body": "Consulta sintética", "metadata": {"sequence": 1}}],
        },
    }
    assert TurnRequest.model_validate(payload).model_dump(mode="json") == payload
    response = client.post("/api/v1/agent/turn", json=payload, headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["messageId"] == payload["messageId"]


@pytest.mark.parametrize("header", [key for key in HEADERS if key != "Authorization"])
@pytest.mark.parametrize("value", ["", "   "])
def test_empty_header(client: TestClient, header: str, value: str) -> None:
    response = client.post("/api/v1/agent/turn", json=PAYLOAD, headers={**HEADERS, header: value})
    assert response.status_code == 400
    assert response.json() == {"detail": "Revisá los datos del mensaje para continuar."}


@pytest.mark.parametrize("field", ["technicalContext", "history"])
def test_missing_context_field(client: TestClient, field: str) -> None:
    context = {"technicalContext": {}, "history": []}
    context.pop(field)
    response = client.post("/api/v1/agent/turn", json={**PAYLOAD, "context": context}, headers=HEADERS)
    assert response.status_code == 400
    assert response.json() == {"detail": "Revisá los datos del mensaje para continuar."}


def test_malformed_json_is_sanitized(client: TestClient) -> None:
    response = client.post(
        "/api/v1/agent/turn",
        content='{"private-data":',
        headers={**HEADERS, "Content-Type": "application/json"},
    )
    assert response.status_code == 400
    assert response.json() == {"detail": "Revisá los datos del mensaje para continuar."}
