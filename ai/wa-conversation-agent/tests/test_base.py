"""Verifica importación sin red y configuración aislada del modelo."""

import importlib
import tomllib
from pathlib import Path

import pytest
from pydantic import ValidationError

from wa_conversation_agent import agent
from wa_conversation_agent.config import Settings

COMPONENT = Path(__file__).resolve().parents[1]


def test_project_manifest_and_lock() -> None:
    manifest = tomllib.loads((COMPONENT / "pyproject.toml").read_text(encoding="utf-8"))
    assert manifest["project"]["requires-python"] == ">=3.12"
    dependencies = manifest["dependency-groups"]["dev"]
    for name in ("ruff", "mypy", "pytest", "pytest-asyncio", "pytest-cov", "respx", "testcontainers"):
        assert any(dependency.startswith(f"{name}>=") for dependency in dependencies)
    lock = tomllib.loads((COMPONENT / "uv.lock").read_text(encoding="utf-8"))
    assert any(package["name"] == "wa-conversation-agent" for package in lock["package"])


def test_environment_example_is_usable_without_credentials(monkeypatch: pytest.MonkeyPatch) -> None:
    example = dict(
        line.split("=", 1)
        for line in (COMPONENT / ".env.example").read_text(encoding="utf-8").splitlines()
        if line and not line.startswith("#")
    )
    assert example["AGENT_PORT"] == "8103"
    assert example["AGENT_MCP_URL"] == "http://localhost:8101/mcp"
    assert example["AGENT_RAG_URL"] == "http://localhost:8102"
    for name in ("AGENT_REDIS_URL", "AGENT_MEMORY_SERVER_URL", "AGENT_SERVICE_TOKEN", "GOOGLE_API_KEY"):
        assert example[name] == ""
    for name, value in example.items():
        monkeypatch.setenv(name, value)
    settings = Settings()
    assert settings.port == 8103
    assert settings.service_token.get_secret_value() == ""


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
