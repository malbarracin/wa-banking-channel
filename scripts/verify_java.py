#!/usr/bin/env python3
"""Corre `mvnw -q -B verify` en cada componente Java del repositorio (cada carpeta con pom.xml y mvnw).
Muestra el resumen y los errores; el log entero queda en <componente>/target/verify.log."""
import os, pathlib, subprocess, sys

root = pathlib.Path(__file__).resolve().parent.parent
SKIP = {".git", "node_modules", "target", "build", "dist", ".opensoma", ".venv"}
wrapper = "mvnw.cmd" if os.name == "nt" else "mvnw"
components = []
for dirpath, dirnames, filenames in os.walk(root):
    dirnames[:] = sorted(d for d in dirnames if d not in SKIP)
    if "pom.xml" in filenames and wrapper in filenames:
        components.append(pathlib.Path(dirpath))
        dirnames[:] = []  # los módulos de adentro los corre su propio pom
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
        errors = [l for l in lines if "ERROR" in l or "FAIL" in l][-40:]
        print("\n".join(errors or lines[-40:]))
print(f"{len(components) - len(failed)} de {len(components)} componentes Java verificados")
sys.exit(1 if failed else 0)
