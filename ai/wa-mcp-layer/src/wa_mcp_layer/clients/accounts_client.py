"""Cliente de productos H4."""

from wa_mcp_layer.clients.base import ProductClient


class AccountsClient(ProductClient):
    service_url_setting = "accounts_service_base_url"
