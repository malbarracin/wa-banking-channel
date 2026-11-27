import sys
from pathlib import Path

import pytest

from wa_knowledge_rag.cli import main


def test_kb_help(monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]) -> None:
    monkeypatch.setattr(sys, "argv", ["kb", "--help"])

    with pytest.raises(SystemExit) as error:
        main()

    assert error.value.code == 0
    assert "usage: kb" in capsys.readouterr().out


def test_migrate_requires_database_url(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str], tmp_path: Path
) -> None:
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("KB_DATABASE_URL", "")
    monkeypatch.setenv("KB_EMBEDDING_DIMENSIONS", "768")
    monkeypatch.setattr(sys, "argv", ["kb", "db", "migrate"])
    with pytest.raises(SystemExit) as error:
        main()
    assert error.value.code == 1
    assert "Configurá KB_DATABASE_URL." in capsys.readouterr().err


def test_migrate_rejects_dimension_mismatch_before_connecting(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("KB_EMBEDDING_DIMENSIONS", "256")
    monkeypatch.setattr(sys, "argv", ["kb", "db", "migrate"])
    with pytest.raises(SystemExit) as error:
        main()
    assert error.value.code == 1
    assert "KB_EMBEDDING_DIMENSIONS=768" in capsys.readouterr().err
