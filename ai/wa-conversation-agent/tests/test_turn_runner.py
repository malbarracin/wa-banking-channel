"""Criterios TASK-151: Runner, idempotencia, 409 y tope/timeout con modelo falso."""

from __future__ import annotations

import asyncio
from collections.abc import Iterator
from typing import Any
from unittest.mock import AsyncMock

import pytest
from fastapi.testclient import TestClient
from google.adk.agents import Agent
from google.adk.models.base_llm import BaseLlm
from google.adk.models.llm_response import LlmResponse
from google.adk.sessions import InMemorySessionService
from google.genai import types
from httpx import ASGITransport, AsyncClient

from wa_conversation_agent.config import Settings
from wa_conversation_agent.guardrails import ESCALATION_CONFIRMATION
from wa_conversation_agent.idempotency import MemoryIdempotencyStore
from wa_conversation_agent.models import TurnResponse
from wa_conversation_agent.server import create_app
from wa_conversation_agent.turn import ConflictError, TurnProcessor

TOKEN = "synthetic-service-token"  # noqa: S105 — credencial sintética exclusiva del test.
PAYLOAD = {
    "correlationId": "7aa50f54-a81c-45d7-912f-aabdf69a07a2",
    "conversationId": "conversation-runner",
    "messageId": "message-runner",
    "customerAddress": "synthetic-address",
    "body": "Hola",
    "context": {"technicalContext": {"state": "INITIAL", "link_status": "active"}, "history": []},
}
HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "X-Correlation-Id": str(PAYLOAD["correlationId"]),
    "X-Conversation-Id": PAYLOAD["conversationId"],
    "X-Message-Id": PAYLOAD["messageId"],
    "X-Idempotency-Key": PAYLOAD["messageId"],
}


def llm_text(text: str) -> LlmResponse:
    return LlmResponse(content=types.Content(role="model", parts=[types.Part(text=text)]))


def llm_call(name: str, args: dict[str, Any] | None = None) -> LlmResponse:
    return LlmResponse(
        content=types.Content(
            role="model",
            parts=[types.Part(function_call=types.FunctionCall(name=name, args=args or {}))],
        )
    )


class GreetingModel(BaseLlm):
    async def generate_content_async(self, llm_request, stream=False):
        yield llm_text("Hola, soy el asistente del banco por WhatsApp.")


_TOOL_ONCE_PHASE = [0]
_SLOW_DELAY = [1.0]


class ToolOnceModel(BaseLlm):
    """Primera respuesta: llama la herramienta; luego cierra con texto."""

    async def generate_content_async(self, llm_request, stream=False):
        _TOOL_ONCE_PHASE[0] += 1
        if _TOOL_ONCE_PHASE[0] == 1:
            yield llm_call("marcar_consulta")
        else:
            yield llm_text("Consulta registrada.")


class LoopingToolModel(BaseLlm):
    async def generate_content_async(self, llm_request, stream=False):
        yield llm_call("marcar_consulta")


class SlowModel(BaseLlm):
    async def generate_content_async(self, llm_request, stream=False):
        await asyncio.sleep(_SLOW_DELAY[0])
        yield llm_text("Tarde.")


class RepeatedProductModel(BaseLlm):
    async def generate_content_async(self, llm_request, stream=False):
        yield llm_call("cuentas_confirmar_transferencia_v1")


def _tool_probe(invoked: list[str]):
    def marcar_consulta() -> dict[str, str]:
        """Marca una consulta sintética para verificar idempotencia."""
        invoked.append("marcar_consulta")
        return {"status": "ok", "frase": "Consulta registrada."}

    return marcar_consulta


def build_processor(
    model: BaseLlm,
    *,
    tools: list[Any] | None = None,
    settings: Settings | None = None,
    idempotency: MemoryIdempotencyStore | None = None,
) -> TurnProcessor:
    config = settings or Settings(
        service_token=TOKEN,
        max_tool_calls_per_turn=8,
        turn_timeout_seconds=30.0,
        tool_timeout_seconds=10.0,
    )
    agent = Agent(name="turn_test", model=model, tools=tools or [], instruction="Responde breve.")
    return TurnProcessor.from_settings(
        config,
        agent=agent,
        session_service=InMemorySessionService(),
        memory_service=None,
        idempotency=idempotency or MemoryIdempotencyStore(),
    )


@pytest.fixture
def greeting_client(monkeypatch: pytest.MonkeyPatch) -> Iterator[TestClient]:
    monkeypatch.setenv("AGENT_SERVICE_TOKEN", TOKEN)
    processor = build_processor(GreetingModel(model="fake"))
    with TestClient(create_app(Settings(service_token=TOKEN), processor=processor)) as client:
        yield client


