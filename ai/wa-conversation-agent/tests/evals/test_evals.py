"""Modelo real; dobles de H7/H8, sin operaciones bancarias ni Redis."""

import asyncio
import json
import os
from pathlib import Path
from typing import Any

import pytest
from google.adk.evaluation.agent_evaluator import AgentEvaluator
from google.adk.tools.base_tool import BaseTool
from google.genai.types import FunctionDeclaration

from wa_conversation_agent.agent import root_agent
from wa_conversation_agent.guardrails import escalar_a_humano
from wa_conversation_agent.knowledge import consultar_conocimiento
from wa_conversation_agent.mcp_tools import before_tool_callback

HERE = Path(__file__).parent
SCHEMAS = HERE.parents[3] / "shared/contracts/wa-mcp-layer/schemas"


class ContractTool(BaseTool):
    """Mantiene declaración de H7 y callback real; solo sustituye transporte."""

    def __init__(self, capability: str, result: dict[str, Any]) -> None:
        schema = json.loads((SCHEMAS / f"{capability}.v1.schema.json").read_text(encoding="utf-8"))
        super().__init__(name=capability.replace(".", "_") + "_v1", description=schema["description"])
        self.parameters = {
            "type": "object",
            "properties": {"parametros": schema["properties"]["entrada"]["properties"]["parametros"]},
            "required": ["parametros"],
        }
        self.result = result

    def _get_declaration(self) -> FunctionDeclaration:
        return FunctionDeclaration(name=self.name, description=self.description, parameters_json_schema=self.parameters)

    async def run_async(self, *, args: dict[str, Any], tool_context: Any) -> Any:
        decision = before_tool_callback(self, args, tool_context)
        return decision if decision is not None else self.result


@pytest.mark.evals
@pytest.mark.parametrize("evalset", sorted(HERE.glob("*.evalset.json")), ids=lambda path: path.stem)
async def test_real_model(evalset: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    if not os.environ.get("GOOGLE_API_KEY"):
        pytest.skip("Requiere GOOGLE_API_KEY; no se simula la evaluación del modelo")
    import httpx
    import respx

    fixtures = json.loads(await asyncio.to_thread((HERE / "service_fixtures.json").read_text, encoding="utf-8"))
    tools = [ContractTool(name, result) for name, result in fixtures["h7"].items()]
    monkeypatch.setattr(root_agent, "tools", [*tools, consultar_conocimiento, escalar_a_humano])
    monkeypatch.setenv("AGENT_RAG_URL", "https://rag.example.test")
    dataset = json.loads(await asyncio.to_thread(evalset.read_text, encoding="utf-8"))
    scenario = dataset["eval_set_id"]
    payload = fixtures["h8"].get(scenario, fixtures["h8"]["sin_respuesta"])
    with respx.mock(assert_all_called=scenario in fixtures["h8"]) as router:
        router.post("https://rag.example.test/retrieval").mock(return_value=httpx.Response(200, json=payload))
        await AgentEvaluator.evaluate(
            agent_module="wa_conversation_agent",
            eval_dataset_file_path_or_dir=str(evalset),
            num_runs=1,
            print_detailed_results=True,
        )
