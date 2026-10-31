"""Verifica importación sin red y configuración aislada del modelo."""

import importlib

import pytest
from pydantic import ValidationError

from wa_conversation_agent import agent
from wa_conversation_agent.config import Settings


def test_import_without_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    module = importlib.reload(agent)
    assert module.root_agent.name == "wa_conversation_agent"
    assert module.root_agent.tools == []


def test_environment_and_secret_isolation(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_PORT", "8203")
    monkeypatch.setenv("AGENT_MODEL", "test-model")
    monkeypatch.setenv("AGENT_SERVICE_TOKEN", "synthetic-token")
    settings = Settings()
    assert settings.port == 8203
    assert settings.model == "test-model"
    assert settings.service_token.get_secret_value() == "synthetic-token"
    assert "synthetic-token" not in repr(settings)
    assert "synthetic-token" not in settings.model_dump_json()
    assert "synthetic-token" not in str(agent.root_agent.instruction)


def test_invalid_port_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(port=0)
