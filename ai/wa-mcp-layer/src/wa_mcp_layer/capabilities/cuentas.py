"""Capacidades H4 v1, seg�n delivery/openapi/openapi.json de wa-accounts-service.

H4 valida vigencia y titularidad. Cada confirmaci�n es una �nica petici�n;
sus estados y comprobantes se devuelven sin interpretar ni ejecutar otro paso.
"""

from datetime import UTC, datetime
from time import perf_counter
from typing import Literal
from uuid import UUID

from pydantic import Field, JsonValue, ValidationError

from wa_mcp_layer.audit.logger import log_invocation
from wa_mcp_layer.capabilities.acceso import Parametros, Rechazo
from wa_mcp_layer.clients.accounts_client import AccountsClient
from wa_mcp_layer.envelope import Auditoria, ContextoSesion, Resultado
from wa_mcp_layer.errors import map_error
from wa_mcp_layer.security.session_context import BusinessError, require_credential


class Cuenta(Parametros):
    # Un segmento simple evita que una entrada cambie la ruta HTTP fija.
    numero: str = Field(min_length=1, max_length=34, pattern=r"^[A-Za-z0-9_-]+$", repr=False)


class Movimientos(Cuenta):
    desde: datetime | None = None
    hasta: datetime | None = None
    tipo: Literal["DEBITO", "CREDITO"] | None = None
    limit: int | None = Field(default=None, gt=0)
    cursor: str | None = Field(default=None, repr=False)


class Transferencia(Parametros):
    cuentaOrigen: str = Field(min_length=1, max_length=34, repr=False)
    cuentaDestino: str | None = Field(default=None, max_length=34, repr=False)
    terceroId: UUID | None = None
    monto: float = Field(ge=0.01, allow_inf_nan=False)
    moneda: Literal["ARS", "USD"]
    concepto: str | None = Field(default=None, max_length=255, repr=False)


class Confirmacion(Parametros):
    transferencia_id: UUID
    # Otros valores deben llegar a H4: reinician all� la doble confirmaci�n.
    confirmacion: str


class CuentasCapabilities:
    def __init__(self, client: AccountsClient) -> None:
        self._client = client

    async def listar_cuentas(self, contexto: ContextoSesion) -> Resultado | Rechazo:
        return await self._invoke("listar_cuentas", contexto, {})

    async def consultar_saldo(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("consultar_saldo", contexto, parametros)

    async def consultar_movimientos(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("consultar_movimientos", contexto, parametros)

    async def listar_terceros(self, contexto: ContextoSesion) -> Resultado | Rechazo:
        return await self._invoke("listar_terceros", contexto, {})

    async def preparar_transferencia(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("preparar_transferencia", contexto, parametros)

    async def confirmar_transferencia(self, contexto: ContextoSesion, parametros: JsonValue) -> Resultado | Rechazo:
        return await self._invoke("confirmar_transferencia", contexto, parametros)

    async def _invoke(self, action: str, context: ContextoSesion, params: JsonValue) -> Resultado | Rechazo:
        start = perf_counter()
        audit = Auditoria(
            capacidad=f"cuentas.{action}", version=1, correlation_id=context.correlation_id, timestamp=datetime.now(UTC)
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
                servicio_origen="H4",
                resultado=status,
                duracion_ms=(perf_counter() - start) * 1000,
            )

    async def _execute(self, action: str, context: ContextoSesion, params: JsonValue) -> JsonValue:
        if action in {"listar_cuentas", "listar_terceros"}:
            path = "/api/v1/cuentas" if action == "listar_cuentas" else "/api/v1/terceros"
            return await self._client.request("GET", path, context)
        if action == "consultar_saldo":
            cuenta = Cuenta.model_validate(params)
            return await self._client.request("GET", f"/api/v1/cuentas/{cuenta.numero}/saldo", context)
        if action == "consultar_movimientos":
            movimientos = Movimientos.model_validate(params)
            query = movimientos.model_dump(mode="json", exclude={"numero"}, exclude_none=True)
            return await self._client.request(
                "GET",
                f"/api/v1/cuentas/{movimientos.numero}/movimientos",
                context,
                params={key: str(value) for key, value in query.items()},
            )
        if action == "preparar_transferencia":
            body = Transferencia.model_validate(params).model_dump(mode="json", exclude_none=True)
            return await self._client.request("POST", "/api/v1/transferencias", context, body=body)
        confirmation = Confirmacion.model_validate(params)
        return await self._client.request(
            "POST",
            f"/api/v1/transferencias/{confirmation.transferencia_id}/confirmar",
            context,
            body={"confirmacion": confirmation.confirmacion},
        )
