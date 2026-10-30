"""Configuración de infraestructura; no contiene credenciales de sesión."""

from pydantic import Field, HttpUrl
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Valores de entorno alineados con la arquitectura de H7."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    port: int = Field(default=8101, ge=1, le=65535)
    users_service_base_url: HttpUrl = HttpUrl("http://localhost:8080")
    credentials_service_base_url: HttpUrl = HttpUrl("http://localhost:8082")
    channel_service_base_url: HttpUrl = HttpUrl("http://localhost:8081")
    accounts_service_base_url: HttpUrl = HttpUrl("http://localhost:8084")
    cards_service_base_url: HttpUrl = HttpUrl("http://localhost:8086")
    insurance_service_base_url: HttpUrl = HttpUrl("http://localhost:8085")
    mcp_clients_connect_timeout_ms: int = Field(default=2000, gt=0)
    mcp_clients_read_timeout_ms: int = Field(default=5000, gt=0)
    mcp_capability_acceso_revocar_credenciales_masivo_enabled: bool = False
