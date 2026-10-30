"""Capacidades H5 v1; H5 valida titularidad, vínculo y confirmaciones sin reintentos."""

from datetime import UTC, date, datetime
from time import perf_counter
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, JsonValue, ValidationError

from wa_mcp_layer.audit.logger import log_invocation
from wa_mcp_layer.catalog import Fallo
from wa_mcp_layer.clients.cards_client import CardsClient
from wa_mcp_layer.envelope import Auditoria, ContextoSesion, Resultado
from wa_mcp_layer.errors import map_error
from wa_mcp_layer.security.session_context import BusinessError, require_credential


class Parametros(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)


class Tarjeta(Parametros):
    tarjetaId: UUID


class Consumos(Tarjeta):
    dias: int | None = None
    limit: int | None = None
    cursor: str | None = None


class BloqueoTemporal(Tarjeta):
    confirmar: str | None = None


class DesbloqueoTemporal(Tarjeta):
    validacionAdicional: bool = False


class BloqueoDefinitivo(Tarjeta):
    motivo: str | None = None
    confirmarPrimero: str | None = None
    confirmarSegundo: str | None = None


class AvisoViaje(Tarjeta):
    pais: str = Field(min_length=1, max_length=255)
    region: str | None = Field(default=None, max_length=255)
    fechaDesde: date
    fechaHasta: date


class TarjetasCapabilities:
    def __init__(self, client: CardsClient) -> None:
        self._client = client

    async def listar_tarjetas(self, contexto: ContextoSesion) -> Resultado | Fallo:
        return await self._invoke("listar_tarjetas", contexto, {})

    async def consultar_consumos(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Fallo:
        return await self._invoke("consultar_consumos", contexto, parametros)

    async def bloquear_temporal(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Fallo:
        return await self._invoke("bloquear_temporal", contexto, parametros)

    async def desbloquear_temporal(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Fallo:
        return await self._invoke("desbloquear_temporal", contexto, parametros)

    async def bloquear_definitivo(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Fallo:
        return await self._invoke("bloquear_definitivo", contexto, parametros)

    async def registrar_aviso_viaje(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Fallo:
        return await self._invoke("registrar_aviso_viaje", contexto, parametros)

    async def _invoke(self, action: str, context: ContextoSesion, params: JsonValue) -> Resultado | Fallo:
        start = perf_counter()
        audit = Auditoria(
            capacidad=f"tarjetas.{action}",
            version=1,
            correlation_id=context.correlation_id,
            timestamp=datetime.now(UTC),
        )
        status = "EXITO"
        try:
            require_credential(context)
            data = await self._execute(action, context, params)
            return Resultado(resultado=data, auditoria=audit)
        except (ValidationError, BusinessError) as exc:
            error = exc.error if isinstance(exc, BusinessError) else map_error(400)
            status = error.codigo
            return Fallo(error=error, auditoria=audit)
        finally:
            log_invocation(
                context=context,
                capacidad=audit.capacidad,
                version=1,
                servicio_origen="H5",
                resultado=status,
                duracion_ms=(perf_counter() - start) * 1000,
            )

    async def _execute(self, action: str, context: ContextoSesion, params: JsonValue) -> JsonValue:
        prefix = "/api/v1/tarjetas"
        if action == "listar_tarjetas":
            return await self._client.request("GET", prefix, context)
        models: dict[str, type[Tarjeta]] = {
            "consultar_consumos": Consumos,
            "bloquear_temporal": BloqueoTemporal,
            "desbloquear_temporal": DesbloqueoTemporal,
            "bloquear_definitivo": BloqueoDefinitivo,
            "registrar_aviso_viaje": AvisoViaje,
        }
        parsed = models[action].model_validate(params)
        body = parsed.model_dump(mode="json", exclude_none=True)
        path = f"{prefix}/{parsed.tarjetaId}"
        if isinstance(parsed, Consumos):
            query: dict[str, str | int] = {}
            for key, value in (("dias", parsed.dias), ("limit", parsed.limit), ("cursor", parsed.cursor)):
                if value is not None:
                    query[key] = value
            return await self._client.request("GET", f"{path}/consumos", context, params=query)
        suffixes = {
            "bloquear_temporal": "bloquear/temporal",
            "desbloquear_temporal": "desbloquear/temporal",
            "bloquear_definitivo": "bloquear/definitivo",
            "registrar_aviso_viaje": "aviso-viaje",
        }
        return await self._client.request("POST", f"{path}/{suffixes[action]}", context, body=body)
