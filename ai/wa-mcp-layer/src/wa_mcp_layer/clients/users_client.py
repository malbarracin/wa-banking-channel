"""Cliente H1; las capacidades restringen el acceso a soporte."""

from wa_mcp_layer.clients.base import BaseClient


class UsersClient(BaseClient):
    service_url_setting = "users_service_base_url"