def test_full_turn_returns_matching_identifiers(greeting_client: TestClient) -> None:
    response = greeting_client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    assert response.status_code == 200
    result = TurnResponse.model_validate(response.json())
    for key in ("correlationId", "conversationId", "messageId"):
        assert response.json()[key] == PAYLOAD[key]
    assert result.reply.body
    assert result.escalate is False
    assert result.toolsUsed == []


def test_idempotent_replay_invokes_tool_once(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_SERVICE_TOKEN", TOKEN)
    _TOOL_ONCE_PHASE[0] = 0
    invoked: list[str] = []
    store = MemoryIdempotencyStore()
    processor = build_processor(ToolOnceModel(model="fake"), tools=[_tool_probe(invoked)], idempotency=store)
    with TestClient(create_app(Settings(service_token=TOKEN), processor=processor)) as client:
        first = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
        second = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json() == second.json()
    assert invoked == ["marcar_consulta"]
    assert first.json()["toolsUsed"] == ["marcar_consulta"]


@pytest.mark.asyncio
async def test_concurrent_same_key_returns_409(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_SERVICE_TOKEN", TOKEN)
    started = asyncio.Event()
    release = asyncio.Event()

    class BlockingProcessor(TurnProcessor):
        async def _run_turn(self, payload, budget):  # type: ignore[no-untyped-def]
            started.set()
            await release.wait()
            return await super()._run_turn(payload, budget)

    base = build_processor(GreetingModel(model="fake"))
    processor = BlockingProcessor(
        settings=base.settings,
        session_service=base.session_service,
        memory_service=base.memory_service,
        idempotency=base.idempotency,
        agent=base.agent,
    )
    app = create_app(Settings(service_token=TOKEN), processor=processor)
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        first_task = asyncio.create_task(client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS))
        await started.wait()
        second = await client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
        release.set()
        first = await first_task
    assert first.status_code == 200
    assert second.status_code == 409
    assert second.json() == {"detail": "El mensaje ya se está procesando."}


def test_tool_limit_escalates(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_SERVICE_TOKEN", TOKEN)
    invoked: list[str] = []
    settings = Settings(service_token=TOKEN, max_tool_calls_per_turn=1, turn_timeout_seconds=30.0)
    processor = build_processor(LoopingToolModel(model="fake"), tools=[_tool_probe(invoked)], settings=settings)
    with TestClient(create_app(settings, processor=processor)) as client:
        response = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    assert response.status_code == 200
    body = response.json()
    assert body["escalate"] is True
    assert body["reply"]["body"] == ESCALATION_CONFIRMATION
    assert invoked == ["marcar_consulta"]


def test_turn_timeout_escalates(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_SERVICE_TOKEN", TOKEN)
    _SLOW_DELAY[0] = 1.0
    settings = Settings(service_token=TOKEN, turn_timeout_seconds=0.05, max_tool_calls_per_turn=8)
    processor = build_processor(SlowModel(model="fake"), settings=settings)
    with TestClient(create_app(settings, processor=processor)) as client:
        response = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    assert response.status_code == 200
    body = response.json()
    assert body["escalate"] is True
    assert body["reply"]["body"] == ESCALATION_CONFIRMATION


@pytest.mark.asyncio
async def test_failure_retains_reservation_to_prevent_repeated_operations() -> None:
    from wa_conversation_agent.models import TurnRequest

    store = MemoryIdempotencyStore()
    settings = Settings(service_token=TOKEN, turn_timeout_seconds=30.0)
    processor = build_processor(GreetingModel(model="fake"), settings=settings, idempotency=store)
    request = TurnRequest.model_validate(PAYLOAD)
    processor._run_turn = AsyncMock(side_effect=RuntimeError("boom"))  # type: ignore[method-assign]
    with pytest.raises(RuntimeError):
        await processor.process(request, PAYLOAD["messageId"])
    processor._run_turn = AsyncMock(  # type: ignore[method-assign]
        return_value=TurnResponse.model_validate(
            {
                **{k: PAYLOAD[k] for k in ("correlationId", "conversationId", "messageId")},
                "reply": {"body": "ok"},
                "escalate": False,
                "toolsUsed": [],
            }
        )
    )
    with pytest.raises(ConflictError):
        await processor.process(request, PAYLOAD["messageId"])
    processor._run_turn.assert_not_awaited()


def test_tool_timeout_escalates_before_turn_timeout() -> None:
    async def marcar_consulta() -> dict[str, str]:
        """Herramienta sintética que no termina dentro del límite."""
        await asyncio.Event().wait()
        return {"status": "ok"}

    settings = Settings(service_token=TOKEN, tool_timeout_seconds=0.03, turn_timeout_seconds=30)
    processor = build_processor(LoopingToolModel(model="fake"), tools=[marcar_consulta], settings=settings)
    with TestClient(create_app(settings, processor=processor)) as client:
        response = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
        replay = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["escalate"] is True
    assert response.json()["reply"]["body"] == ESCALATION_CONFIRMATION
    assert response.json()["toolsUsed"] == ["marcar_consulta"]
    assert response.json() == replay.json()


def test_product_operation_runs_at_most_once_even_if_model_repeats_call() -> None:
    invoked: list[str] = []

    def cuentas_confirmar_transferencia_v1() -> dict[str, str]:
        """Operación sintética para comprobar que no se duplica."""
        invoked.append("transferencia")
        return {"status": "ok"}

    processor = build_processor(RepeatedProductModel(model="fake"), tools=[cuentas_confirmar_transferencia_v1])
    with TestClient(create_app(processor.settings, processor=processor)) as client:
        result = client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    assert result.status_code == 200
    assert result.json()["escalate"] is True
    assert invoked == ["transferencia"]


def test_reused_key_with_changed_payload_is_rejected(greeting_client: TestClient) -> None:
    first = greeting_client.post("/api/v1/agent/turn", json=PAYLOAD, headers=HEADERS)
    changed = greeting_client.post("/api/v1/agent/turn", json={**PAYLOAD, "body": "Otro"}, headers=HEADERS)
    assert first.status_code == 200
    assert changed.status_code == 409


@pytest.mark.asyncio
async def test_cancelled_turn_does_not_repeat_uncertain_operation() -> None:
    from wa_conversation_agent.models import TurnRequest

    started = asyncio.Event()
    invoked: list[str] = []

    async def marcar_consulta() -> dict[str, str]:
        """Simula una operación aplicada antes de perder la respuesta."""
        invoked.append("operacion")
        started.set()
        await asyncio.Event().wait()
        return {"status": "ok"}

    processor = build_processor(LoopingToolModel(model="fake"), tools=[marcar_consulta])
    request = TurnRequest.model_validate(PAYLOAD)
    task = asyncio.create_task(processor.process(request, request.messageId))
    try:
        await asyncio.wait_for(started.wait(), timeout=5)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        with pytest.raises(ConflictError):
            await processor.process(request, request.messageId)
        assert invoked == ["operacion"]
    finally:
        if not task.done():
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)
        await processor.aclose()


@pytest.mark.asyncio
async def test_runner_merges_safe_memory_and_purges_on_unlink() -> None:
    import json

    from fakeredis.aioredis import FakeRedis

    from wa_conversation_agent.memory import RedisLongTermMemoryService, RedisSessionService, RedisStore
    from wa_conversation_agent.models import TurnRequest

    client = FakeRedis()
    store = RedisStore(client, "synthetic-key-secret-for-tests-0001")
    sessions = RedisSessionService(store)
    memory = RedisLongTermMemoryService(store)
    base = build_processor(GreetingModel(model="fake"))
    processor = TurnProcessor(base.settings, sessions, memory, base.idempotency, agent=base.agent)
    key = store.key("long", processor.app_name, "synthetic-customer")
    await client.set(key, json.dumps({"language": "es", "summary": "solicito_ayuda"}))
    payload = {
        **PAYLOAD,
        "context": {
            "history": [],
            "technicalContext": {
                "customer_id": "synthetic-customer",
                "link_status": "active",
                "response_style": "breve",
                "credencial": "synthetic-transient-credential",
                "credencial_valida": True,
                "vinculo_activo": True,
            },
        },
    }
    try:
        first = await processor.process(TurnRequest.model_validate(payload), payload["messageId"])
        assert first.escalate is False
        assert json.loads(await client.get(key)) == {
            "language": "es",
            "summary": "solicito_ayuda",
            "response_style": "breve",
        }
        session = await sessions.get_session(
            app_name=processor.app_name, user_id="synthetic-customer", session_id=payload["conversationId"]
        )
        assert session is not None
        assert "credencial" not in session.state
        payload["messageId"] = "unlink-message"
        payload["context"]["technicalContext"] = {"customer_id": "synthetic-customer", "link_status": "unlinked"}
        await processor.process(TurnRequest.model_validate(payload), payload["messageId"])
        assert not await client.exists(key)
    finally:
        await processor.aclose()
