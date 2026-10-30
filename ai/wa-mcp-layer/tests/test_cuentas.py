"""H4 sin red: aislamiento, errores, auditor�a y doble confirmaci�n."""

import json

import httpx
import pytest
import respx
from pydantic import SecretStr

from wa_mcp_layer.capabilities.acceso import Rechazo
from wa_mcp_layer.capabilities.cuentas import CuentasCapabilities
from wa_mcp_layer.clients.accounts_client import AccountsClient
from wa_mcp_layer.envelope import ContextoSesion, Resultado
from wa_mcp_layer.settings import Settings

TOKEN = "aaa.bbb.ccc"  # noqa: S105 -- Credencial sint�tica para transporte simulado.
PHONE = "+5491100000000"
ID = "11111111-1111-4111-8111-111111111111"
CASES = [
    ("listar_cuentas", "GET", "/cuentas", None, [{"numeroCuenta": "001"}]),
    ("consultar_saldo", "GET", "/cuentas/001/saldo", {"numero": "001"}, {"saldoDisponible": 100}),
    (
        "consultar_movimientos",
        "GET",
        "/cuentas/001/movimientos",
        {
            "numero": "001",
            "limit": 2,
            "cursor": "a+/=?&",
            "tipo": "DEBITO",
            "desde": "2026-10-01T00:00:00Z",
            "hasta": "2026-10-02T00:00:00Z",
        },
        {"items": [], "nextCursor": "opaque", "hasMore": True},
    ),
    ("listar_terceros", "GET", "/terceros", None, [{"terceroId": ID}]),
    (
        "preparar_transferencia",
        "POST",
        "/transferencias",
        {"cuentaOrigen": "001", "terceroId": ID, "monto": 10.25, "moneda": "ARS", "concepto": "Prueba"},
        {"transferenciaId": ID, "estado": "PREPARADA", "requiereDobleConfirmacion": True},
    ),
    (
        "confirmar_transferencia",
        "POST",
        f"/transferencias/{ID}/confirmar",
        {"transferencia_id": ID, "confirmacion": "CONFIRMAR"},
        {"transferenciaId": ID, "estado": "CONFIRMADA"},
    ),
]


def context(token=TOKEN):
    return ContextoSesion(
        numero_whatsapp=PHONE, correlation_id="task-126", credencial=SecretStr(token) if token else None
    )


async def invoke(capabilities, action, params, ctx):
    method = getattr(capabilities, action)
    return await (method(ctx) if params is None else method(ctx, params))


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@pytest.mark.parametrize("status", [200, 401, 403, 400, 404, 409, 500, 503])
@respx.mock
async def test_capabilities(action, method, path, params, response, status, capsys):
    actual_status = 201 if action == "preparar_transferencia" and status == 200 else status
    route = respx.route(method=method, path=f"/api/v1{path}").respond(
        actual_status, json=response if status == 200 else {"message": f"SQL {TOKEN}"}
    )
    async with AccountsClient(Settings()) as client:
        result = await invoke(CuentasCapabilities(client), action, params, context())
    assert route.call_count == 1
    request = route.calls[0].request
    assert request.url.host == "localhost" and request.url.port == 8084
    assert request.headers["X-Credencial-Sesion"] == TOKEN
    if status == 200:
        assert isinstance(result, Resultado) and result.resultado == response
    else:
        assert isinstance(result, Rechazo)
        expected_codes = {
            400: "SOLICITUD_INVALIDA",
            401: "CREDENCIAL_INVALIDA",
            403: "VINCULO_INACTIVO",
            404: "NO_ENCONTRADO",
            409: "CONFLICTO",
            500: "SERVICIO_NO_DISPONIBLE",
            503: "SERVICIO_NO_DISPONIBLE",
        }
        assert result.error.codigo == expected_codes[status]
        assert result.error.reintentable == (status in {500, 503})
        assert "SQL" not in result.model_dump_json() and TOKEN not in result.model_dump_json()
    if action == "preparar_transferencia":
        assert json.loads(request.content) == params
    if action == "confirmar_transferencia":
        assert json.loads(request.content) == {"confirmacion": "CONFIRMAR"}
    if action == "consultar_movimientos":
        assert dict(request.url.params) == {key: str(value) for key, value in params.items() if key != "numero"}
    event = json.loads(capsys.readouterr().out)
    assert event["capacidad"] == result.auditoria.capacidad == f"cuentas.{action}"
    assert event["correlation_id"] == result.auditoria.correlation_id == "task-126"
    assert event["servicio_origen"] == "H4" and event["duracion_ms"] >= 0
    assert event["resultado"] == ("EXITO" if status == 200 else result.error.codigo)
    assert result.auditoria.version == 1
    assert all(value not in json.dumps(event) for value in [TOKEN, PHONE, "Prueba", "SQL"])


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@pytest.mark.parametrize("token,code", [(None, "CREDENCIAL_REQUERIDA"), ("bad", "CREDENCIAL_INVALIDA")])
@respx.mock
async def test_credential_required(action, method, path, params, response, token, code, capsys):
    async with AccountsClient(Settings()) as client:
        result = await invoke(CuentasCapabilities(client), action, params, context(token))
    assert isinstance(result, Rechazo) and result.error.codigo == code
    assert not respx.calls
    assert json.loads(capsys.readouterr().out)["resultado"] == code


