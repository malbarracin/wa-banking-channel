"""H3: rutas fijas, número ligado al contexto y cuerpos del OpenAPI de H3."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, JsonValue

from wa_mcp_layer.clients.channel_client import ChannelClient
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.envelope import ContextoSesion
from wa_mcp_layer.errors import map_error
from wa_mcp_layer.security.session_context import BusinessError


class Numero(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True, strict=True)

    numero_whatsapp: str | None = Field(default=None, pattern=r"^\+[1-9][0-9]{6,14}$", repr=False)


class Verificacion(Numero):
    documentoTipo: str = Field(min_length=1, max_length=20)
    documentoNumero: str = Field(min_length=1, max_length=30, repr=False)
    datosVerificacion: dict[str, str] = Field(max_length=32, repr=False)


class Preferencias(Numero):
    idioma: str | None = Field(default=None, max_length=20)
    notificacionesHabilitadas: bool | None = None
    horarioPreferido: str | None = Field(default=None, max_length=100)


class Confirmacion(Numero):
    confirmacion: Literal[True]


class Bloqueo(Confirmacion):
    # POLITICA_BANCO es una operación interna: H9 no puede elegirla.
    motivo: Literal["SOLICITUD_CLIENTE"]


async def ejecutar(
    client: ChannelClient,
    credentials: CredentialsClient,
    context: ContextoSesion,
    params: Numero,
    *,
    method: str,
    suffix: str,
    authenticated: bool,
    soporte: bool,
) -> JsonValue:
    numero = params.numero_whatsapp or context.numero_whatsapp
    if not soporte and numero != context.numero_whatsapp:
        raise BusinessError(map_error(403))
    body: JsonValue = params.model_dump(mode="json", exclude={"numero_whatsapp"}, exclude_unset=True)
    if suffix == "terminos":
        path = "/api/v1/vinculos/terminos"
        body = {"numeroWhatsapp": numero}
    else:
        path = f"/api/v1/vinculos/{numero}/{suffix}"
    if method == "GET":
        body = None
    if authenticated:
        return await client.authenticated_request(method, path, context, credentials, body=body)
    return await client.request(method, path, context, body=body)
