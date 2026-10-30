"""H2 con transporte simulado: nunca llama servicios ni modelos reales."""

import json

import httpx
import pytest
import respx
from pydantic import SecretStr

from wa_mcp_layer.capabilities.acceso import AccesoCapabilities, Rechazo
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.envelope import ContextoSesion, Resultado
from wa_mcp_layer.settings import Settings

PHONE = "+5491100000000"
TOKEN = "aaa.bbb.ccc"  # noqa: S105 -- JWT sintético sin firma ni validez, solo para transporte simulado.
EMISSION = {"documentoTipo": "DNI", "documentoNumero": "00000000", "vinculoActivo": True, "identidadVerificada": True}
IDENTITY = {"vigente": True, "numeroWhatsapp": PHONE, "documentoTipo": "DNI", "documentoNumero": "00000000"}
CASES = [
    (
        "emitir_credencial",
        "POST",
        "/emitir",
        EMISSION,
        {"credencial": TOKEN, "fechaExpiracion": "2026-11-01T00:00:00Z"},
    ),
    ("validar_credencial", "POST", "/validar", None, IDENTITY),
    ("revocar_credencial", "POST", f"/{PHONE}/revocacion", {"motivo": "BLOQUEO"}, None),
    ("revocar_credenciales_masivo", "POST", "/revocacion-masiva", {"criterio": "TODAS"}, {"revocadas": 2}),
    ("consultar_historial_credencial", "GET", f"/{PHONE}/historial", None, {"estadoActual": "ACTIVA", "eventos": []}),
]


@pytest.mark.parametrize("action,method,path,params,response", CASES)
@pytest.mark.parametrize("status", [200, 400, 401, 403, 404, 409, 500])
@respx.mock
async def test_capabilities(action, method, path, params, response, status, capsys):
    settings = Settings(mcp_capability_acceso_revocar_credenciales_masivo_enabled=True)
    context = ContextoSesion(numero_whatsapp=PHONE, correlation_id="task-125", credencial=SecretStr(TOKEN))
    actual_status = 204 if action == "revocar_credencial" and status == 200 else status
    payload = response if status == 200 else {"message": f"SQL secret {TOKEN}"}
    route = respx.route(method=method, url=f"http://localhost:8082/api/v1/credenciales{path}").mock(
        return_value=httpx.Response(actual_status, json=payload) if actual_status != 204 else httpx.Response(204)
    )
    async with CredentialsClient(settings) as client:
        capability = getattr(AccesoCapabilities(client, settings), action)
        result = await (capability(context) if params is None else capability(context, params))
    assert route.call_count == 1
    if status == 200:
        assert isinstance(result, Resultado)
        assert result.resultado == response
    else:
        assert isinstance(result, Rechazo)
        assert TOKEN not in result.model_dump_json()
        assert "SQL" not in result.model_dump_json()
    if action == "emitir_credencial":
        assert json.loads(route.calls[0].request.content) == {**EMISSION, "numeroWhatsapp": PHONE}
    if action == "validar_credencial":
        assert json.loads(route.calls[0].request.content) == {"credencial": TOKEN}
    if action == "revocar_credencial":
        assert json.loads(route.calls[0].request.content) == {"motivo": "BLOQUEO", "solicitadoPor": "CANAL"}
    if action == "revocar_credenciales_masivo":
        assert json.loads(route.calls[0].request.content) == {"criterio": "TODAS"}
    if action == "consultar_historial_credencial":
        assert route.calls[0].request.content == b""
    event = json.loads(capsys.readouterr().out)
    assert event["capacidad"] == f"acceso.{action}"
    assert event["correlation_id"] == result.auditoria.correlation_id == "task-125"
    assert event["resultado"] == ("EXITO" if status == 200 else result.error.codigo)
    assert event["duracion_ms"] >= 0
    assert event["servicio_origen"] == "H2"
    assert result.auditoria.version == 1
    assert TOKEN not in json.dumps(event) and PHONE not in json.dumps(event)


@pytest.mark.parametrize("token,code", [(None, "CREDENCIAL_REQUERIDA"), ("bad", "CREDENCIAL_INVALIDA")])
@respx.mock
async def test_validation_without_credential(token, code, capsys):
    context = ContextoSesion(
        numero_whatsapp=PHONE, correlation_id="missing", credencial=SecretStr(token) if token else None
    )
    async with CredentialsClient(Settings()) as client:
        result = await AccesoCapabilities(client, Settings()).validar_credencial(context)
    assert isinstance(result, Rechazo) and result.error.codigo == code
    assert len(respx.calls) == 0
    assert json.loads(capsys.readouterr().out)["resultado"] == code


