#!/usr/bin/env python3
"""Verify each integration component and keep detailed output in its target directory."""
import os
from pathlib import Path
import re
import subprocess
import time

COMPONENTS = ["platform/wa-event-platform"]
ROOT = Path(__file__).resolve().parent.parent


def main():
    failed = 0
    total = 0
    for name in COMPONENTS:
        component = ROOT / name
        if not (component / "pom.xml").is_file():
            continue
        total += 1
        log = component / "target" / "verify.log"
        log.parent.mkdir(parents=True, exist_ok=True)
        wrapper = "mvnw.cmd" if os.name == "nt" else "./mvnw"
        started = time.monotonic()
        try:
            result = subprocess.run([str(component / wrapper), "-q", "-B", "verify"], cwd=component,
                                    capture_output=True, text=True, errors="replace", check=False)
            output = result.stdout + result.stderr
            code = result.returncode
        except OSError as exc:
            output, code = str(exc), 1
        log.write_text(output, encoding="utf-8")
        if code:
            failed += 1
            print(f"FALLÓ {name}")
            lines = [line for line in output.splitlines() if re.search(r"\[ERROR\]|FAIL|Exception|Tests run:.*(?:Failures: [1-9]|Errors: [1-9])", line)]
            print("\n".join(lines[:60]) or output[:2000])
            print(f"Log: {log}")
        else:
            print(f"OK {name} ({time.monotonic() - started:.1f} s)")
    print(f"{total} componentes: {total - failed} OK, {failed} falló")
    return int(failed != 0)


if __name__ == "__main__":
    raise SystemExit(main())
