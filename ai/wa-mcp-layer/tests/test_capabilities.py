"""Cobertura del catálogo H1/H3 con servicios simulados y datos sintéticos."""

import json
from contextlib import AsyncExitStack

import httpx
import pytest
from pydantic import SecretStr

from wa_mcp_layer.catalog import CATALOG, Catalog, Fallo
from wa_mcp_layer.clients.channel_client import ChannelClient
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.clients.users_client import UsersClient
from wa_mcp_layer.envelope import ContextoSesion, Resultado
from wa_mcp_layer.settings import Settings

PHONE = "+5491100000000"
EXPECTED_SUPPORT = {"identidad.consultar_cliente", "identidad.consultar_estado_cliente", "vinculo.consultar_historial"}
EXPECTED_CHANNEL = {
    "vinculo.aceptar_terminos",
    "vinculo.verificar_identidad",
    "vinculo.consultar_estado",
    "vinculo.consultar_perfil",
    "vinculo.actualizar_preferencias",
    "vinculo.bloquear",
    "vinculo.desvincular",
}
PARAMS = {
    "identidad.consultar_cliente": {"tipo": "DNI", "numero": "00000001"},
    "identidad.consultar_estado_cliente": {"tipo": "DNI", "numero": "00000001"},
    "vinculo.verificar_identidad": {
        "documentoTipo": "DNI",
        "documentoNumero": "00000001",
        "datosVerificacion": {"codigo": "000000"},
    },
    "vinculo.actualizar_preferencias": {"idioma": "es", "notificacionesHabilitadas": True},
    "vinculo.bloquear": {"motivo": "SOLICITUD_CLIENTE", "confirmacion": True},
    "vinculo.desvincular": {"confirmacion": True},
}


@pytest.fixture
def ctx():
    return ContextoSesion(numero_whatsapp=PHONE, correlation_id="task-124", credencial=SecretStr("aaa.bbb.ccc"))


@pytest.fixture
async def backend():
    calls = []
    status = [200]

    def handler(request):
        calls.append(request)
        if request.url.path == "/api/v1/credenciales/validar":
            return httpx.Response(
                200,
                json={
                    "vigente": True,
                    "numeroWhatsapp": PHONE,
                    "documentoTipo": "DNI",
                    "documentoNumero": "00000001",
                },
            )
        return httpx.Response(status[0], json={"estado": "ACTIVO"} if status[0] == 200 else {"message": "SQL secret"})

    async with AsyncExitStack() as stack:
        clients = [
            await stack.enter_async_context(kind(Settings(), transport=httpx.MockTransport(handler)))
            for kind in (UsersClient, ChannelClient, CredentialsClient)
        ]
        yield Catalog(*clients), calls, status


@pytest.mark.parametrize("name", CATALOG)
async def test_success_routes_headers_and_audit(name, ctx, backend, capfd):
    catalog, calls, _ = backend
    entry = CATALOG[name]
    result = await catalog.invoke(name, ctx, PARAMS.get(name, {}), actor=entry.actor)
    assert isinstance(result, Resultado)
    assert result.resultado == {"estado": "ACTIVO"}
    request = calls[-1]
    assert request.method == entry.method
    if entry.servicio == "H1":
        assert request.url.path == "/api/v1/users/DNI/00000001" + ("/status" if name.endswith("estado_cliente") else "")
        assert entry.actor == "soporte"
    else:
        expected = "/api/v1/vinculos/" + ("terminos" if entry.suffix == "terminos" else PHONE + "/" + entry.suffix)
        assert request.url.path == expected
        if entry.method != "GET":
            assert json.loads(request.content) == (
                {"numeroWhatsapp": PHONE} if entry.suffix == "terminos" else PARAMS[name]
            )
    if entry.requiere_credencial:
        assert len(calls) == 2
        assert request.headers["X-Canal-Credencial-Valida"] == "true"
        assert request.headers["X-Credencial-Documento-Numero"] == "00000001"
    else:
        assert len(calls) == 1
        assert "X-Canal-Credencial-Valida" not in request.headers
    assert "X-Credencial-Sesion" not in request.headers
    event = json.loads(capfd.readouterr().out)
    assert event["capacidad"] == name and event["resultado"] == "EXITO"
    assert event["correlation_id"] == result.auditoria.correlation_id == "task-124"
    assert PHONE not in json.dumps(event) and "aaa.bbb.ccc" not in json.dumps(event)


