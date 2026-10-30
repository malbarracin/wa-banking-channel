"""Transporte sin reintentos; nunca propaga mensajes técnicos del origen."""

from typing import Self

import httpx
from pydantic import JsonValue

from wa_mcp_layer.envelope import ContextoSesion
from wa_mcp_layer.errors import map_error as map_error
from wa_mcp_layer.security.session_context import BusinessError, require_credential
from wa_mcp_layer.settings import Settings


class BaseClient:
    service_url_setting: str

    def __init__(self, settings: Settings, *, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self._http = httpx.AsyncClient(
            base_url=str(getattr(settings, self.service_url_setting)),
            timeout=httpx.Timeout(
                settings.mcp_clients_read_timeout_ms / 1000,
                connect=settings.mcp_clients_connect_timeout_ms / 1000,
            ),
            transport=transport,
            follow_redirects=False,
            trust_env=False,
        )

    async def __aenter__(self) -> Self:
        return self

    async def __aexit__(self, *_args: object) -> None:
        await self.aclose()

    async def aclose(self) -> None:
        await self._http.aclose()

    def headers(self, context: ContextoSesion) -> dict[str, str]:
        return {}

    async def request(self, method: str, path: str, context: ContextoSesion, *, body: JsonValue = None) -> JsonValue:
        return await self._request(method, path, self.headers(context), body=body)

    async def _request(self, method: str, path: str, headers: dict[str, str], *, body: JsonValue = None) -> JsonValue:
        # Las rutas las fijan las capacidades publicadas; nunca aceptar una URL externa.
        if not path.startswith("/api/v1/") or ".." in path or "?" in path or "#" in path:
            raise BusinessError(map_error(400))
        try:
            response = await self._http.request(method, path, headers=headers, json=body)
        except httpx.RequestError:
            raise BusinessError(map_error(None)) from None
        if not 200 <= response.status_code < 300:
            raise BusinessError(map_error(response.status_code))
        if response.status_code == 204:
            return None
        try:
            value: JsonValue = response.json()
            return value
        except ValueError:
            raise BusinessError(map_error(503)) from None


class ProductClient(BaseClient):
    def headers(self, context: ContextoSesion) -> dict[str, str]:
        return {"X-Credencial-Sesion": require_credential(context)}
