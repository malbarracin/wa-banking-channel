"""Contrato público H8 con HTTP simulado y sin modelo real."""

import json
from typing import Any

import httpx
import pytest
import respx

from wa_conversation_agent.knowledge import consultar_conocimiento

URL = "http://knowledge.test:8102/retrieval"


@pytest.fixture(autouse=True)
def configure(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_RAG_URL", "http://knowledge.test:8102/")
    monkeypatch.setenv("AGENT_RAG_TIMEOUT_SECONDS", "2.5")


def evidence(confidence: str = "high") -> dict[str, Any]:
    return {
        "results": [
            {
                "content": "Consultá los canales oficiales.",
                "citation": {
                    "source_id": "00000000-0000-4000-8000-000000000001",
                    "title": "Guía sintética",
                    "section": None,
                    "version": 1,
                    "source_uri": None,
                },
                "score": 0.91,
            }
        ],
        "confidence": confidence,
        "version": "kb-v1",
        "correlationId": "turn-1",
    }


@pytest.mark.parametrize("confidence", ["high", "medium"])
@respx.mock
async def test_evidence_and_public_request(confidence: str) -> None:
    payload = evidence(confidence)
    route = respx.post(URL).mock(return_value=httpx.Response(200, json=payload))
    assert await consultar_conocimiento("  Requisitos  ", "turn-1") == payload
    assert json.loads(route.calls.last.request.content) == {
        "query": "Requisitos",
        "audience": "public",
        "top_k": 5,
        "correlationId": "turn-1",
    }
    assert route.calls.last.request.extensions["timeout"]["read"] == 2.5


@respx.mock
async def test_insufficient() -> None:
    payload = {"results": [], "confidence": "low", "reason": "sin_respuesta_suficiente", "correlationId": "turn-1"}
    respx.post(URL).mock(return_value=httpx.Response(200, json=payload))
    assert await consultar_conocimiento("Pregunta", "turn-1") == payload


@pytest.mark.parametrize("status", [200, 400, 503])
@respx.mock
async def test_public_audience_and_turn_correlation_on_every_outcome(status: int) -> None:
    correlation_id = "turn:synthetic-147.2"
    payload = {
        "results": [],
        "confidence": "low",
        "reason": "sin_respuesta_suficiente",
        "correlationId": correlation_id,
    }
    route = respx.post(URL).mock(return_value=httpx.Response(status, json=payload))

    result = await consultar_conocimiento("Consulta sintética", correlation_id)

    assert route.call_count == 1
    request = json.loads(route.calls.last.request.content)
    assert request["audience"] == "public"
    assert request["correlationId"] == correlation_id
    assert result["correlationId"] == correlation_id
    assert result["results"] == []
    assert result["reason"] == ("no_disponible" if status == 503 else "sin_respuesta_suficiente")


@pytest.mark.parametrize(
    "status,reason",
    [(400, "sin_respuesta_suficiente"), (503, "no_disponible"), (500, "no_disponible"), (302, "no_disponible")],
)
@respx.mock
async def test_errors_discard_body(status: int, reason: str) -> None:
    respx.post(URL).mock(return_value=httpx.Response(status, json={"error": "detalle no confiable"}))
    assert await consultar_conocimiento("Pregunta", "turn-1") == {
        "results": [],
        "confidence": "low",
        "reason": reason,
        "correlationId": "turn-1",
    }


@pytest.mark.parametrize("error", [httpx.ReadTimeout, httpx.ConnectError])
@respx.mock
async def test_transport_errors(error: type[httpx.HTTPError]) -> None:
    respx.post(URL).mock(side_effect=error("detalle interno"))
    result = await consultar_conocimiento("Pregunta", "turn-1")
    assert result["reason"] == "no_disponible"
    assert result["results"] == []


@pytest.mark.parametrize("defect", ["citation", "empty", "correlation", "low_content", "score", "json"])
@respx.mock
async def test_invalid_responses_fail_closed(defect: str) -> None:
    payload = evidence()
    if defect == "citation":
        del payload["results"][0]["citation"]
    elif defect == "empty":
        payload["results"] = []
    elif defect == "correlation":
        payload["correlationId"] = "another-turn"
    elif defect == "low_content":
        payload["confidence"] = "low"
    elif defect == "score":
        payload["results"][0]["score"] = 2
    response = httpx.Response(200, content=b"invalid") if defect == "json" else httpx.Response(200, json=payload)
    respx.post(URL).mock(return_value=response)
    result = await consultar_conocimiento("Pregunta", "turn-1")
    assert result["reason"] == "no_disponible"
    assert result["results"] == []


@pytest.mark.parametrize("query,correlation", [(" ", "turn-1"), ("x" * 10001, "turn-1"), ("Q", "bad id")])
@respx.mock
async def test_invalid_input_without_network(query: str, correlation: str) -> None:
    result = await consultar_conocimiento(query, correlation)
    assert result["reason"] == "sin_respuesta_suficiente"
    assert result["results"] == []
    assert not respx.calls
