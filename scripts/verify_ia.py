"""Run the quality gates for the Python AI components."""

import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPONENTS = ("ai/wa-knowledge-rag",)
COMMANDS = (
    ("sync", ("uv", "sync", "--frozen")),
    ("lint", ("uv", "run", "--frozen", "ruff", "check", ".")),
    ("format", ("uv", "run", "--frozen", "ruff", "format", "--check", ".")),
    ("types", ("uv", "run", "--frozen", "mypy", "src")),
    (
        "tests",
        (
            "uv",
            "run",
            "--frozen",
            "pytest",
            "-q",
            "--no-header",
            "-rfE",
            "--tb=short",
            "--cov=src",
            "--cov-fail-under=80",
        ),
    ),
)


def main() -> int:
    failed = False
    for component in COMPONENTS:
        directory = ROOT / component
        if not directory.is_dir():
            continue
        if shutil.which("uv") is None:
            print(f"{component} FALLÓ sync (uv no está disponible)")
            return 1
        log_path = directory / ".verify.log"
        failures = []
        with log_path.open("w", encoding="utf-8") as log:
            for step, command in COMMANDS:
                result = subprocess.run(command, cwd=directory, capture_output=True, text=True, check=False)
                output = result.stdout + result.stderr
                log.write(f"\n$ {' '.join(command)}\n{output}")
                if result.returncode:
                    failures.append(step)
                    print(f"{component} FALLÓ {step}")
                    print("\n".join(output.splitlines()[-60:]))
                    if step == "sync":
                        break
        if failures:
            print(f"Log completo en {log_path}")
            failed = True
        else:
            print(f"{component} OK")
    return int(failed)


if __name__ == "__main__":
    raise SystemExit(main())
