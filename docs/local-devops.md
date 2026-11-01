# Entorno local E2E (docker compose)

## Requisitos
- Docker Desktop / Docker Engine
- Docker Compose v2
- Puertos libres segun .env.example

## Levantar
`ash
cp .env.example .env
./scripts/start.sh
`
Windows PowerShell:
`powershell
Copy-Item .env.example .env -Force
./scripts/start.ps1
`
Directo:
`ash
docker compose up -d --build
`

## Parar
`ash
./scripts/stop.sh
docker compose down
`

## Reset (limpiar volumenes/datos)
`ash
./scripts/reset.sh
docker compose down -v
`

## Verificacion
`ash
docker compose config
docker compose ps
`

## Smoke E2E (webhook simulado)
1. Asegurar stack healthy: docker compose ps debe mostrar todos healthy
2. Flujo: Twilio simulado -> gateway -> Kafka (wa.message.received.v1) -> orquestador -> agente H9 -> servicios H1-H6/MCP/RAG -> respuesta outbound (wa.message.ready-to-send.v1) -> gateway -> Twilio simulado

## Topics creados
- wa.message.received.v1 (particionado por conversationId)
- wa.message.ready-to-send.v1
- wa.message.status.v1
- wa.message.dlq.v1

## Notas
- Sin secretos reales: usar solo .env.example como referencia
- Kafka en modo KRaft
- Healthchecks ordenan el arranque
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

