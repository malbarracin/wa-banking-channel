"""Stub MCP local basado en los schemas publicados; no necesita el contenedor H7."""

import json
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from google.adk.tools import FunctionTool
from google.adk.tools.mcp_tool.mcp_session_manager import StreamableHTTPConnectionParams
from google.adk.tools.tool_context import ToolContext
from mcp.types import CallToolResult, ListToolsResult, Tool

from wa_conversation_agent.agent import root_agent
from wa_conversation_agent.config import Settings
from wa_conversation_agent.knowledge import consultar_conocimiento
from wa_conversation_agent.mcp_tools import ALLOWED, REVERIFY, SENSITIVE, before_tool_callback, create_toolset

CONTRACT = Path(__file__).resolve().parents[3] / "shared/contracts/wa-mcp-layer"


def test_mcp_transport_configuration(monkeypatch):
    monkeypatch.delenv("AGENT_MCP_URL", raising=False)
    default = create_toolset(Settings())
    assert isinstance(default._connection_params, StreamableHTTPConnectionParams)
    assert default._connection_params.url == "http://localhost:8101/mcp"
    monkeypatch.setenv("AGENT_MCP_URL", "http://127.0.0.1:18101/mcp")
    configured = create_toolset(Settings())
    assert configured._connection_params.url == "http://127.0.0.1:18101/mcp"


@pytest.fixture
def local_mcp_stub():
    """Servidor stub que publica las 32 herramientas con entrada del contrato."""
    envelope = json.loads((CONTRACT / "schemas/_envelope.json").read_text(encoding="utf-8"))
    tools = []
    for path in sorted((CONTRACT / "schemas").glob("*.v1.schema.json")):
        schema = json.loads(path.read_text(encoding="utf-8"))
        entrada = schema["properties"]["entrada"] if "properties" in schema else schema["entrada"]
        tools.append(
            Tool(
                name=path.name.removesuffix(".v1.schema.json").replace(".", "_") + "_v1",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "contexto": envelope["definitions"]["contexto"],
                        "parametros": entrada["properties"]["parametros"],
                    },
                    "required": ["contexto", "parametros"],
                },
            )
        )
    session = SimpleNamespace(
        list_tools=AsyncMock(return_value=ListToolsResult(tools=tools)),
        call_tool=AsyncMock(
            return_value=CallToolResult(
                content=[], structuredContent={"resultado": {"saldo": 10}, "auditoria": {"correlation_id": "test-148"}}
            )
        ),
    )
    owner = create_toolset(Settings())
    owner._mcp_session_manager = SimpleNamespace(create_session=AsyncMock(return_value=session))
    return owner, session


def context(**overrides):
    state = {
        "credencial": "synthetic-session-secret",
        "credencial_valida": True,
        "vinculo_activo": True,
        "numero_whatsapp": "+5491100000000",
        "correlation_id": "test-148",
    }
    state.update(overrides)
    return SimpleNamespace(state=state, spec=ToolContext)


def test_callback_allows_public_knowledge_without_identity():
    tool_context = SimpleNamespace(state={})
    args = {"query": "Información pública", "correlation_id": "test-148"}
    original = dict(args)
    result = before_tool_callback(FunctionTool(consultar_conocimiento), args, tool_context)
    assert result is None
    assert args == original
    assert "_h7_envelope" not in tool_context.__dict__


async def test_catalog_filter(local_mcp_stub):
    owner, session = local_mcp_stub
    selected = await owner.get_tools()
    rows = [
        line.split("|")
        for line in (CONTRACT / "capabilities.md").read_text(encoding="utf-8").splitlines()
        if line.startswith("| `") and len(line.split("|")) == 10
    ]
    expected = {row[1].strip().strip("`").replace(".", "_") + "_v1" for row in rows if row[6].strip() == "agente"}
    assert len(rows) == 32
    assert len(session.list_tools.return_value.tools) == 32
    assert {tool.name for tool in selected} == expected == set(ALLOWED)
    assert len(expected) == 27
    assert root_agent.before_tool_callback == before_tool_callback


@pytest.mark.parametrize(
    "invalid",
    [
        {"credencial": None},
        {"credencial": ""},
        {"credencial": "   "},
        {"credencial": 123},
        {"credencial_valida": False},
        {"vinculo_activo": False},
        {"credencial_valida": "true"},
        {"vinculo_activo": "true"},
        {"credencial_valida": None},
        {"vinculo_activo": None},
    ],
)
@pytest.mark.parametrize("tool_name", sorted(SENSITIVE))
async def test_identity_blocks_without_transport(local_mcp_stub, invalid, tool_name):
    owner, session = local_mcp_stub
    tool = next(t for t in await owner.get_tools() if t.name == tool_name)
    args = {"parametros": {}, "contexto": {"credencial": "model-forgery"}}
    ctx = context(**invalid)
    assert before_tool_callback(tool, args, ctx) == REVERIFY
    assert await tool.run_async(args=args, tool_context=ctx) == REVERIFY
    session.call_tool.assert_not_awaited()


async def test_credential_only_on_wire(local_mcp_stub, caplog):
    owner, session = local_mcp_stub
    tool = next(t for t in await owner.get_tools() if t.name == "cuentas_consultar_saldo_v1")
    args = {"parametros": {"numero": "test-account"}, "contexto": {"credencial": "forged"}}
    session.call_tool.return_value.structuredContent = {
        "resultado": {"credencial": "synthetic-session-secret", "text": "synthetic-session-secret"}
    }
    caplog.set_level("DEBUG")
    result = await tool.run_async(args=args, tool_context=context())
    wire = session.call_tool.call_args.kwargs["arguments"]
    assert wire["contexto"]["credencial"] == "synthetic-session-secret"
    assert wire["contexto"]["correlation_id"] == "test-148"
    assert "contexto" not in args
    assert "synthetic-session-secret" not in json.dumps(args) + str(tool._get_declaration()) + str(
        root_agent.instruction
    )
    assert "synthetic-session-secret" not in json.dumps(result) + caplog.text
    assert "contexto" not in str(tool._get_declaration())
    session.call_tool.side_effect = RuntimeError("synthetic-session-secret")
    result = await tool.run_async(args=args, tool_context=context())
    assert result["error"]["codigo"] == "SERVICIO_NO_DISPONIBLE"
    assert "synthetic-session-secret" not in str(result) + caplog.text


async def test_onboarding_without_identity_and_unknown_tool(local_mcp_stub):
    owner, session = local_mcp_stub
    tool = next(t for t in await owner.get_tools() if t.name == "vinculo_verificar_identidad_v1")
    assert await tool.run_async(args={"parametros": {}}, tool_context=context(credencial=None))
    assert session.call_tool.call_args.kwargs["arguments"]["contexto"]["credencial"] is None
    tool.name = "identidad_consultar_cliente_v1"
    assert before_tool_callback(tool, {}, context())["error"]["codigo"] == "CAPACIDAD_NO_DISPONIBLE"
