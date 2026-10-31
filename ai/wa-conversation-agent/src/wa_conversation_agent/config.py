"""Configuración de H9 desde variables de entorno."""

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Las conexiones y los secretos permanecen fuera del contexto del modelo."""

    model_config = SettingsConfigDict(env_prefix="AGENT_")

    port: int = Field(default=8103, ge=1, le=65535)
    model: str = "gemini-2.5-flash"
    mcp_url: str = "http://localhost:8101/mcp"
    rag_url: str = "http://localhost:8102"
    rag_timeout_seconds: float = Field(default=10.0, gt=0, allow_inf_nan=False)
    redis_url: SecretStr = Field(default=SecretStr(""), repr=False)
    memory_server_url: SecretStr = Field(default=SecretStr(""), repr=False)
    service_token: SecretStr = Field(default=SecretStr(""), repr=False)
