"""Aislamiento de la configuración frente al entorno de la máquina."""

import pytest

from wa_knowledge_rag.config import Settings


@pytest.fixture(autouse=True)
def clean_settings_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in Settings.model_fields:
        monkeypatch.delenv(name.upper(), raising=False)
    monkeypatch.setitem(Settings.model_config, "env_file", None)
