# Correcciones del PR #18 — dev-integracion

## Cambios

- `scripts/start.sh` y `scripts/start.ps1` delegan en `scripts/start_stack.py`.
  Python 3 ejecuta el build y luego aplica un plazo total de 600 s al arranque
  y la espera. Todos los servicios declarados por Compose deben existir:
  los permanentes deben estar running/healthy y `kafka-init` exited con código 0.
  Fallos de Docker, bootstrap, servicios detenidos o plazo agotado devuelven 1.
  No se imprimen variables de entorno ni logs de contenedores.
- Redis y el servidor de memoria publican exclusivamente en `127.0.0.1`.
- El criterio E2E de `specs/local-devops/spec.md` queda sin cumplir.
  La entrega E2E permanece abierta: faltan las integraciones H0.2/H7 señaladas
  en la revisión y una ejecución con evidencia del recorrido completo.
- Guías de arranque actualizadas en README, documentation y docs/local-devops.

## Verificación local (2026-11-01)

- `python -m unittest discover -s scripts -p "test_*.py"`: código 0,
  26 pruebas. Los nueve casos nuevos verifican éxito, ausencia de contenedores,
  healthcheck ausente, unhealthy/starting, réplicas, bootstrap fallido,
  permanente detenido, plazo agotado, recuperación y fallo de Docker.
  Se simulan Docker y el reloj; no acreditan salud runtime.
- `docker compose config --quiet`: código 0.
- `docker compose config --format json`, filtrado a los puertos Redis/memoria:
  ambos tienen `host_ip: 127.0.0.1` (6379 y 8000).
- `docker info`: código 1, acceso denegado a `dockerDesktopLinuxEngine`.
- `python scripts/start_stack.py`: código 1 ante Docker inaccesible,
  sin afirmar disponibilidad ni imprimir configuración resuelta.
- `scripts/start.ps1`: código 1; propaga el fallo de Docker del helper.
- `python scripts/verify_integracion.py`: código 0,
  `Verificación correcta: 3 componente(s).` Maven Wrapper `-q -B verify`
  secuencial para gateway, orquestador y plataforma de eventos, con los gates
  JaCoCo existentes. Log temporal: `step030-verify.log` en TEMP.

No se acreditan salud runtime, Testcontainers omitidos ni smoke E2E.
Para repetir con Docker accesible: ejecutar `scripts/start.ps1` o
`scripts/start.sh`, exigir código 0 y seguir `docs/local-devops.md`.
El éxito de arranque no satisface por sí solo el criterio E2E pendiente.
