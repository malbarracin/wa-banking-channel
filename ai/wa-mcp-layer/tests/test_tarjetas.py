"""Contratos H5 con HTTP simulado, sin credenciales ni datos reales."""

import json

import httpx
import pytest
import respx
from pydantic import SecretStr

from wa_mcp_layer.capabilities.tarjetas import TarjetasCapabilities
from wa_mcp_layer.clients.cards_client import CardsClient
from wa_mcp_layer.envelope import ContextoSesion, Fallo, Resultado
from wa_mcp_layer.settings import Settings

CARD = "11111111-1111-4111-8111-111111111111"
TOKEN = "aaa.bbb.ccc"  # noqa: S105 -- JWT sintético para transporte simulado.
CASES = [
    ("listar_tarjetas", "GET", "", None),
    ("consultar_consumos", "GET", f"/{CARD}/consumos", {"tarjetaId": CARD, "dias": 30, "limit": 5, "cursor": "a+/="}),
    ("bloquear_temporal", "POST", f"/{CARD}/bloquear/temporal", {"tarjetaId": CARD, "confirmar": "CONFIRMAR"}),
    ("desbloquear_temporal", "POST", f"/{CARD}/desbloquear/temporal", {"tarjetaId": CARD, "validacionAdicional": True}),
    (
        "bloquear_definitivo",
        "POST",
        f"/{CARD}/bloquear/definitivo",
        {
            "tarjetaId": CARD,
            "motivo": "ROBO",
            "confirmarPrimero": "CONFIRMAR",
            "confirmarSegundo": "CONFIRMAR",
        },
    ),
    (
        "registrar_aviso_viaje",
        "POST",
        f"/{CARD}/aviso-viaje",
        {
            "tarjetaId": CARD,
            "pais": "España",
            "fechaDesde": "2026-12-01",
            "fechaHasta": "2026-12-15",
        },
    ),
]


@pytest.mark.parametrize("action,method,path,params", CASES)
@pytest.mark.parametrize("status", [200, 401, 403, 409, 500, 503, None])
@respx.mock
async def test_h5(action, method, path, params, status, capsys):
    context = ContextoSesion(numero_whatsapp="+5491100000000", correlation_id="task-127", credencial=SecretStr(TOKEN))
    route = respx.route(method=method, path=f"/api/v1/tarjetas{path}")
    if status is None:
        route.mock(side_effect=httpx.ReadTimeout("SQL secret"))
    else:
        route.mock(return_value=httpx.Response(status, json={"estado": "ACTIVA"} if status == 200 else {"SQL": TOKEN}))
    async with CardsClient(Settings()) as client:
        capability = getattr(TarjetasCapabilities(client), action)
        result = await (capability(context) if params is None else capability(context, params))
    assert route.call_count == 1
    request = route.calls[0].request
    assert request.headers["X-Credencial-Sesion"] == TOKEN
    if method == "POST":
        assert json.loads(request.content) == params
    elif params:
        assert dict(request.url.params) == {"dias": "30", "limit": "5", "cursor": "a+/="}
        assert request.content == b""
    if status == 200:
        assert isinstance(result, Resultado) and result.resultado == {"estado": "ACTIVA"}
    else:
        assert isinstance(result, Fallo)
        assert "SQL" not in result.model_dump_json() and TOKEN not in result.model_dump_json()
    event = json.loads(capsys.readouterr().out)
    assert event["capacidad"] == result.auditoria.capacidad == f"tarjetas.{action}"
    assert event["servicio_origen"] == "H5"
    assert event["correlation_id"] == result.auditoria.correlation_id == "task-127"
    assert event["resultado"] == ("EXITO" if status == 200 else result.error.codigo)
    assert event["duracion_ms"] >= 0 and result.auditoria.version == 1
    assert TOKEN not in json.dumps(event) and context.numero_whatsapp not in json.dumps(event)


@pytest.mark.parametrize("action,method,path,params", CASES)
@pytest.mark.parametrize("token", [None, "invalid"])
@respx.mock
async def test_missing_credential(action, method, path, params, token, capsys):
    context = ContextoSesion(numero_whatsapp="+5491100000000", correlation_id="missing", credencial=token)
    async with CardsClient(Settings()) as client:
        capability = getattr(TarjetasCapabilities(client), action)
        result = await (capability(context) if params is None else capability(context, params))
    assert isinstance(result, Fallo)
    assert result.error.codigo == ("CREDENCIAL_REQUERIDA" if token is None else "CREDENCIAL_INVALIDA")
    assert not respx.calls
    assert json.loads(capsys.readouterr().out)["resultado"] == result.error.codigo


@pytest.mark.parametrize("params", [{"tarjetaId": "../escape"}, {"tarjetaId": CARD, "url": "https://invalid"}])
@respx.mock
async def test_invalid_params(params):
    context = ContextoSesion(numero_whatsapp="+5491100000000", correlation_id="invalid", credencial=TOKEN)
    async with CardsClient(Settings()) as client:
        result = await TarjetasCapabilities(client).consultar_consumos(context, params)
    assert isinstance(result, Fallo)
    assert not respx.calls


@pytest.mark.parametrize("confirmation", [None, "CANCELAR", "CONFIRMAR"])
@respx.mock
async def test_confirmation_forwarded_without_retry(confirmation):
    context = ContextoSesion(numero_whatsapp="+5491100000000", correlation_id="confirm", credencial=TOKEN)
    route = respx.post(path=f"/api/v1/tarjetas/{CARD}/bloquear/temporal").mock(
        return_value=httpx.Response(409, json={"code": "CONFIRMACION_REQUERIDA"})
    )
    params = {"tarjetaId": CARD}
    if confirmation is not None:
        params["confirmar"] = confirmation
    async with CardsClient(Settings()) as client:
        result = await TarjetasCapabilities(client).bloquear_temporal(context, params)
    assert isinstance(result, Fallo) and route.call_count == 1
    assert json.loads(route.calls[0].request.content) == params
