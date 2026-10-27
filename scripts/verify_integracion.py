"""Verifica todos los componentes de integración con su propio Maven Wrapper."""
from pathlib import Path
import os
import subprocess
import sys


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    projects = sorted([*root.glob("gateway/*/pom.xml"), *root.glob("platform/*/pom.xml")])
    failed = []
    for pom in projects:
        component = pom.parent
        wrapper = component / ("mvnw.cmd" if os.name == "nt" else "mvnw")
        print(f"Verificando {component.relative_to(root)}", flush=True)
        try:
            result = subprocess.run([str(wrapper), "-q", "-B", "verify"], cwd=component, check=False)
            if result.returncode:
                failed.append(str(component.relative_to(root)))
        except OSError as error:
            print(f"No se pudo ejecutar {wrapper}: {error}", file=sys.stderr)
            failed.append(str(component.relative_to(root)))
    if failed:
        print("Fallaron: " + ", ".join(failed), file=sys.stderr)
        return 1
    print(f"Verificación correcta: {len(projects)} componente(s).", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