@respx.mock
async def test_disabled_mass_revocation(capsys):
    context = ContextoSesion(numero_whatsapp=PHONE, correlation_id="disabled")
    async with CredentialsClient(Settings()) as client:
        result = await AccesoCapabilities(client, Settings()).revocar_credenciales_masivo(
            context, {"criterio": "TODAS"}
        )
    assert isinstance(result, Rechazo) and result.error.codigo == "CAPACIDAD_NO_DISPONIBLE"
    assert len(respx.calls) == 0
    assert json.loads(capsys.readouterr().out)["resultado"] == "CAPACIDAD_NO_DISPONIBLE"


@pytest.mark.parametrize(
    "action,params",
    [
        ("emitir_credencial", {**EMISSION, "identidadVerificada": False}),
        ("emitir_credencial", {**EMISSION, "numeroWhatsapp": "+5491199999999"}),
        ("revocar_credencial", {"motivo": "inventado"}),
        (
            "revocar_credenciales_masivo",
            {"criterio": "TODAS", "segmento": {"documentoTipo": "DNI", "documentoNumero": "0"}},
        ),
        ("revocar_credenciales_masivo", {"criterio": "POR_CLIENTE"}),
    ],
)
@respx.mock
async def test_invalid_parameters(action, params):
    settings = Settings(mcp_capability_acceso_revocar_credenciales_masivo_enabled=True)
    async with CredentialsClient(settings) as client:
        result = await getattr(AccesoCapabilities(client, settings), action)(
            ContextoSesion(numero_whatsapp=PHONE, correlation_id="invalid"), params
        )
    assert isinstance(result, Rechazo) and result.error.codigo == "SOLICITUD_INVALIDA"


@pytest.mark.parametrize(
    "action,method,path,params,response", [case for case in CASES if case[0] != "validar_credencial"]
)
@respx.mock
async def test_initial_access_without_credential(action, method, path, params, response):
    settings = Settings(mcp_capability_acceso_revocar_credenciales_masivo_enabled=True)
    route = respx.route(method=method, url=f"http://localhost:8082/api/v1/credenciales{path}").respond(
        204 if action == "revocar_credencial" else 200, json=response
    )
    async with CredentialsClient(settings) as client:
        capability = getattr(AccesoCapabilities(client, settings), action)
        context = ContextoSesion(numero_whatsapp=PHONE, correlation_id="initial")
        result = await (capability(context) if params is None else capability(context, params))
    assert isinstance(result, Resultado) and route.call_count == 1


@respx.mock
async def test_timeout_is_audited(capsys):
    respx.post("http://localhost:8082/api/v1/credenciales/emitir").mock(side_effect=httpx.ReadTimeout("secret"))
    async with CredentialsClient(Settings()) as client:
        result = await AccesoCapabilities(client, Settings()).emitir_credencial(
            ContextoSesion(numero_whatsapp=PHONE, correlation_id="timeout"), EMISSION
        )
    assert isinstance(result, Rechazo) and result.error.codigo == "SERVICIO_NO_DISPONIBLE"
    assert "secret" not in result.model_dump_json() + capsys.readouterr().out


@pytest.mark.parametrize(
    "identity", [{}, {**IDENTITY, "vigente": False}, {**IDENTITY, "numeroWhatsapp": "+5491199999999"}]
)
@respx.mock
async def test_invalid_identity(identity):
    respx.post("http://localhost:8082/api/v1/credenciales/validar").respond(200, json=identity)
    async with CredentialsClient(Settings()) as client:
        result = await AccesoCapabilities(client, Settings()).validar_credencial(
            ContextoSesion(numero_whatsapp=PHONE, correlation_id="identity", credencial=SecretStr(TOKEN))
        )
    assert isinstance(result, Rechazo) and result.error.codigo == "CREDENCIAL_INVALIDA"


@respx.mock
async def test_mass_revocation_by_customer():
    params = {"criterio": "POR_CLIENTE", "segmento": {"documentoTipo": "DNI", "documentoNumero": "00000000"}}
    route = respx.post("http://localhost:8082/api/v1/credenciales/revocacion-masiva").respond(
        200, json={"revocadas": 1}
    )
    settings = Settings(mcp_capability_acceso_revocar_credenciales_masivo_enabled=True)
    async with CredentialsClient(settings) as client:
        result = await AccesoCapabilities(client, settings).revocar_credenciales_masivo(
            ContextoSesion(numero_whatsapp=PHONE, correlation_id="mass"), params
        )
    assert isinstance(result, Resultado)
    assert json.loads(route.calls[0].request.content) == params
