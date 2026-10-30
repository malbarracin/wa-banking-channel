"""Regresiones de sesión y transporte con datos sintéticos y sin red."""

import httpx
import pytest
from test_clients import context

from wa_mcp_layer.clients.accounts_client import AccountsClient
from wa_mcp_layer.clients.cards_client import CardsClient
from wa_mcp_layer.clients.channel_client import ChannelClient
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.clients.insurance_client import InsuranceClient
from wa_mcp_layer.security.session_context import REVERIFY, BusinessError
from wa_mcp_layer.settings import Settings


@pytest.mark.parametrize("client_type", [AccountsClient, CardsClient, InsuranceClient])
@pytest.mark.parametrize("token", [None, "", "invalid", "a.b.c\r\nInjected: yes"])
async def test_product_rejects_bad_credentials_before_transport(client_type, token) -> None:
    def unexpected_request(request: httpx.Request) -> httpx.Response:
        pytest.fail("Una credencial ausente o mal formada no debe llegar al servicio")

    async with client_type(Settings(), transport=httpx.MockTransport(unexpected_request)) as client:
        with pytest.raises(BusinessError) as error:
            await client.request("GET", "/api/v1/productos", context(token))
    assert error.value.error.mensaje == REVERIFY
    assert error.value.error.reintentable is False


async def test_configured_timeouts_and_service_origin() -> None:
    settings = Settings(
        accounts_service_base_url="http://accounts.test:8123",
        mcp_clients_connect_timeout_ms=375,
        mcp_clients_read_timeout_ms=1250,
    )

    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == "http://accounts.test:8123/api/v1/cuentas"
        assert request.extensions["timeout"]["connect"] == 0.375
        assert request.extensions["timeout"]["read"] == 1.25
        return httpx.Response(200, json=[])

    async with AccountsClient(settings, transport=httpx.MockTransport(handler)) as client:
        assert await client.request("GET", "/api/v1/cuentas", context()) == []


async def test_channel_revalidates_after_revocation() -> None:
    validation_calls = 0
    channel_calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal validation_calls, channel_calls
        if request.url.path == "/api/v1/credenciales/validar":
            validation_calls += 1
            if validation_calls > 1:
                return httpx.Response(401, json={"message": "detalle interno sintético"})
            return httpx.Response(
                200,
                json={
                    "vigente": True,
                    "numeroWhatsapp": "+5491100000000",
                    "documentoTipo": "DNI",
                    "documentoNumero": "00001234",
                },
            )
        channel_calls += 1
        return httpx.Response(200, json={"ok": True})

    transport = httpx.MockTransport(handler)
    async with CredentialsClient(Settings(), transport=transport) as credentials:
        async with ChannelClient(Settings(), transport=transport) as channel:
            assert await channel.authenticated_request("GET", "/api/v1/perfil", context(), credentials) == {"ok": True}
            with pytest.raises(BusinessError) as error:
                await channel.authenticated_request("GET", "/api/v1/perfil", context(), credentials)
    assert error.value.error.codigo == "CREDENCIAL_INVALIDA"
    assert error.value.error.mensaje == REVERIFY
    assert validation_calls == 2
    assert channel_calls == 1
