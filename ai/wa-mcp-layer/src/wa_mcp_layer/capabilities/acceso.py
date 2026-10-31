"""Capacidades H2 v1; invocación desde contexto técnico, nunca desde texto libre.

Emisión recibe confirmaciones resueltas por H3 por el ejecutor de H9.
Historial y revocación masiva son exclusivos del consumidor de soporte/banco;
el registro MCP debe excluirlos del filtro conversacional de H9.
"""

from datetime import UTC, datetime
from time import perf_counter
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, JsonValue, ValidationError, model_validator

from wa_mcp_layer.audit.logger import log_invocation
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.envelope import Auditoria, ContextoSesion, ErrorNegocio, Resultado
from wa_mcp_layer.errors import map_error
from wa_mcp_layer.security.session_context import BusinessError
from wa_mcp_layer.settings import Settings


class Parametros(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)


class Documento(Parametros):
    documentoTipo: str = Field(min_length=1, max_length=20, repr=False)
    documentoNumero: str = Field(min_length=1, max_length=30, repr=False)


class Emision(Documento):
    vinculoActivo: Literal[True]
    identidadVerificada: Literal[True]


class Revocacion(Parametros):
    motivo: Literal["BLOQUEO", "DESVINCULACION", "FRAUDE", "POLITICA_BANCO", "REVOCACION_MASIVA", "REEMPLAZO"]
    solicitadoPor: Literal["CANAL", "BANCO", "SISTEMA"] = "CANAL"


class RevocacionMasiva(Parametros):
    criterio: Literal["TODAS", "POR_CLIENTE"]
    segmento: Documento | None = None

    @model_validator(mode="after")
    def validar_segmento(self) -> "RevocacionMasiva":
        if (self.criterio == "POR_CLIENTE") != (self.segmento is not None):
            raise ValueError("Segmento incompatible con el criterio")
        return self


class Rechazo(BaseModel):
    error: ErrorNegocio
    auditoria: Auditoria


class AccesoCapabilities:
    """Usa un cliente compartido cuyo ciclo de vida administra el servidor."""

    def __init__(self, client: CredentialsClient, settings: Settings) -> None:
        self._client = client
        self._masivo_enabled = settings.mcp_capability_acceso_revocar_credenciales_masivo_enabled

    async def emitir_credencial(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("emitir_credencial", contexto, parametros)

    async def validar_credencial(self, contexto: ContextoSesion) -> Resultado | Rechazo:
        return await self._invoke("validar_credencial", contexto, None)

    async def revocar_credencial(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("revocar_credencial", contexto, parametros)

    async def revocar_credenciales_masivo(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("revocar_credenciales_masivo", contexto, parametros)

    async def consultar_historial_credencial(self, contexto: ContextoSesion) -> Resultado | Rechazo:
        return await self._invoke("consultar_historial_credencial", contexto, None)

    async def _invoke(self, action: str, context: ContextoSesion, params: JsonValue) -> Resultado | Rechazo:
        start = perf_counter()
        audit = Auditoria(
            capacidad=f"acceso.{action}", version=1, correlation_id=context.correlation_id, timestamp=datetime.now(UTC)
        )
        status = "EXITO"
        try:
            data = await self._execute(action, context, params)
            return Resultado(resultado=data, auditoria=audit)
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
                servicio_origen="H2",
                resultado=status,
                duracion_ms=(perf_counter() - start) * 1000,
            )

    async def _execute(self, action: str, context: ContextoSesion, params: JsonValue) -> JsonValue:
        prefix = "/api/v1/credenciales"
        if action == "validar_credencial":
            identity = await self._client.validate(context)
            return identity.model_dump(mode="json")
        if action == "emitir_credencial":
            body = Emision.model_validate(params).model_dump(mode="json")
            body["numeroWhatsapp"] = context.numero_whatsapp
            return await self._client.request("POST", f"{prefix}/emitir", context, body=body)
        if action == "revocar_credencial":
            body = Revocacion.model_validate(params).model_dump(mode="json")
            await self._client.request("POST", f"{prefix}/{context.numero_whatsapp}/revocacion", context, body=body)
            return None
        if action == "revocar_credenciales_masivo":
            if not self._masivo_enabled:
                raise BusinessError(
                    ErrorNegocio(
                        codigo="CAPACIDAD_NO_DISPONIBLE", mensaje="Esta operación no está disponible en este canal."
                    )
                )
            body = RevocacionMasiva.model_validate(params).model_dump(mode="json", exclude_none=True)
            return await self._client.request("POST", f"{prefix}/revocacion-masiva", context, body=body)
        return await self._client.request("GET", f"{prefix}/{context.numero_whatsapp}/historial", context)