@pytest.mark.parametrize("name", CATALOG)
async def test_no_credential_follows_catalog_policy(name, ctx, backend, capfd):
    catalog, calls, _ = backend
    ctx.credencial = None
    entry = CATALOG[name]
    result = await catalog.invoke(name, ctx, PARAMS.get(name, {}), actor=entry.actor)
    if entry.requiere_credencial:
        assert isinstance(result, Fallo) and result.error.codigo == "CREDENCIAL_REQUERIDA"
        assert calls == []
    else:
        assert isinstance(result, Resultado)
        assert len(calls) == 1
    assert json.loads(capfd.readouterr().out)["capacidad"] == name


@pytest.mark.parametrize("name", CATALOG)
async def test_service_errors_are_business_errors_and_audited(name, ctx, backend, capfd):
    catalog, _, status = backend
    status[0] = 500
    result = await catalog.invoke(name, ctx, PARAMS.get(name, {}), actor=CATALOG[name].actor)
    assert isinstance(result, Fallo) and result.error.codigo == "SERVICIO_NO_DISPONIBLE"
    assert "SQL" not in result.model_dump_json()
    assert json.loads(capfd.readouterr().out)["resultado"] == "SERVICIO_NO_DISPONIBLE"


@pytest.mark.parametrize("name", ["inventada.operacion", "identidad.consultar_cliente", "vinculo.consultar_historial"])
async def test_unknown_or_support_operation_denied(name, ctx, backend):
    catalog, calls, _ = backend
    result = await catalog.invoke(name, ctx, {})
    assert isinstance(result, Fallo) and result.error.codigo == "CAPACIDAD_NO_DISPONIBLE"
    assert calls == []


async def test_disabled_capability(ctx, backend, monkeypatch):
    catalog, calls, _ = backend
    monkeypatch.setenv("MCP_CAPABILITY_VINCULO_CONSULTAR_ESTADO_ENABLED", "false")
    result = await catalog.invoke("vinculo.consultar_estado", ctx, {})
    assert isinstance(result, Fallo) and result.error.codigo == "CAPACIDAD_NO_DISPONIBLE"
    assert calls == []


def test_required_h1_h3_operations_and_support_policy():
    # Lista independiente: parametrizar solo sobre CATALOG no detectaría una operación omitida.
    assert EXPECTED_SUPPORT | EXPECTED_CHANNEL <= CATALOG.keys()
    for name in EXPECTED_SUPPORT | EXPECTED_CHANNEL:
        entry = CATALOG[name]
        assert entry.actor == ("soporte" if name in EXPECTED_SUPPORT else "agente")
        assert entry.servicio == ("H1" if name.startswith("identidad.") else "H3")
        assert entry.tool_name == name.replace(".", "_") + "_v1"


@pytest.mark.parametrize("name", sorted(EXPECTED_SUPPORT | EXPECTED_CHANNEL))
async def test_wrong_actor_is_rejected_and_audited_before_transport(name, ctx, backend, capfd):
    catalog, calls, _ = backend
    actor = "agente" if name in EXPECTED_SUPPORT else "soporte"
    result = await catalog.invoke(name, ctx, PARAMS.get(name, {}), actor=actor)
    assert isinstance(result, Fallo)
    assert result.error.codigo == "CAPACIDAD_NO_DISPONIBLE"
    assert calls == []
    event = json.loads(capfd.readouterr().out)
    assert event["capacidad"] == name
    assert event["resultado"] == "CAPACIDAD_NO_DISPONIBLE"


@pytest.mark.parametrize(
    "name,params",
    [
        ("identidad.consultar_cliente", {"tipo": "../", "numero": "00000001"}),
        ("vinculo.consultar_estado", {"numero_whatsapp": "+5491100000001"}),
        ("vinculo.actualizar_preferencias", {"documentoNumero": "00000002"}),
        ("vinculo.bloquear", {"motivo": "POLITICA_BANCO", "confirmacion": True}),
        ("vinculo.desvincular", {"confirmacion": False}),
    ],
)
async def test_invalid_input_cannot_escape_authority(name, params, ctx, backend):
    catalog, calls, _ = backend
    result = await catalog.invoke(name, ctx, params, actor=CATALOG[name].actor)
    assert isinstance(result, Fallo)
    assert calls == []
