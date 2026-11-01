"""Start Compose and require healthy services plus successful Kafka bootstrap."""
import json
from pathlib import Path
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[1]


def docker(*args, timeout=None):
    result = subprocess.run(
        ["docker", *args], cwd=ROOT, capture_output=True, text=True,
        timeout=timeout, check=False,
    )
    if result.returncode:
        raise RuntimeError("Docker falló; consultar compose ps y logs localmente.")
    return result.stdout


def pending_services(services, containers):
    pending = []
    for service in services:
        matches = [c for c in containers if
                   c.get("Config", {}).get("Labels", {}).get("com.docker.compose.service") == service]
        if not matches:
            pending.append(service + ": ausente")
        for container in matches:
            state = container["State"]
            if service == "kafka-init":
                if state["Status"] == "exited":
                    if state["ExitCode"] != 0:
                        raise RuntimeError("kafka-init terminó con error.")
                    continue
            elif state["Status"] == "running" and state.get("Health", {}).get("Status") == "healthy":
                continue
            elif state["Status"] in {"exited", "dead"}:
                raise RuntimeError(service + ": servicio permanente detenido.")
            pending.append(service + ": " + state["Status"] + "/" +
                           state.get("Health", {}).get("Status", "sin healthcheck"))
    return pending


def main():
    try:
        services = docker("compose", "config", "--services").split()
        if not services or "kafka-init" not in services:
            raise RuntimeError("Compose no declara el stack esperado con kafka-init.")
        docker("compose", "build")
        deadline = time.monotonic() + 600
        docker("compose", "up", "-d", timeout=600)
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError("Tiempo de espera de salud agotado (600 s).")
            ids = docker("compose", "ps", "--all", "--quiet", timeout=remaining).split()
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError("Tiempo de espera de salud agotado (600 s).")
            containers = json.loads(docker("inspect", *ids, timeout=remaining)) if ids else []
            pending = pending_services(services, containers)
            if not pending:
                print("Stack sano; kafka-init terminó con código 0.")
                return 0
            print("Esperando: " + ", ".join(pending), flush=True)
            time.sleep(max(0, min(2, deadline - time.monotonic())))
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
