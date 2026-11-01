# Entorno local E2E (docker compose)

## Guía operativa — TASK-188 (2026-11-01)

Desde la raíz, con Docker Engine, Compose v2, Python 3 y puertos libres, crear
`.env` solo si falta y configurar sus secretos locales antes del arranque.
No sobrescribir una configuración existente. Bash:

```bash
test -f .env || cp .env.example .env
./scripts/start.sh
```

PowerShell:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
./scripts/start.ps1
```

Los scripts inician pero no esperan salud. Habilitar el transporte al simulador
y esperar como máximo 600 s por salud, además del tiempo de build:

```text
docker compose config --quiet
docker compose -f docker-compose.yml -f scripts/compose.smoke.yml config --quiet
docker compose -f docker-compose.yml -f scripts/compose.smoke.yml up -d --build --wait --wait-timeout 600
docker compose ps --all
docker compose ps --all --format json
```

No continuar ante código distinto de cero o servicios permanentes sin estado
running/healthy. `kafka-init` debe terminar con código 0, sin exigirle healthy.
Diagnosticar con `docker compose logs --no-color --tail 100 <servicio>` localmente;
redactar datos sensibles antes de compartir. No imprimir configuración resuelta.
`--env-file .env.example` no reemplaza los `env_file: .env` de cinco servicios.

Exportar en la terminal del seed/arnés los mismos valores locales de
`CREDENTIAL_H3_SERVICE_TOKEN`, `TWILIO_AUTH_TOKEN`, `TWILIO_WEBHOOK_PUBLIC_URL`,
`TWILIO_WHATSAPP_FROM` y `GOOGLE_API_KEY` que usa el stack; Python no lee `.env`.
H2/H3 comparten el primer token; H0.2/H9 comparten
`H9_SERVICE_TOKEN`/`AGENT_SERVICE_TOKEN`. No guardar sus valores en evidencia.
Con H1/H2/H3 y sus bases sanos:

```text
python scripts/seed_smoke.py seed --local-only
python scripts/seed_smoke.py check --local-only
```

Si ya existe seed, verificarlo o limpiarlo según [su guía](local-smoke-data.md).
La activación usa la excepción SQL exclusivamente local autorizada (TASK-193
sigue su sustitución por API). `check` reemite JWT e invalida el anterior: correr
antes del smoke, nunca durante una conversación. No insertar JWT en eventos.

Bash, usando solo el número sintético generado:

```bash
SMOKE_SENDER=$(python -c "import json; print('whatsapp:' + json.load(open('.local-seed/smoke.json'))['phone'])")
python scripts/smoke_twilio.py --sender "$SMOKE_SENDER" --body "Quiero consultar mi perfil" --expect QA-Smoke --timeout 60 --observe 15
```

PowerShell:

```powershell
$smokeSender = 'whatsapp:' + (Get-Content -Raw .local-seed/smoke.json | ConvertFrom-Json).phone
python scripts/smoke_twilio.py --sender $smokeSender --body "Quiero consultar mi perfil" --expect QA-Smoke --timeout 60 --observe 15
```

`GOOGLE_API_KEY` es opcional para levantar y validar el entorno, pero obligatoria
para inferencia real y para este arnés, que la exige antes de inyectar el webhook.
Una contingencia no acredita H9/H7/negocio. El seed no instala sesión en H0.2:
comprobar cómo obtiene la credencial H2 antes de afirmar perfil protegido.

### Evidencia y métricas del caso

ACK requerido <1 s, espera outbound 60 s y observación de replay 15 s. El JSON
del arnés no informa latencia del recorrido: medir por separado desde primera
inyección hasta captura outbound con reloj monotónico del mismo host o timestamps
sincronizados. El tiempo total incluye observación y no equivale a esa latencia.
Guardar fecha, códigos, estados e identificadores sintéticos; contrastar Kafka,
turno H9, auditoría H7 y llamada exitosa H3/perfil → H1. Contar efectos, envíos
adicionales, errores y DLQ para la correlación hasta fin de observación. Si se
usan métricas globales, tomar deltas antes/después sin tráfico concurrente y
registrar esa limitación. No reproducir DLQ automáticamente.

Resultado observado en run `20261031-f8ae`: Docker inaccesible por acceso
denegado al pipe `dockerDesktopLinuxEngine`; no hay salud ni E2E acreditados.
`GOOGLE_API_KEY` estaba ausente. El intento del tester salió con código 1 por
falta de `TWILIO_AUTH_TOKEN`, antes de enviar el webhook.

| Medición | Resultado observado | Condición al repetir |
| --- | --- | --- |
| Latencia ACK / recorrido | No medida, sin inyección | ACK <1 s; outbound dentro de espera de 60 s |
| POST del caso | 0 en intento abortado | 2 con el mismo MessageSid |
| Efectos / envíos adicionales por replay | No medidos | 1 efecto, 0 envíos adicionales durante 15 s |
| Errores del canal | No medidos; 1 fallo de prerrequisito del arnés | Sin errores para la correlación; registrar conteo |
| DLQ del caso | No consultada; ausencia no acreditada | 0 registros para la correlación durante el caso |

No reemplazar «no medido» por cero. [TASK-187](evidence/task-187-smoke.md)
registra 17 pruebas aisladas OK, validación Compose con código 0 y gate del tester
de tres componentes; gateway: 108 pruebas, 3 omitidas, 91,87 % de cobertura.
Esto no demuestra E2E ni Testcontainers omitidos. [TASK-185](evidence/task-185-startup-health.md)
documenta salud no verificada. Repetir con Docker, secretos exportados y sesión
comprobada, y completar mediciones antes de declarar éxito E2E.
No publicar cuerpos, JWT, Authorization, secretos ni teléfonos reales.

### Limpiar y detener

Con servicios sanos, limpiar por API (revocación, desvinculación y baja lógica;
se conservan auditorías):

```text
python scripts/seed_smoke.py cleanup --local-only
```

Parar preservando volúmenes: `./scripts/stop.sh` (Bash) o
`./scripts/stop.ps1` (PowerShell). Con override:

```text
docker compose -f docker-compose.yml -f scripts/compose.smoke.yml down
```

Reset explícito para entorno desechable: `./scripts/reset.sh` (Bash) o
`./scripts/reset.ps1` (PowerShell); elimina **todos** los volúmenes y datos.
Equivalente con override:

```text
docker compose -f docker-compose.yml -f scripts/compose.smoke.yml down -v
```

Retirar el archivo local obsoleto `.local-seed/smoke.json` si reset eliminó sus
datos, antes de crear otro seed. Ante limpieza parcial seguir la guía del seed.
No resetear como parte del arranque.

Verificación del repositorio, secuencial:

```text
python -m unittest discover -s scripts -p "test_*.py"
python scripts/verify_integracion.py
```

Gate por componente: `./mvnw -q -B clean verify` en Bash o
`./mvnw.cmd -q -B clean verify` en PowerShell, conservando JaCoCo ≥80 %.
Sin Docker no acredita pruebas de integración omitidas.

Verificación de TASK-188: las 17 pruebas de scripts pasaron; enlaces locales y
22 delimitadores de bloques Markdown comprobados. Compose con override,
`--env-file .env.example config --no-env-resolution --quiet`, terminó con código
0. `docker info` devolvió código 1 por acceso denegado al Engine.
`python scripts/verify_integracion.py`, ejecutado secuencialmente, informó
`Verificación correcta: 3 componente(s).` sin resumen `Fallaron:`. La sesión
PowerShell que redirigió su salida devolvió 1; no se equipara ese código exterior
con un gate acreditado con código 0. opensoma debe repetir la verificación fuera
de este entorno. No se acredita runtime ni tests omitidos por falta de Docker.

# Seed para el smoke E2E

Preparar y verificar los datos con la [guía del seed local](local-smoke-data.md).
Incluye autenticación H2/H3, excepción SQL local autorizada para activar el vínculo
y limpieza por API. No requiere datos ni números Twilio reales.

## Captura Twilio local (TASK-187)

El override `scripts/compose.smoke.yml` habilita exclusivamente en perfil `local`
el transporte del SDK hacia `host.docker.internal:8099`. No usar cuenta Twilio
real. El SDK mantiene serialización, autenticación, reserva y presupuesto de
reintentos. El callback configurado debe seguir siendo una URL HTTPS válida.
Preparar `.env` y exportar en la terminal los mismos valores locales de
`TWILIO_AUTH_TOKEN`, `TWILIO_WEBHOOK_PUBLIC_URL`, `TWILIO_WHATSAPP_FROM` y
`GOOGLE_API_KEY`; el arnés no lee ni imprime secretos. La URL pública es exactamente
la usada por RequestValidator, aunque la petición se envíe a localhost.

```text
docker compose -f docker-compose.yml -f scripts/compose.smoke.yml up -d --build --wait --wait-timeout 600
python scripts/seed_smoke.py check --local-only
python scripts/smoke_twilio.py --sender whatsapp:+15551234567 --body "Quiero consultar mi perfil" --expect QA-Smoke
```

Sustituir el número por el del archivo sintético `.local-seed/smoke.json`.
`--expect` acepta una subcadena de negocio, sin exigir una redacción literal del
modelo. El simulador arranca antes de inyectar el webhook y no guarda cuerpos ni
Authorization. Se firma el formulario, se exige ACK <1 s y eco de `correlationId`,
se repite inmediatamente el mismo MessageSid y se espera hasta 60 s el outbound.
Tras capturarlo se observan otros 15 s sin segundo envío. La ventana anti-replay
del gateway debe superar el intervalo entre ambos POST (default 300 s).
El puerto 8099 debe ser accesible desde Docker y limitarse al entorno local.

**El resultado `transport=ok` es parcial.** Antes de acreditar E2E, contrastar
los identificadores impresos con registros Kafka de received/ready/status,
headers y claves `conversationId`, turno H9 y auditoría de H7 con llamada exitosa
a H3/perfil → H1. Verificar todos los servicios sanos y cero registros de DLQ
para esa correlación durante el caso. `toolsUsed` o un saludo no acreditan éxito
de negocio. No hacen falta seis llamadas de negocio en un solo mensaje.
No publicar cuerpos, JWT ni secretos al guardar evidencia.

El seed no instala sesión en H0.2: antes de afirmar cobertura del perfil protegido
se necesita comprobar que el contexto técnico del turno obtiene la credencial
H2 por el mecanismo del orquestador. No insertar JWT en el webhook ni en eventos.
Sin esa integración una contingencia o solicitud de verificación es cobertura
parcial, aunque llegue al simulador.

Validación aislada: `python -m unittest discover -s scripts -p "test_*.py"`.
Evidencia de este paso: [TASK-187](evidence/task-187-smoke.md).

