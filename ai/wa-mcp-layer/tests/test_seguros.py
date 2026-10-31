"""H6 sin red: aislamiento, errores, auditoría y passthrough de denuncias."""

import json

import httpx
import pytest
import respx
from pydantic import SecretStr

from wa_mcp_layer.capabilities.acceso import Rechazo
from wa_mcp_layer.capabilities.seguros import SegurosCapabilities
from wa_mcp_layer.clients.insurance_client import InsuranceClient
from wa_mcp_layer.envelope import ContextoSesion, Resultado
from wa_mcp_layer.settings import Settings

TOKEN = "aaa.bbb.ccc"  # noqa: S105 -- Credencial sintética para transporte simulado.
PHONE = "+5491100000000"
POLIZA = "e7a62f43-9b82-4c01-b2ab-7f902eab1234"
CASO = "SIN-2026-000123"
CASES = [
    ("listar_polizas", "GET", "/seguros", None, [{"id": POLIZA, "numeroPoliza": "POL-000123", "ramo": "AUTO"}]),
    (
        "consultar_coberturas",
        "GET",
        f"/seguros/{POLIZA}/coberturas",
        {"poliza_id": POLIZA},
        {"coberturas": ["Responsabilidad civil"], "exclusiones": [], "resumen": True},
    ),
    (
        "denunciar_siniestro",
        "POST",
        "/siniestros",
        {
            "polizaId": POLIZA,
            "tipoSiniestro": "AUTO",
            "fechaSiniestro": "2026-10-20",
            "descripcion": "Choque en interseccion",
        },
        {"numeroCaso": CASO, "proximosPasos": "Envia documentacion", "plazos": "Segun proceso formal"},
    ),
    (
        "consultar_siniestro",
        "GET",
        f"/siniestros/{CASO}",
        {"numero_caso": CASO},
        {"numeroCaso": CASO, "estado": "DOCUMENTACION_PENDIENTE", "pendiente": "Informe"},
    ),
    (
        "consultar_documentacion_pendiente",
        "GET",
        f"/siniestros/{CASO}/documentacion-pendiente",
        {"numero_caso": CASO},
        {"documentos": ["Informe pericial"], "orientacionEnvio": "Canales oficiales"},
    ),
]


def context(token=TOKEN):
    return ContextoSesion(
        numero_whatsapp=PHONE, correlation_id="task-128", credencial=SecretStr(token) if token else None
    )


async def invoke(capabilities, action, params, ctx):
    method = getattr(capabilities, action)
    return await (method(ctx) if params is None else method(ctx, params))


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@pytest.mark.parametrize("status", [200, 401, 403, 400, 404, 409, 500, 503])
@respx.mock
async def test_capabilities(action, method, path, params, response, status, capsys):
    actual_status = 201 if action == "denunciar_siniestro" and status == 200 else status
    route = respx.route(method=method, path=f"/api/v1{path}").respond(
        actual_status, json=response if status == 200 else {"message": f"SQL {TOKEN}"}
    )
    async with InsuranceClient(Settings()) as client:
        result = await invoke(SegurosCapabilities(client), action, params, context())
    assert route.call_count == 1
    request = route.calls[0].request
    assert request.url.host == "localhost" and request.url.port == 8085
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
    if action == "denunciar_siniestro":
        assert json.loads(request.content) == params
    event = json.loads(capsys.readouterr().out)
    assert event["capacidad"] == result.auditoria.capacidad == f"seguros.{action}"
    assert event["correlation_id"] == result.auditoria.correlation_id == "task-128"
    assert event["servicio_origen"] == "H6" and event["duracion_ms"] >= 0
    assert event["resultado"] == ("EXITO" if status == 200 else result.error.codigo)
    assert result.auditoria.version == 1
    assert all(value not in json.dumps(event) for value in [TOKEN, PHONE, "Choque", "SQL"])


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@pytest.mark.parametrize("token,code", [(None, "CREDENCIAL_REQUERIDA"), ("bad", "CREDENCIAL_INVALIDA")])
@respx.mock
async def test_credential_required(action, method, path, params, response, token, code, capsys):
    async with InsuranceClient(Settings()) as client:
        result = await invoke(SegurosCapabilities(client), action, params, context(token))
    assert isinstance(result, Rechazo) and result.error.codigo == code
    assert not respx.calls
    assert json.loads(capsys.readouterr().out)["resultado"] == code


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@pytest.mark.parametrize("status,code", [(401, "CREDENCIAL_INVALIDA"), (403, "VINCULO_INACTIVO")])
@respx.mock
async def test_product_rechecks_credential_after_success(action, method, path, params, response, status, code):
    success_status = 201 if action == "denunciar_siniestro" else 200
    route = respx.route(method=method, path=f"/api/v1{path}").mock(
        side_effect=[httpx.Response(success_status, json=response), httpx.Response(status)]
    )
    async with InsuranceClient(Settings()) as client:
        capabilities = SegurosCapabilities(client)
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
        ("consultar_coberturas", {"poliza_id": "../siniestros"}),
        ("consultar_coberturas", {"poliza_id": POLIZA, "credencial": TOKEN}),
        ("consultar_siniestro", {"numero_caso": "../seguros"}),
        ("consultar_siniestro", {"numero_caso": "SIN 2026"}),
        ("denunciar_siniestro", {"polizaId": POLIZA, "tipoSiniestro": "AUTO"}),
        (
            "denunciar_siniestro",
            {"polizaId": POLIZA, "tipoSiniestro": "MOTO", "fechaSiniestro": "2026-10-20", "descripcion": "x"},
        ),
        ("consultar_documentacion_pendiente", {"numero_caso": ""}),
    ],
)
@respx.mock
async def test_invalid_parameters(action, params):
    async with InsuranceClient(Settings()) as client:
        result = await invoke(SegurosCapabilities(client), action, params, context())
    assert isinstance(result, Rechazo) and result.error.codigo == "SOLICITUD_INVALIDA"
    assert not respx.calls


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@respx.mock
async def test_timeouts_are_not_retried(action, method, path, params, response, capsys):
    route = respx.route(method=method, path=f"/api/v1{path}").mock(side_effect=httpx.ReadTimeout("SQL secret"))
    async with InsuranceClient(Settings()) as client:
        result = await invoke(SegurosCapabilities(client), action, params, context())
    assert isinstance(result, Rechazo) and result.error.codigo == "SERVICIO_NO_DISPONIBLE"
    assert route.call_count == 1
    assert "SQL secret" not in result.model_dump_json() + capsys.readouterr().out
