"""Pruebas de la configuración base, sin servicios externos."""

import pytest
from pydantic import ValidationError

from wa_mcp_layer.settings import Settings


@pytest.fixture(autouse=True)
def isolate_settings_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    for field in Settings.model_fields:
        monkeypatch.delenv(field.upper(), raising=False)


def test_example_configuration() -> None:
    settings = Settings(_env_file=".env.example")
    assert settings.port == 8101
    assert settings.users_service_base_url.port == 8080
    assert settings.credentials_service_base_url.port == 8082
    assert settings.channel_service_base_url.port == 8081
    assert settings.accounts_service_base_url.port == 8084
    assert settings.cards_service_base_url.port == 8086
    assert settings.insurance_service_base_url.port == 8085
    assert settings.mcp_clients_connect_timeout_ms == 2000
    assert settings.mcp_clients_read_timeout_ms == 5000
    assert not settings.mcp_capability_acceso_revocar_credenciales_masivo_enabled


def test_environment_overrides(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PORT", "8201")
    monkeypatch.setenv("USERS_SERVICE_BASE_URL", "https://users.example.test")
    settings = Settings(_env_file=None)
    assert settings.port == 8201
    assert settings.users_service_base_url.host == "users.example.test"


@pytest.mark.parametrize(
    ("variable", "value"),
    [("PORT", "0"), ("MCP_CLIENTS_READ_TIMEOUT_MS", "0"), ("USERS_SERVICE_BASE_URL", "invalid")],
)
def test_invalid_environment(variable: str, value: str, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv(variable, value)
    with pytest.raises(ValidationError):
        Settings(_env_file=None)
