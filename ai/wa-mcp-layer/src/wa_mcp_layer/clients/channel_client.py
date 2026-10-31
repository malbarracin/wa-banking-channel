"""H3: el contexto autenticado se obtiene de H2 en cada solicitud sensible."""

from pydantic import JsonValue

from wa_mcp_layer.clients.base import BaseClient
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.envelope import ContextoSesion
from wa_mcp_layer.security.session_context import ValidatedIdentity


class ChannelClient(BaseClient):
    service_url_setting = "channel_service_base_url"

    async def validated_identity(self, context: ContextoSesion, credentials: CredentialsClient) -> ValidatedIdentity:
        return await credentials.validate(context)

    def authenticated_headers(self, context: ContextoSesion, identity: ValidatedIdentity) -> dict[str, str]:
        return identity.headers(context)

    async def authenticated_request(
        self,
        method: str,
        path: str,
        context: ContextoSesion,
        credentials: CredentialsClient,
        *,
        body: JsonValue = None,
    ) -> JsonValue:
        identity = await self.validated_identity(context, credentials)
        return await self._request(method, path, self.authenticated_headers(context, identity), body=body)
