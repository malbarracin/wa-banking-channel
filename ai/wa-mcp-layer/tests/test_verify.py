"""Verifica el gate del equipo sin ejecutar procesos externos."""

import runpy
import subprocess
from pathlib import Path
from unittest.mock import patch

import pytest

VERIFY = Path(__file__).resolve().parents[2] / "verify.py"


def test_required_scaffold_files_exist() -> None:
    component = Path(__file__).resolve().parents[1]
    for name in ("pyproject.toml", "uv.lock", ".env.example", "src/wa_mcp_layer/__init__.py"):
        assert (component / name).is_file()
    assert VERIFY.is_file()


def test_verifier_discovers_components_and_runs_all_gates() -> None:
    main = runpy.run_path(str(VERIFY))["main"]
    projects = [VERIFY.parent / name / "pyproject.toml" for name in ("one", "two")]
    with (
        patch.object(Path, "glob", return_value=projects) as discover,
        patch("shutil.which", return_value="uv"),
        patch("subprocess.run", return_value=subprocess.CompletedProcess([], 0)) as run,
    ):
        assert main() == 0
    discover.assert_called_once_with("*/pyproject.toml")
    expected = [
        ["uv", "sync", "--frozen"],
        ["uv", "run", "ruff", "check", "."],
        ["uv", "run", "ruff", "format", "--check", "."],
        ["uv", "run", "mypy", "src"],
        ["uv", "run", "pytest", "-q", "--cov=src", "--cov-fail-under=80"],
    ]
    assert run.call_count == 10
    for index, call in enumerate(run.call_args_list):
        assert call.args == (expected[index % 5],)
        assert call.kwargs == {"cwd": projects[index // 5].parent, "check": False}


@pytest.mark.parametrize("failure", [subprocess.CompletedProcess([], 2), OSError("uv unavailable")])
def test_verifier_reports_failure_and_continues_other_components(failure: object) -> None:
    main = runpy.run_path(str(VERIFY))["main"]
    projects = [VERIFY.parent / name / "pyproject.toml" for name in ("one", "two")]
    with (
        patch.object(Path, "glob", return_value=projects),
        patch("shutil.which", return_value="uv"),
        patch("subprocess.run", side_effect=[failure, *[subprocess.CompletedProcess([], 0)] * 5]) as run,
    ):
        assert main() == 1
    assert run.call_count == 6
    assert run.call_args_list[-1].kwargs["cwd"] == projects[1].parent
