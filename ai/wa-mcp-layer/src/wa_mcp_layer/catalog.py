"""Catálogo v1: registro versionado y despacho H1/H3.

Cada entrada declara nombre, versión, servicio, requiere_credencial, actor, handler
y flag habilitada. H2/H4/H5/H6 se ejecutan por sus módulos de capacidades; el
catálogo es la fuente de publicación y de rechazo (no publicada / deshabilitada).
El actor lo resuelve el consumidor confiable, nunca parámetros del LLM.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import UTC, datetime
from time import perf_counter
from types import MappingProxyType
from typing import Literal

from pydantic import BaseModel, JsonValue, ValidationError

from wa_mcp_layer.audit.logger import log_invocation
from wa_mcp_layer.capabilities import acceso, cuentas, identidad, seguros, tarjetas, vinculo
from wa_mcp_layer.clients.channel_client import ChannelClient
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.clients.users_client import UsersClient
from wa_mcp_layer.envelope import Auditoria, ContextoSesion, ErrorNegocio, Fallo, Resultado
from wa_mcp_layer.errors import map_error
from wa_mcp_layer.security.session_context import BusinessError

Actor = Literal["agente", "soporte", "banco"]

__all__ = ["Actor", "CATALOG", "Capability", "Catalog", "Fallo", "H1_H3", "resolve"]


@dataclass(frozen=True)
class Capability:
    nombre: str
    actor: Actor
    servicio: str
    handler: str
    parametros: type[BaseModel]
    method: str = "GET"
    suffix: str = ""
    requiere_credencial: bool = False
    version: int = 1
    habilitada: bool = True

    @property
    def tool_name(self) -> str:
        return f"{self.nombre.replace('.', '_')}_v{self.version}"

    @property
    def env_flag(self) -> str:
        return f"MCP_CAPABILITY_{self.nombre.replace('.', '_').upper()}_ENABLED"

    def is_enabled(self) -> bool:
        raw = os.getenv(self.env_flag)
        if raw is None:
            return self.habilitada
        return raw.lower() == "true"


def _cap(
    nombre: str,
    actor: Actor,
    servicio: str,
    handler: str,
    parametros: type[BaseModel],
    *,
    method: str = "GET",
    suffix: str = "",
    requiere_credencial: bool = False,
    habilitada: bool = True,
) -> Capability:
    return Capability(
        nombre=nombre,
        actor=actor,
        servicio=servicio,
        handler=handler,
        parametros=parametros,
        method=method,
        suffix=suffix,
        requiere_credencial=requiere_credencial,
        habilitada=habilitada,
    )


_EMPTY = acceso.Parametros

_ENTRIES = (
    _cap("identidad.consultar_cliente", "soporte", "H1", "consultar_cliente", identidad.ConsultaCliente),
    _cap("identidad.consultar_estado_cliente", "soporte", "H1", "consultar_estado_cliente", identidad.ConsultaCliente),
    _cap("acceso.emitir_credencial", "agente", "H2", "emitir_credencial", acceso.Emision),
    _cap("acceso.validar_credencial", "agente", "H2", "validar_credencial", _EMPTY),
    _cap("acceso.revocar_credencial", "agente", "H2", "revocar_credencial", acceso.Revocacion),
    _cap(
        "acceso.revocar_credenciales_masivo",
        "banco",
        "H2",
        "revocar_credenciales_masivo",
        acceso.RevocacionMasiva,
        habilitada=False,
    ),
    _cap("acceso.consultar_historial_credencial", "soporte", "H2", "consultar_historial_credencial", _EMPTY),
    _cap(
        "vinculo.aceptar_terminos", "agente", "H3", "aceptar_terminos", vinculo.Numero, method="POST", suffix="terminos"
    ),
    _cap(
        "vinculo.verificar_identidad",
        "agente",
        "H3",
        "verificar_identidad",
        vinculo.Verificacion,
        method="POST",
        suffix="verificaciones",
    ),
    _cap("vinculo.consultar_estado", "agente", "H3", "consultar_estado", vinculo.Numero, suffix="estado"),
    _cap(
        "vinculo.consultar_perfil",
        "agente",
        "H3",
        "consultar_perfil",
        vinculo.Numero,
        suffix="perfil",
        requiere_credencial=True,
    ),
    _cap(
        "vinculo.actualizar_preferencias",
        "agente",
        "H3",
        "actualizar_preferencias",
        vinculo.Preferencias,
        method="PATCH",
        suffix="preferencias",
        requiere_credencial=True,
    ),
    _cap(
        "vinculo.bloquear",
        "agente",
        "H3",
        "bloquear",
        vinculo.Bloqueo,
        method="PATCH",
        suffix="bloqueo",
        requiere_credencial=True,
    ),
    _cap(
        "vinculo.desvincular",
        "agente",
        "H3",
        "desvincular",
        vinculo.Confirmacion,
        method="PATCH",
        suffix="desvinculacion",
        requiere_credencial=True,
    ),
    _cap("vinculo.consultar_historial", "soporte", "H3", "consultar_historial", vinculo.Numero, suffix="historial"),
    _cap("cuentas.listar_cuentas", "agente", "H4", "listar_cuentas", _EMPTY, requiere_credencial=True),
    _cap("cuentas.consultar_saldo", "agente", "H4", "consultar_saldo", cuentas.Cuenta, requiere_credencial=True),
    _cap(
        "cuentas.consultar_movimientos",
        "agente",
        "H4",
        "consultar_movimientos",
        cuentas.Movimientos,
        requiere_credencial=True,
    ),
    _cap("cuentas.listar_terceros", "agente", "H4", "listar_terceros", _EMPTY, requiere_credencial=True),
    _cap(
        "cuentas.preparar_transferencia",
        "agente",
        "H4",
        "preparar_transferencia",
        cuentas.Transferencia,
        requiere_credencial=True,
    ),
    _cap(
        "cuentas.confirmar_transferencia",
        "agente",
        "H4",
        "confirmar_transferencia",
        cuentas.Confirmacion,
        requiere_credencial=True,
    ),
    _cap("tarjetas.listar_tarjetas", "agente", "H5", "listar_tarjetas", _EMPTY, requiere_credencial=True),
    _cap(
        "tarjetas.consultar_consumos", "agente", "H5", "consultar_consumos", tarjetas.Consumos, requiere_credencial=True
    ),
    _cap(
        "tarjetas.bloquear_temporal",
        "agente",
        "H5",
        "bloquear_temporal",
        tarjetas.BloqueoTemporal,
        requiere_credencial=True,
    ),
    _cap(
        "tarjetas.desbloquear_temporal",
        "agente",
        "H5",
        "desbloquear_temporal",
        tarjetas.DesbloqueoTemporal,
        requiere_credencial=True,
    ),
    _cap(
        "tarjetas.bloquear_definitivo",
        "agente",
        "H5",
        "bloquear_definitivo",
        tarjetas.BloqueoDefinitivo,
        requiere_credencial=True,
    ),
    _cap(
        "tarjetas.registrar_aviso_viaje",
        "agente",
        "H5",
        "registrar_aviso_viaje",
        tarjetas.AvisoViaje,
        requiere_credencial=True,
    ),
    _cap("seguros.listar_polizas", "agente", "H6", "listar_polizas", _EMPTY, requiere_credencial=True),
    _cap(
        "seguros.consultar_coberturas", "agente", "H6", "consultar_coberturas", seguros.Poliza, requiere_credencial=True
    ),
    _cap(
        "seguros.denunciar_siniestro", "agente", "H6", "denunciar_siniestro", seguros.Denuncia, requiere_credencial=True
    ),
    _cap("seguros.consultar_siniestro", "agente", "H6", "consultar_siniestro", seguros.Caso, requiere_credencial=True),
    _cap(
        "seguros.consultar_documentacion_pendiente",
        "agente",
        "H6",
        "consultar_documentacion_pendiente",
        seguros.Caso,
        requiere_credencial=True,
    ),
)
CATALOG = MappingProxyType({entry.nombre: entry for entry in _ENTRIES})
if len(CATALOG) != 32:  # invariante del contrato publicado
    msg = f"CATALOG debe tener 32 capacidades, tiene {len(CATALOG)}"
    raise RuntimeError(msg)

H1_H3 = MappingProxyType({name: entry for name, entry in CATALOG.items() if entry.servicio in {"H1", "H3"}})


def resolve(nombre: str, *, actor: Actor = "agente") -> Capability | None:
    """Devuelve la capacidad si está publicada, habilitada y el actor coincide."""
    entry = CATALOG.get(nombre)
    if entry is None or actor != entry.actor or not entry.is_enabled():
        return None
    return entry


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
            resolved = resolve(nombre, actor=actor)
            if resolved is None or resolved.servicio not in {"H1", "H3"}:
                raise BusinessError(
                    ErrorNegocio(codigo="CAPACIDAD_NO_DISPONIBLE", mensaje="Esta operación no está disponible.")
                )
            params = resolved.parametros.model_validate(parametros)
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
                    method=resolved.method,
                    suffix=resolved.suffix,
                    authenticated=resolved.requiere_credencial,
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
