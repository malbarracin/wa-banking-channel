import sys
from pathlib import Path

import pytest

from wa_knowledge_rag import cli
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


def test_sync_loop_retries_and_uses_interval(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    attempts = []
    intervals = []

    def sync(*_args: object) -> list[str]:
        attempts.append("sync")
        if len(attempts) == 1:
            raise RuntimeError("Proveedor no disponible")
        return []

    def wait(interval: int) -> None:
        intervals.append(interval)
        if len(intervals) == 2:
            raise KeyboardInterrupt

    monkeypatch.setenv("KB_SYNC_INTERVAL", "2m")
    monkeypatch.setattr(sys, "argv", ["kb", "sync", "--loop"])
    monkeypatch.setattr(cli, "_run", sync)
    monkeypatch.setattr(cli, "sleep", wait)
    main()
    assert attempts == ["sync", "sync"]
    assert intervals == [120, 120]
    assert capsys.readouterr().err == "Proveedor no disponible\n"


@pytest.mark.parametrize(
    "args",
    [
        ["publish"],
        ["publish", "pieza", "--all-approved"],
        ["rollback", "--to", "1"],
        ["rollback", "pieza"],
        ["rollback", "--to-version", "1"],
        ["rollback", "pieza", "--to-version", "0"],
    ],
)
def test_release_commands_validate_targets_before_connecting(args: list[str], monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(sys, "argv", ["kb", *args])
    with pytest.raises(SystemExit) as error:
        main()
    assert error.value.code == 2
