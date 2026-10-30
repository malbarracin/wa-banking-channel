"""Ejecuta los quality gates de cada componente Python de IA con uv."""

from pathlib import Path
import shutil
import subprocess
import sys


def main() -> int:
    root = Path(__file__).resolve().parent
    projects = sorted(root.glob("*/pyproject.toml"))
    if not projects:
        print("No hay componentes Python de IA.")
        return 0
    uv = shutil.which("uv")
    if uv is None:
        print("ERROR: uv no está disponible en PATH.", file=sys.stderr)
        return 1
    steps = (
        ("sync", "--frozen"),
        ("run", "ruff", "check", "."),
        ("run", "ruff", "format", "--check", "."),
        ("run", "mypy", "src"),
        ("run", "pytest", "-q", "--cov=src", "--cov-fail-under=80"),
    )
    failed = False
    for project in projects:
        for step in steps:
            print(f"[{project.parent.name}] uv {' '.join(step)}", flush=True)
            try:
                result = subprocess.run([uv, *step], cwd=project.parent, check=False)
            except OSError as error:
                print(f"ERROR [{project.parent.name}] {step}: {error}", file=sys.stderr)
                failed = True
                break
            if result.returncode:
                print(f"ERROR [{project.parent.name}] {' '.join(step)}: {result.returncode}", file=sys.stderr)
                failed = True
                break
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
