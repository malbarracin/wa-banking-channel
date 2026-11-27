#!/usr/bin/env python3
"""Verify each Java component, retaining its complete Maven log."""
import os
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parent.parent
skip = {".git", "node_modules", "target", "build", "dist", ".opensoma", ".venv"}
wrapper = "mvnw.cmd" if os.name == "nt" else "mvnw"
components = []
for dirpath, dirnames, filenames in os.walk(root):
    dirnames[:] = sorted(d for d in dirnames if d not in skip)
    if "pom.xml" in filenames and wrapper in filenames:
        components.append(pathlib.Path(dirpath))
        dirnames[:] = []
failed = []
for comp in components:
    log = comp / "target" / "verify.log"
    log.parent.mkdir(exist_ok=True)
    cmd = [str(comp / wrapper)] if os.name == "nt" else ["./" + wrapper]
    with open(log, "w", encoding="utf-8", errors="replace") as out:
        code = subprocess.call(cmd + ["-q", "-B", "verify"], cwd=comp, stdout=out, stderr=subprocess.STDOUT)
    name = comp.relative_to(root).as_posix() or "."
    print(f"{'OK   ' if code == 0 else 'FALLA'} {name}")
    if code != 0:
        failed.append(name)
        lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
        errors = [line for line in lines if "ERROR" in line or "FAIL" in line][-40:]
        print("\n".join(errors or lines[-40:]))
print(f"{len(components) - len(failed)} de {len(components)} componentes Java verificados")
sys.exit(1 if failed else 0)
