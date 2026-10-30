"""Cliente H2 sin caché de vigencia."""

from pydantic import ValidationError

from wa_mcp_layer.clients.base import BaseClient, map_error
from wa_mcp_layer.envelope import ContextoSesion
from wa_mcp_layer.security.session_context import BusinessError, ValidatedIdentity, require_credential


class CredentialsClient(BaseClient):
    service_url_setting = "credentials_service_base_url"

    async def validate(self, context: ContextoSesion) -> ValidatedIdentity:
        result = await self.request(
            "POST", "/api/v1/credenciales/validar", context, body={"credencial": require_credential(context)}
        )
        try:
            identity = ValidatedIdentity.model_validate(result)
        except ValidationError:
            raise BusinessError(map_error(401)) from None
        identity.headers(context)
        return identity
