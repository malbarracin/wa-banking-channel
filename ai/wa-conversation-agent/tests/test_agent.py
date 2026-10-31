"""Contrato de configuración del agente, sin invocar al modelo real."""

import importlib
from importlib.resources import files

import pytest
from google.adk.tools import FunctionTool

from wa_conversation_agent import agent
from wa_conversation_agent.knowledge import consultar_conocimiento


def test_knowledge_tool_has_adk_declaration() -> None:
    declaration = FunctionTool(consultar_conocimiento)._get_declaration()
    assert declaration is not None
    assert declaration.name == "consultar_conocimiento"
    assert declaration.parameters is not None
    assert set(declaration.parameters.properties) == {"query", "correlation_id"}


@pytest.mark.parametrize(
    "message",
    [
        "Hola, soy el asistente del banco por WhatsApp. ¿En qué te puedo ayudar hoy?",
        "Esa operación supera el monto permitido para este canal. Podés hacerla desde la app o sucursal.",
        "Por seguridad, necesitamos verificar tu identidad de nuevo para continuar.",
        "Por seguridad, volvé a verificar tu identidad para continuar",
        "Para ayudarte mejor, te comunico con una persona de nuestro equipo.",
    ],
)
def test_instruction_contains_exact_bank_messages(message: str) -> None:
    assert isinstance(agent.root_agent.instruction, str)
    assert message in agent.root_agent.instruction


def test_agent_loads_packaged_prompt_without_credentials(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    monkeypatch.delenv("AGENT_MODEL", raising=False)
    monkeypatch.chdir(files("wa_conversation_agent").parent)
    root_agent = importlib.reload(agent).root_agent
    assert root_agent.model == "gemini-2.5-flash"
    assert root_agent.tools == [consultar_conocimiento]
    prompt = files("wa_conversation_agent").joinpath("prompts/instruccion_v1.md").read_text(encoding="utf-8")
    assert root_agent.instruction == prompt
