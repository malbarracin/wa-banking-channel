from pathlib import Path

import pytest
from pydantic import SecretStr

from wa_knowledge_rag.config import Settings

SECRETS = {
    "KB_DATABASE_URL": "database-url-for-tests",
    "GOOGLE_API_KEY": "api-key-for-tests",
    "KB_CONSUMERS": "[]",
}
DEFAULTS = {
    "KB_PORT": "8102",
    "KB_EMBEDDING_MODEL": "gemini-embedding-001",
    "KB_EMBEDDING_DIMENSIONS": "768",
    "KB_ENVIRONMENT": "dev",
    "KB_CONFIDENCE_THRESHOLD": "0.70",
    "KB_MIN_RELEVANCE": "0.45",
    "KB_AMBIGUITY_MARGIN": "0.05",
    "KB_LEXICAL_BONUS": "0.05",
    "KB_APPROVAL_POLICY_PATH": "config/approval-policy.yaml",
    "KB_SYNC_INTERVAL": "24h",
    "KB_MAX_CHARS_WHATSAPP": "1000",
    "KB_EMBEDDING_TIMEOUT_S": "5",
}


def test_defaults(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    monkeypatch.chdir(tmp_path)
    for name in DEFAULTS.keys() | SECRETS.keys():
        monkeypatch.delenv(name, raising=False)

    settings = Settings()

    assert settings.model_dump(mode="json") == {
        "port": 8102,
        "database_url": None,
        "google_api_key": None,
        "embedding_model": "gemini-embedding-001",
        "embedding_dimensions": 768,
        "environment": "dev",
        "consumers": None,
        "confidence_threshold": 0.70,
        "min_relevance": 0.45,
        "ambiguity_margin": 0.05,
        "lexical_bonus": 0.05,
        "approval_policy_path": "config/approval-policy.yaml",
        "sync_interval": "24h",
        "max_chars_whatsapp": 1000,
        "embedding_timeout_s": 5,
    }


def test_environment_overrides(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    monkeypatch.chdir(tmp_path)
    overrides = {
        "KB_PORT": "8202",
        "KB_EMBEDDING_MODEL": "embedding-for-tests",
        "KB_EMBEDDING_DIMENSIONS": "384",
        "KB_ENVIRONMENT": "test",
        "KB_CONFIDENCE_THRESHOLD": "0.8",
        "KB_MIN_RELEVANCE": "0.5",
        "KB_AMBIGUITY_MARGIN": "0.12",
        "KB_LEXICAL_BONUS": "0.1",
        "KB_APPROVAL_POLICY_PATH": "test-policy.yaml",
        "KB_SYNC_INTERVAL": "12h",
        "KB_MAX_CHARS_WHATSAPP": "900",
        "KB_EMBEDDING_TIMEOUT_S": "1.5",
    }
    for name, value in (overrides | SECRETS).items():
        monkeypatch.setenv(name, value)

    settings = Settings()

    for name, value in overrides.items():
        assert str(getattr(settings, name.removeprefix("KB_").lower())) == value
    for name, value in SECRETS.items():
        secret = getattr(settings, name.removeprefix("KB_").lower())
        assert isinstance(secret, SecretStr)
        assert secret.get_secret_value() == value
        assert value not in repr(settings)


def test_env_example() -> None:
    env_file = Path(__file__).resolve().parents[2] / ".env.example"
    values = dict(line.split("=", 1) for line in env_file.read_text(encoding="utf-8").splitlines())

    assert values == DEFAULTS | dict.fromkeys(SECRETS, "")
