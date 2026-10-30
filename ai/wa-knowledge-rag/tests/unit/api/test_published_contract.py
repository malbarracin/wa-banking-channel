"""Los ejemplos publicados para H9 deben conservar compatibilidad con H8."""

import json
import re
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from wa_knowledge_rag.api import create_app
from wa_knowledge_rag.api.deps import RetrievalRequest
from wa_knowledge_rag.retrieval.formatter import InsufficientResponse, RetrievalResponse

CONTRACT = Path(__file__).resolve().parents[5] / "shared/contracts/wa-knowledge-rag/retrieval.md"
EXAMPLES = re.findall(r"```json\s*\n(.*?)\n```", CONTRACT.read_text(encoding="utf-8"), re.DOTALL)


@pytest.mark.parametrize("index,model", [(0, RetrievalRequest), (1, RetrievalResponse), (2, InsufficientResponse)])
def test_published_examples_match_models(index, model):
    body = json.loads(EXAMPLES[index])
    parsed = model.model_validate(body)
    assert parsed.model_dump(mode="json", by_alias=True) == body


def test_published_error_matches_http_response():
    assert len(EXAMPLES) == 4
    expected = json.loads(EXAMPLES[3])
    with TestClient(create_app()) as client:
        response = client.post("/retrieval", json={"correlationId": expected["correlationId"]})
    assert response.status_code == 400
    assert response.json() == expected
