"""Configuración sin red, base de datos ni modelo real."""

from pathlib import Path

import pytest
from pydantic import ValidationError

from wa_knowledge_rag.config import Settings


def test_defaults() -> None:
    settings = Settings()
    assert settings.port == 8102
    assert settings.embedding_model == "text-embedding-004"
    assert settings.embedding_dimensions == 768
    assert settings.rag_top_k == 5
    assert settings.log_level == "INFO"
    assert settings.database_url is None
    assert settings.google_api_key is None
    assert settings.rag_confidence_high_min is None
    assert settings.rag_confidence_medium_min is None


def test_environment_and_secret_masking(monkeypatch: pytest.MonkeyPatch) -> None:
    values = {
        "PORT": "8202",
        "DATABASE_URL": "postgresql://synthetic:fixture@localhost/test",
        "GOOGLE_API_KEY": "synthetic-test-key",
        "EMBEDDING_MODEL": "fixture-model",
        "EMBEDDING_DIMENSIONS": "32",
        "RAG_TOP_K": "3",
        "RAG_CONFIDENCE_HIGH_MIN": "0.8",
        "RAG_CONFIDENCE_MEDIUM_MIN": "0.4",
        "LOG_LEVEL": "WARNING",
    }
    for name, value in values.items():
        monkeypatch.setenv(name, value)
    settings = Settings()
    assert settings.port == 8202
    assert settings.embedding_model == "fixture-model"
    assert settings.embedding_dimensions == 32
    assert settings.rag_top_k == 3
    assert settings.rag_confidence_high_min == 0.8
    assert settings.rag_confidence_medium_min == 0.4
    assert settings.log_level == "WARNING"
    assert settings.database_url is not None
    assert settings.database_url.get_secret_value() == values["DATABASE_URL"]
    assert settings.google_api_key is not None
    assert settings.google_api_key.get_secret_value() == values["GOOGLE_API_KEY"]
    for secret in (values["DATABASE_URL"], values["GOOGLE_API_KEY"]):
        assert secret not in repr(settings)
        assert secret not in settings.model_dump_json()


@pytest.mark.parametrize(
    ("name", "value"),
    [
        ("PORT", "0"),
        ("PORT", "65536"),
        ("EMBEDDING_DIMENSIONS", "0"),
        ("RAG_TOP_K", "-1"),
        ("RAG_CONFIDENCE_HIGH_MIN", "1.1"),
        ("RAG_CONFIDENCE_MEDIUM_MIN", "-0.1"),
        ("LOG_LEVEL", "invalid"),
    ],
)
def test_invalid_environment(monkeypatch: pytest.MonkeyPatch, name: str, value: str) -> None:
    monkeypatch.setenv(name, value)
    with pytest.raises(ValidationError):
        Settings()


@pytest.mark.parametrize("medium", ["0.8", "0.9"])
def test_invalid_threshold_order(monkeypatch: pytest.MonkeyPatch, medium: str) -> None:
    monkeypatch.setenv("RAG_CONFIDENCE_HIGH_MIN", "0.8")
    monkeypatch.setenv("RAG_CONFIDENCE_MEDIUM_MIN", medium)
    with pytest.raises(ValidationError, match="debe ser menor"):
        Settings()


def test_example_loads_without_secrets() -> None:
    example = Path(__file__).resolve().parents[2] / ".env.example"
    settings = Settings(_env_file=example)
    assert settings.database_url is None
    assert settings.google_api_key is None
    assert settings.rag_confidence_high_min is None
    assert settings.rag_confidence_medium_min is None
