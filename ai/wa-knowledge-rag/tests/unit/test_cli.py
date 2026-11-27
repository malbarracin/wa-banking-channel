import sys

import pytest

from wa_knowledge_rag.cli import main


def test_kb_help(monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]) -> None:
    monkeypatch.setattr(sys, "argv", ["kb", "--help"])

    with pytest.raises(SystemExit) as error:
        main()

    assert error.value.code == 0
    assert "usage: kb" in capsys.readouterr().out