@respx.mock
async def test_double_confirmation_passes_unchanged():
    first = {"transferenciaId": ID, "estado": "CONFIRMADA"}
    second = {"transferenciaId": ID, "estado": "EJECUTADA", "comprobante": {"referencia": "synthetic", "monto": 10.25}}
    route = respx.post(f"http://localhost:8084/api/v1/transferencias/{ID}/confirmar").mock(
        side_effect=[httpx.Response(200, json=first), httpx.Response(200, json=second), httpx.Response(409)]
    )
    async with AccountsClient(Settings()) as client:
        capabilities = CuentasCapabilities(client)
        params = {"transferencia_id": ID, "confirmacion": "CONFIRMAR"}
        result = await capabilities.confirmar_transferencia(context(), params)
        assert result.resultado == first and route.call_count == 1
        result = await capabilities.confirmar_transferencia(context(), params)
        assert result.resultado == second and route.call_count == 2
        result = await capabilities.confirmar_transferencia(context(), {**params, "confirmacion": "CANCELAR"})
        assert isinstance(result, Rechazo) and result.error.codigo == "CONFLICTO"
        assert json.loads(route.calls[2].request.content) == {"confirmacion": "CANCELAR"}


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@pytest.mark.parametrize("status,code", [(401, "CREDENCIAL_INVALIDA"), (403, "VINCULO_INACTIVO")])
@respx.mock
async def test_product_rechecks_credential_after_success(action, method, path, params, response, status, code):
    route = respx.route(method=method, path=f"/api/v1{path}").mock(
        side_effect=[httpx.Response(200, json=response), httpx.Response(status)]
    )
    async with AccountsClient(Settings()) as client:
        capabilities = CuentasCapabilities(client)
        ctx = context()
        first = await invoke(capabilities, action, params, ctx)
        assert isinstance(first, Resultado) and first.resultado == response
        second = await invoke(capabilities, action, params, ctx)
    assert isinstance(second, Rechazo) and second.error.codigo == code
    assert route.call_count == 2
    assert all(call.request.headers["X-Credencial-Sesion"] == TOKEN for call in route.calls)


@pytest.mark.parametrize(
    "action,params",
    [
        ("consultar_saldo", {"numero": "../terceros"}),
        ("consultar_saldo", {"numero": "001", "credencial": TOKEN}),
        ("consultar_movimientos", {"numero": "001", "limit": 0}),
        ("confirmar_transferencia", {"transferencia_id": "../cuentas", "confirmacion": "CONFIRMAR"}),
        ("preparar_transferencia", {"cuentaOrigen": "001"}),
    ],
)
@respx.mock
async def test_invalid_parameters(action, params):
    async with AccountsClient(Settings()) as client:
        result = await invoke(CuentasCapabilities(client), action, params, context())
    assert isinstance(result, Rechazo) and result.error.codigo == "SOLICITUD_INVALIDA"
    assert not respx.calls


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@respx.mock
async def test_timeouts_are_not_retried(action, method, path, params, response, capsys):
    route = respx.route(method=method, path=f"/api/v1{path}").mock(side_effect=httpx.ReadTimeout("SQL secret"))
    async with AccountsClient(Settings()) as client:
        result = await invoke(CuentasCapabilities(client), action, params, context())
    assert isinstance(result, Rechazo) and result.error.codigo == "SERVICIO_NO_DISPONIBLE"
    assert route.call_count == 1
    assert "SQL secret" not in result.model_dump_json() + capsys.readouterr().out
