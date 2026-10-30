"""Cliente de productos H5."""

from wa_mcp_layer.clients.base import ProductClient


class CardsClient(ProductClient):
    service_url_setting = "cards_service_base_url"
