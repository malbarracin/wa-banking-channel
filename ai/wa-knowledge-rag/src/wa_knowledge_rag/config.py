"""Runtime configuration for the knowledge base."""

from pathlib import Path
from typing import Annotated, Literal

from pydantic import Field, PositiveFloat, PositiveInt, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

UnitInterval = Annotated[float, Field(ge=0, le=1)]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="KB_",
        env_file=".env",
        env_file_encoding="utf-8",
        env_ignore_empty=True,
        hide_input_in_errors=True,
    )

    port: Annotated[int, Field(ge=1, le=65535)] = 8102
    database_url: SecretStr | None = None
    google_api_key: SecretStr | None = Field(default=None, validation_alias="GOOGLE_API_KEY")
    embedding_model: str = "gemini-embedding-001"
    embedding_dimensions: PositiveInt = 768
    environment: Literal["dev", "test", "real"] = "dev"
    consumers: SecretStr | None = None
    confidence_threshold: UnitInterval = 0.70
    min_relevance: UnitInterval = 0.45
    ambiguity_margin: UnitInterval = 0.05
    lexical_bonus: UnitInterval = 0.05
    approval_policy_path: Path = Path("config/approval-policy.yaml")
    sync_interval: str = "24h"
    max_chars_whatsapp: PositiveInt = 1000
    embedding_timeout_s: PositiveFloat = 5
