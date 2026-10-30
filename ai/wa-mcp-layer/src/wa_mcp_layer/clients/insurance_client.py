"""Cliente de productos H6."""

from wa_mcp_layer.clients.base import ProductClient


class InsuranceClient(ProductClient):
    service_url_setting = "insurance_service_base_url"
