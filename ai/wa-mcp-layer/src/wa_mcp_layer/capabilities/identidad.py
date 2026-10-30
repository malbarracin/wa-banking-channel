"""H1: consultas exclusivas de soporte, sin credencial de producto."""

from pydantic import BaseModel, ConfigDict, Field, JsonValue

from wa_mcp_layer.clients.users_client import UsersClient
from wa_mcp_layer.envelope import ContextoSesion


class ConsultaCliente(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)

    tipo: str = Field(pattern=r"^[A-Za-z0-9_-]{1,20}$")
    numero: str = Field(pattern=r"^[A-Za-z0-9-]{1,30}$", repr=False)


async def consultar_cliente(client: UsersClient, context: ContextoSesion, params: ConsultaCliente) -> JsonValue:
    return await client.request("GET", f"/api/v1/users/{params.tipo}/{params.numero}", context)


async def consultar_estado_cliente(client: UsersClient, context: ContextoSesion, params: ConsultaCliente) -> JsonValue:
    return await client.request("GET", f"/api/v1/users/{params.tipo}/{params.numero}/status", context)
