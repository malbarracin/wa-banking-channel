"""Verify the configured components from any working directory."""

import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
COMPONENTS = (Path("services/wa-users-service"),)


def main() -> int:
    failed = False
    for component in COMPONENTS:
        directory = ROOT / component
        wrapper = str(directory / "mvnw.cmd") if os.name == "nt" else "./mvnw"
        try:
            result = subprocess.run(
                [wrapper, "-q", "verify"], cwd=directory, capture_output=True, text=True,
                encoding="utf-8", errors="replace", check=False,
            )
            output = result.stdout + result.stderr
            (directory / ".verify.log").write_text(output, encoding="utf-8")
            if result.returncode:
                failed = True
                errors = [line for line in output.splitlines() if "[ERROR]" in line or "Error" in line]
                print(f"{component}: FALLÓ verify (log: {component}/.verify.log)")
                print("\n".join((errors or output.splitlines()[-20:])[-60:]))
            else:
                print(f"{component}: OK")
        except OSError as exc:
            failed = True
            print(f"{component}: FALLÓ verify: {exc}")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
