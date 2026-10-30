"""Catálogo v1 y despachador común. Actor provisto por el consumidor confiable, nunca por parámetros del LLM."""

import os
from dataclasses import dataclass
from datetime import UTC, datetime
from time import perf_counter
from types import MappingProxyType
from typing import Literal

from pydantic import BaseModel, JsonValue, ValidationError

from wa_mcp_layer.audit.logger import log_invocation
from wa_mcp_layer.capabilities import identidad, vinculo
from wa_mcp_layer.clients.channel_client import ChannelClient
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.clients.users_client import UsersClient
from wa_mcp_layer.envelope import Auditoria, ContextoSesion, ErrorNegocio, Resultado
from wa_mcp_layer.errors import map_error
from wa_mcp_layer.security.session_context import BusinessError

Actor = Literal["agente", "soporte"]


@dataclass(frozen=True)
class Capability:
    nombre: str
    actor: Actor
    servicio: str
    parametros: type[BaseModel]
    method: str = "GET"
    suffix: str = ""
    requiere_credencial: bool = False
    version: int = 1
    habilitada: bool = True

    @property
    def tool_name(self) -> str:
        return f"{self.nombre.replace('.', '_')}_v{self.version}"


_ENTRIES = (
    Capability("identidad.consultar_cliente", "soporte", "H1", identidad.ConsultaCliente),
    Capability("identidad.consultar_estado_cliente", "soporte", "H1", identidad.ConsultaCliente),
    Capability("vinculo.aceptar_terminos", "agente", "H3", vinculo.Numero, "POST", "terminos"),
    Capability("vinculo.verificar_identidad", "agente", "H3", vinculo.Verificacion, "POST", "verificaciones"),
    Capability("vinculo.consultar_estado", "agente", "H3", vinculo.Numero, suffix="estado"),
    Capability("vinculo.consultar_perfil", "agente", "H3", vinculo.Numero, suffix="perfil", requiere_credencial=True),
    Capability("vinculo.actualizar_preferencias", "agente", "H3", vinculo.Preferencias, "PATCH", "preferencias", True),
    Capability("vinculo.bloquear", "agente", "H3", vinculo.Bloqueo, "PATCH", "bloqueo", True),
    Capability("vinculo.desvincular", "agente", "H3", vinculo.Confirmacion, "PATCH", "desvinculacion", True),
    Capability("vinculo.consultar_historial", "soporte", "H3", vinculo.Numero, suffix="historial"),
)
CATALOG = MappingProxyType({entry.nombre: entry for entry in _ENTRIES})


class Fallo(BaseModel):
    error: ErrorNegocio
    auditoria: Auditoria


class Catalog:
    def __init__(self, users: UsersClient, channel: ChannelClient, credentials: CredentialsClient) -> None:
        self.users = users
        self.channel = channel
        self.credentials = credentials

    async def invoke(
        self, nombre: str, context: ContextoSesion, parametros: JsonValue, *, actor: Actor = "agente"
    ) -> Resultado | Fallo:
        start = perf_counter()
        entry = CATALOG.get(nombre)
        # No se registran nombres arbitrarios recibidos como entrada en el log.
        audit = Auditoria(
            capacidad=entry.nombre if entry else "catalogo.desconocida",
            version=entry.version if entry else 1,
            correlation_id=context.correlation_id,
            timestamp=datetime.now(UTC),
        )
        status = "EXITO"
        try:
            if (
                entry is None
                or actor != entry.actor
                or not entry.habilitada
                or os.getenv(f"MCP_CAPABILITY_{entry.nombre.replace('.', '_').upper()}_ENABLED", "true").lower()
                != "true"
            ):
                raise BusinessError(
                    ErrorNegocio(codigo="CAPACIDAD_NO_DISPONIBLE", mensaje="Esta operación no está disponible.")
                )
            params = entry.parametros.model_validate(parametros)
            if isinstance(params, identidad.ConsultaCliente):
                function = (
                    identidad.consultar_cliente
                    if nombre == "identidad.consultar_cliente"
                    else identidad.consultar_estado_cliente
                )
                value = await function(self.users, context, params)
            elif isinstance(params, vinculo.Numero):
                value = await vinculo.ejecutar(
                    self.channel,
                    self.credentials,
                    context,
                    params,
                    method=entry.method,
                    suffix=entry.suffix,
                    authenticated=entry.requiere_credencial,
                    soporte=actor == "soporte",
                )
            else:
                raise BusinessError(map_error(400))
            return Resultado(resultado=value, auditoria=audit)
        except (BusinessError, ValidationError) as exc:
            error = exc.error if isinstance(exc, BusinessError) else map_error(400)
            status = error.codigo
            return Fallo(error=error, auditoria=audit)
        finally:
            log_invocation(
                context=context,
                capacidad=audit.capacidad,
                version=audit.version,
                servicio_origen=entry.servicio if entry else "H7",
                resultado=status,
                duracion_ms=(perf_counter() - start) * 1000,
            )
