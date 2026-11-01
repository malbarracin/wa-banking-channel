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

