"""Capacidades H6 v1, según delivery/openapi de wa-insurance-service.

H6 valida vigencia y titularidad. Cada denuncia es una única petición;
el número de caso y los próximos pasos se reenvían sin interpretar ni reintentar.
"""

from datetime import UTC, date, datetime
from time import perf_counter
from typing import Literal
from uuid import UUID

from pydantic import Field, JsonValue, ValidationError

from wa_mcp_layer.audit.logger import log_invocation
from wa_mcp_layer.capabilities.acceso import Parametros, Rechazo
from wa_mcp_layer.clients.insurance_client import InsuranceClient
from wa_mcp_layer.envelope import Auditoria, ContextoSesion, Resultado
from wa_mcp_layer.errors import map_error
from wa_mcp_layer.security.session_context import BusinessError, require_credential


class Poliza(Parametros):
    poliza_id: UUID


class Caso(Parametros):
    # Un segmento simple evita que una entrada cambie la ruta HTTP fija.
    numero_caso: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$", repr=False)


class Denuncia(Parametros):
    polizaId: UUID
    tipoSiniestro: Literal["HOGAR", "AUTO", "VIDA", "OTROS"]
    fechaSiniestro: date
    descripcion: str = Field(min_length=1, max_length=2000, repr=False)


class SegurosCapabilities:
    def __init__(self, client: InsuranceClient) -> None:
        self._client = client

    async def listar_polizas(self, contexto: ContextoSesion) -> Resultado | Rechazo:
        return await self._invoke("listar_polizas", contexto, {})

    async def consultar_coberturas(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("consultar_coberturas", contexto, parametros)

    async def denunciar_siniestro(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("denunciar_siniestro", contexto, parametros)

    async def consultar_siniestro(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("consultar_siniestro", contexto, parametros)

    async def consultar_documentacion_pendiente(
        self, contexto: ContextoSesion, parametros: JsonValue
    ) -> Resultado | Rechazo:
        return await self._invoke("consultar_documentacion_pendiente", contexto, parametros)

    async def _invoke(self, action: str, context: ContextoSesion, params: JsonValue) -> Resultado | Rechazo:
        start = perf_counter()
        audit = Auditoria(
            capacidad=f"seguros.{action}", version=1, correlation_id=context.correlation_id, timestamp=datetime.now(UTC)
        )
        status = "EXITO"
        try:
            require_credential(context)
            return Resultado(resultado=await self._execute(action, context, params), auditoria=audit)
        except ValidationError:
            error = map_error(400)
            status = error.codigo
            return Rechazo(error=error, auditoria=audit)
        except BusinessError as exc:
            status = exc.error.codigo
            return Rechazo(error=exc.error, auditoria=audit)
        finally:
            log_invocation(
                context=context,
                capacidad=audit.capacidad,
                version=1,
                servicio_origen="H6",
                resultado=status,
                duracion_ms=(perf_counter() - start) * 1000,
            )

    async def _execute(self, action: str, context: ContextoSesion, params: JsonValue) -> JsonValue:
        if action == "listar_polizas":
            return await self._client.request("GET", "/api/v1/seguros", context)
        if action == "consultar_coberturas":
            poliza = Poliza.model_validate(params)
            return await self._client.request("GET", f"/api/v1/seguros/{poliza.poliza_id}/coberturas", context)
        if action == "denunciar_siniestro":
            body = Denuncia.model_validate(params).model_dump(mode="json")
            return await self._client.request("POST", "/api/v1/siniestros", context, body=body)
        caso = Caso.model_validate(params)
        if action == "consultar_siniestro":
            return await self._client.request("GET", f"/api/v1/siniestros/{caso.numero_caso}", context)
        return await self._client.request(
            "GET", f"/api/v1/siniestros/{caso.numero_caso}/documentacion-pendiente", context
        )
