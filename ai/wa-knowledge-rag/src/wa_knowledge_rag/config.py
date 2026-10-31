"""Configuración por entorno; no abre conexiones ni carga modelos al importar."""

from typing import Literal, Self

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Variables del contrato H8; los umbrales requieren calibración posterior."""

    model_config = SettingsConfigDict(env_file=".env", env_ignore_empty=True, extra="ignore")

    port: int = Field(default=8102, ge=1, le=65535)
    database_url: SecretStr | None = None
    google_api_key: SecretStr | None = None
    embedding_model: str = Field(default="text-embedding-004", min_length=1)
    embedding_dimensions: int = Field(default=768, gt=0)
    rag_top_k: int = Field(default=5, gt=0)
    rag_confidence_high_min: float | None = Field(default=None, ge=0, le=1)
    rag_confidence_medium_min: float | None = Field(default=None, ge=0, le=1)
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"

    @model_validator(mode="after")
    def validate_confidence_order(self) -> Self:
        high = self.rag_confidence_high_min
        medium = self.rag_confidence_medium_min
        if high is not None and medium is not None and medium >= high:
            raise ValueError("RAG_CONFIDENCE_MEDIUM_MIN debe ser menor que RAG_CONFIDENCE_HIGH_MIN")
        return self
