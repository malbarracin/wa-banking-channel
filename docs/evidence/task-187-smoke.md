# TASK-187 — evidencia del paso

Fecha: 2026-11-01. No se ejecutó el recorrido contra contenedores.

- `docker info --format '{{.ServerVersion}}'`: acceso denegado al pipe
  `dockerDesktopLinuxEngine`.
- `GOOGLE_API_KEY` no está presente en el entorno del proceso. No se inspeccionaron
  ni publicaron valores secretos de `.env`.
- `python -m unittest discover -s scripts -p "test_*.py"`: 13 pruebas OK;
  incluyen firma HMAC-SHA1 contra vector RFC 2202, orden de campos y captura HTTP
  de formularios con SIDs únicos.
- `docker compose -f docker-compose.yml -f scripts/compose.smoke.yml --env-file
  .env.example config --no-env-resolution --quiet`: código 0. No acredita arranque.
- Se intentaron `mvnw.cmd -q -B clean verify` del gateway y
  `python scripts/verify_integracion.py`. Se interrumpió la ejecución simultánea
  para evitar interferencia; no se acredita gate global ni cobertura JaCoCo.
  Los reportes disponibles del gateway contienen 108 pruebas, cero fallos,
  cero errores y tres omitidas; Testcontainers informa Docker inaccesible.
  Repetir ambos gates secuencialmente con Docker disponible.
- `mvnw.cmd -q -B -Dtest=TwilioSenderTest test`: código 0, usando el transporte
  local real contra WireMock (incluye envío, errores, timeout y rechazo de URL).

Se entrega arnés de transporte, simulador y redirección local del SDK.
No se acreditan latencias reales, destino/cuerpo del modelo, llamada de negocio,
salud global, correlación extremo a extremo, duplicados ni ausencia de DLQ.
Para repetir, usar los comandos de `docs/local-devops.md` con Docker accesible y
clave Google válida. Conservar evidencia de Kafka y auditorías H9/H7/servicio;
la salida del arnés distingue explícitamente transporte de E2E.
