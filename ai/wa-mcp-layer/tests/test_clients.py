"""Contratos de transporte y rechazo sin red ni secretos reales."""

import httpx
import pytest
from pydantic import SecretStr

from wa_mcp_layer.clients.accounts_client import AccountsClient
from wa_mcp_layer.clients.base import map_error
from wa_mcp_layer.clients.cards_client import CardsClient
from wa_mcp_layer.clients.channel_client import ChannelClient
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.clients.insurance_client import InsuranceClient
from wa_mcp_layer.clients.users_client import UsersClient
from wa_mcp_layer.envelope import Auditoria, ContextoSesion, ErrorNegocio, Resultado
from wa_mcp_layer.security.pii import mask_document, mask_name, mask_phone
from wa_mcp_layer.security.session_context import BusinessError, require_credential
from wa_mcp_layer.settings import Settings

SYNTHETIC_CREDENTIAL = "aaa.bbb.ccc"


def context(token: str | None = SYNTHETIC_CREDENTIAL) -> ContextoSesion:
    return ContextoSesion(
        numero_whatsapp="+5491100000000",
        credencial=SecretStr(token) if token is not None else None,
        correlation_id="test-122",
    )


def test_models_and_masks() -> None:
    ctx = context()
    assert "aaa" not in repr(ctx) + ctx.model_dump_json()
    assert "credencial" not in ctx.model_dump()
    assert mask_document("00001234") == mask_phone("+54900001234") == "***1234"
    assert mask_document("1234") == mask_name("Persona Sintética") == "***"
    audit = Auditoria(capacidad="prueba", version=1, correlation_id="test", timestamp="2026-10-30T12:00:00Z")
    assert Resultado(resultado={"ok": True}, auditoria=audit).resultado == {"ok": True}
    assert ErrorNegocio(codigo="TEST", mensaje="Prueba").reintentable is False


@pytest.mark.parametrize("token", [None, "", "bad", "a.b.c\r\nHeader: x", "a" * 8193])
def test_invalid_credential(token: str | None) -> None:
    with pytest.raises(BusinessError):
        require_credential(context(token))


@pytest.mark.parametrize("client_type", [AccountsClient, CardsClient, InsuranceClient])
async def test_product_headers_and_timeout(client_type: type[AccountsClient]) -> None:
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        assert request.headers["X-Credencial-Sesion"] == "aaa.bbb.ccc"
        assert "X-Canal-Credencial-Valida" not in request.headers
        assert request.extensions["timeout"]["connect"] == 2
        assert request.extensions["timeout"]["read"] == 5
        return httpx.Response(200, json={"ok": True})

    async with client_type(Settings(), transport=httpx.MockTransport(handler)) as client:
        assert await client.request("GET", "/api/v1/productos", context()) == {"ok": True}
        with pytest.raises(BusinessError):
            await client.request("GET", "/api/v1/productos", context(None))
    assert len(calls) == 1


@pytest.mark.parametrize(
    "vigente,numero", [(True, "+5491100000000"), (False, "+5491100000000"), (True, "+5491100000001")]
)
async def test_channel_headers_from_h2(vigente: bool, numero: str) -> None:
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        if request.url.path.endswith("validar"):
            assert b'"credencial":"aaa.bbb.ccc"' in request.content
            return httpx.Response(
                200,
                json={
                    "vigente": vigente,
                    "numeroWhatsapp": numero,
                    "documentoTipo": "DNI",
                    "documentoNumero": "00001234",
                },
            )
        assert request.headers["X-Canal-Credencial-Valida"] == "true"
        assert request.headers["X-Credencial-Documento-Tipo"] == "DNI"
        assert request.headers["X-Credencial-Documento-Numero"] == "00001234"
        assert "X-Credencial-Sesion" not in request.headers
        return httpx.Response(204)

    transport = httpx.MockTransport(handler)
    async with CredentialsClient(Settings(), transport=transport) as credentials:
        async with ChannelClient(Settings(), transport=transport) as channel:
            if vigente and numero == context().numero_whatsapp:
                assert await channel.authenticated_request("GET", "/api/v1/perfil", context(), credentials) is None
                assert len(calls) == 2
            else:
                with pytest.raises(BusinessError):
                    await channel.authenticated_request("GET", "/api/v1/perfil", context(), credentials)
                assert len(calls) == 1


@pytest.mark.parametrize(
    "status,code",
    [
        (400, "SOLICITUD_INVALIDA"),
        (401, "CREDENCIAL_INVALIDA"),
        (403, "VINCULO_INACTIVO"),
        (404, "NO_ENCONTRADO"),
        (409, "CONFLICTO"),
        (423, "CONFLICTO"),
        (500, "SERVICIO_NO_DISPONIBLE"),
        (302, "SERVICIO_NO_DISPONIBLE"),
    ],
)
async def test_errors_sanitized(status: int, code: str) -> None:
    async with UsersClient(
        Settings(),
        transport=httpx.MockTransport(
            lambda _: httpx.Response(status, json={"message": "SQL secret", "details": ["stacktrace"]})
        ),
    ) as client:
        with pytest.raises(BusinessError) as error:
            await client.request("GET", "/api/v1/usuarios", context())
    assert error.value.error.codigo == code == map_error(status).codigo
    assert "SQL" not in str(error.value)


@pytest.mark.parametrize("failure", ["timeout", "json", "identity", "path"])
async def test_transport_failures(failure: str) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if failure == "timeout":
            raise httpx.ReadTimeout("secret", request=request)
        return httpx.Response(200, text="bad" if failure == "json" else "{}")

    async with CredentialsClient(Settings(), transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(BusinessError):
            if failure == "identity":
                await client.validate(context())
            else:
                await client.request("GET", "https://external" if failure == "path" else "/api/v1/test", context())
