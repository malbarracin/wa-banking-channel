"""Capacidades H7 v1; contexto técnico fuera de argumentos y eventos del modelo."""

import json
from typing import Any

from google.adk.agents.readonly_context import ReadonlyContext
from google.adk.tools.base_tool import BaseTool
from google.adk.tools.mcp_tool.mcp_session_manager import StreamableHTTPConnectionParams
from google.adk.tools.mcp_tool.mcp_toolset import McpToolset as MCPToolset
from google.adk.tools.tool_context import ToolContext
from google.genai.types import FunctionDeclaration

from .config import Settings

# Allowlist versionada contra capabilities.md: nunca se admiten nombres nuevos por prefijo.
PUBLIC = """acceso.emitir_credencial acceso.validar_credencial acceso.revocar_credencial
vinculo.aceptar_terminos vinculo.verificar_identidad vinculo.consultar_estado""".split()
PROTECTED = """vinculo.consultar_perfil vinculo.actualizar_preferencias vinculo.bloquear vinculo.desvincular
cuentas.listar_cuentas cuentas.consultar_saldo cuentas.consultar_movimientos cuentas.listar_terceros
cuentas.preparar_transferencia cuentas.confirmar_transferencia tarjetas.listar_tarjetas tarjetas.consultar_consumos
tarjetas.bloquear_temporal tarjetas.desbloquear_temporal tarjetas.bloquear_definitivo tarjetas.registrar_aviso_viaje
seguros.listar_polizas seguros.consultar_coberturas seguros.denunciar_siniestro seguros.consultar_siniestro
seguros.consultar_documentacion_pendiente""".split()
ALLOWED = [name.replace(".", "_") + "_v1" for name in PUBLIC + PROTECTED]
SENSITIVE = {name.replace(".", "_") + "_v1" for name in PROTECTED}
REVERIFY = {
    "decision": "reverificar_identidad",
    "mensaje": "Por seguridad, volvé a verificar tu identidad para continuar",
}


def before_tool_callback(tool: BaseTool, args: dict[str, Any], tool_context: ToolContext) -> dict[str, Any] | None:
    """Rechaza productos sin identidad válida; nunca incorpora secretos en args."""
    if tool.name in {"consultar_conocimiento", "escalar_a_humano"}:
        return None
    if tool.name not in ALLOWED:
        return {"error": {"codigo": "CAPACIDAD_NO_DISPONIBLE"}}
    state = tool_context.state
    credential = state.get("credencial")
    if tool.name in SENSITIVE and (
        state.get("vinculo_activo") is not True
        or state.get("credencial_valida") is not True
        or not isinstance(credential, str)
        or not credential.strip()
    ):
        return dict(REVERIFY)
    # El contexto del modelo se ignora incluso si intenta suplantar identidad.
    args.pop("contexto", None)
    # ToolContext es por invocación: no persistir el sobre en state (eventos ADK).
    tool_context.__dict__["_h7_envelope"] = {
        "contexto": {
            "numero_whatsapp": state.get("numero_whatsapp"),
            "correlation_id": state.get("correlation_id"),
            "credencial": credential if isinstance(credential, str) and credential else None,
        },
        "parametros": args.get("parametros", {}),
    }
    return None


def redact(value: Any, credential: str) -> Any:
    """No devuelve credenciales ni siquiera si H7 las repite en su respuesta."""
    if isinstance(value, dict):
        return {k: redact(v, credential) for k, v in value.items() if k != "credencial"}
    if isinstance(value, list):
        return [redact(v, credential) for v in value]
    if isinstance(value, str) and credential:
        return value.replace(credential, "[REDACTADO]")
    return value


class SessionTool(BaseTool):
    """Adapta la declaración MCP y agrega el sobre solo en el transporte."""

    def __init__(self, source: BaseTool, owner: "SessionMCPToolset") -> None:
        super().__init__(name=source.name, description=source.description)
        self.source = source
        self.owner = owner

    def _get_declaration(self) -> FunctionDeclaration | None:
        declaration = self.source._get_declaration()
        if declaration is not None:
            declaration = declaration.model_copy(deep=True)
            if declaration.parameters:
                properties = declaration.parameters.properties
                if properties:
                    properties.pop("contexto", None)
                if declaration.parameters.required:
                    declaration.parameters.required = [x for x in declaration.parameters.required if x != "contexto"]
            if declaration.parameters_json_schema:
                schema = declaration.parameters_json_schema
                schema.get("properties", {}).pop("contexto", None)
                schema["required"] = [x for x in schema.get("required", []) if x != "contexto"]
        return declaration

    async def run_async(self, *, args: dict[str, Any], tool_context: ToolContext) -> Any:
        decision = before_tool_callback(self, args, tool_context)
        if decision is not None:
            return decision
        state = tool_context.state
        credential = state.get("credencial", "")
        if not isinstance(credential, str):
            credential = ""
        envelope = tool_context.__dict__.pop("_h7_envelope")
        try:
            # Evita instrumentación ADK que registra tool_args con el sobre sensible.
            session = await self.owner._mcp_session_manager.create_session()
            result = await session.call_tool(self.name, arguments=envelope)
            payload: Any = result.structuredContent
            if payload is None:
                payload = [json.loads(item.text) for item in result.content if item.type == "text"]
            return redact(payload, credential)
        except Exception:
            # Excepciones del transporte pueden contener el body; nunca se imprimen.
            return {
                "error": {"codigo": "SERVICIO_NO_DISPONIBLE", "mensaje": "No podemos completar la operación ahora."}
            }


class SessionMCPToolset(MCPToolset):
    """MCPToolset con contexto de sesión aislado de las declaraciones del modelo."""

    async def get_tools(self, readonly_context: ReadonlyContext | None = None) -> list[BaseTool]:
        return [SessionTool(tool, self) for tool in await super().get_tools(readonly_context)]


def create_toolset(settings: Settings) -> SessionMCPToolset:
    """Importar H9 no abre conexiones; ADK descubre herramientas al ejecutar."""
    return SessionMCPToolset(
        connection_params=StreamableHTTPConnectionParams(url=settings.mcp_url, timeout=10, sse_read_timeout=10),
        tool_filter=ALLOWED,
    )
