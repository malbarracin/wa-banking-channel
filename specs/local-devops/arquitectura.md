---
title: Runtime local Compose (E2E) — arquitectura
status: aprobada
source: specs/local-devops/spec.md + int-architecture
updated: 2026-10-31T20:15:00Z
---

# Arquitectura — Runtime local Compose (E2E)

## Objetivo
Orquestar todo el canal WhatsApp bancario en un único `docker-compose.yml` en la raíz, con scripts para levantar/parar/resetear, bootstrap de topics Kafka (KRaft), healthchecks, red compartida, volúmenes y configuración vía `.env`.

## Topología
- **Red**: red única `wa-channel` (bridge) para resolución por nombre de servicio.
- **Kafka**: broker KRaft único compartido (`kafka:29092` interno, `localhost:9092` host). Sin ZooKeeper.
- **Init**: contenedor `kafka-init` crea topics del piloto con 3 particiones, `replication-factor 1`, `cleanup.policy=delete` y retenciones acordes a `shared/contracts/events/topics.md`.
- **Servicios Java**: `gateway/wa-twilio-gateway` (8090), `platform/wa-event-platform` (8092), `platform/wa-channel-orchestrator` (8091), `services/wa-*` (8080-8086) con sus Postgres.
- **IA**: `ai/wa-mcp-layer` (8101), `ai/wa-knowledge-rag` (8102), `ai/wa-conversation-agent` (8103), con `kb-postgres` (pgvector), `agent-redis`, `agent-memory-server`.
- **Persistencia**: volúmenes nombrados por componente (kafka, postgres, redis).

## Orden de arranque
1. Infra base: `kafka` (healthy) → `kafka-init` (completed) → Postgres/Redis según dependencia.
2. Plataforma: `wa-event-platform` (opcional pero parte del stack), `wa-twilio-gateway`, `wa-channel-orchestrator`.
3. Servicios H1–H6 con sus DBs.
4. IA: `kb-postgres` → `wa-knowledge-rag`; `agent-redis` → `agent-memory-server` → `wa-conversation-agent`; `wa-mcp-layer` depende de H1–H6.

## Configuración
- `.env` en raíz (no commiteado). `.env.example` con nombres completos.
- Puertos mapeados a host según `project.yaml` (MCP 8101, RAG 8102, agente 8103). Java usan puertos documentados.
- Variables compartidas: `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`.

## Seguridad
- Sin secretos reales. Solo `.env.example`.
- Kafka local sin TLS/SASL.
- Webhook Twilio validado por componente gateway (fuera de alcance de este compose).

## Observabilidad
- Healthchecks en todos los servicios (`/actuator/health` Java, `/health` Python).
- `depends_on` con `condition: service_healthy` / `service_completed_successfully`.
- `correlationId` propagado por componentes (ya implementado).

## ADRs
- ADR-001: Topología local multi-contenedor con docker-compose raíz vs monolito.
- ADR-002: Kafka KRaft para entorno local (sin ZooKeeper).
