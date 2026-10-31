"""Los ejemplos publicados para H0.2 corresponden a los modelos reales de H9."""

import json
import re
from pathlib import Path

import pytest

from wa_conversation_agent.models import ContractModel, TurnRequest, TurnResponse

CONTRACT = Path(__file__).resolve().parents[3] / "shared/contracts/wa-conversation-agent/turn.md"


def test_published_transport_and_retry_contract() -> None:
    document = CONTRACT.read_text(encoding="utf-8")
    for required in (
        "POST /api/v1/agent/turn",
        "Authorization",
        "Bearer <token de servicio>",
        "X-Correlation-Id",
        "X-Conversation-Id",
        "X-Message-Id",
        "X-Idempotency-Key",
        "TTL por defecto de siete días",
        "HTTP 409",
        "H0.2 reintenta timeouts y HTTP 5xx; no reintenta HTTP 4xx",
        "H9 es responsable de no duplicar operaciones sensibles",
    ):
        assert required in document


def test_published_contract_examples_and_references() -> None:
    document = CONTRACT.read_text(encoding="utf-8")
    examples = re.findall(r"```json\s*\n(.*?)\n```", document, re.DOTALL)
    assert len(examples) == 2
    for example, model in zip(examples, (TurnRequest, TurnResponse), strict=True):
        body = json.loads(example)
        assert model.model_validate(body).model_dump(mode="json") == body
    request, response = map(json.loads, examples)
    for field in ("correlationId", "conversationId", "messageId"):
        assert request[field] == response[field]
    reference = "../wa-knowledge-rag/retrieval.md"
    assert reference in document
    assert (CONTRACT.parent / reference).is_file()
    for expected in (
        "AGENT_PORT=8103",
        "H9_BASE_URL=http://localhost:8099",
        "por configuración de entorno del lado de H0.2",
    ):
        assert expected in document


@pytest.mark.parametrize("model", [TurnRequest, TurnResponse])
def test_published_fields_are_complete(model: type[ContractModel]) -> None:
    document = CONTRACT.read_text(encoding="utf-8")
    for field in model.model_fields:
        assert f"`{field}`" in document
