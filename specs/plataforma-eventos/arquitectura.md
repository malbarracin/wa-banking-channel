---
title: Plataforma de Eventos (Kafka) — arquitectura
status: borrador
source: specs/plataforma-eventos/spec.md
updated: 2026-10-27
---

# Arquitectura — Plataforma de Eventos (H0.1)

## Base

- **Componente nuevo**: `platform/wa-event-platform` (Kafka KRaft). No existe aún.
- **Java 21 + Spring Boot 3.x**, Maven con wrapper propio (`./mvnw`).
- **GroupId**: `com.bcra` (de `project.yaml` → `java.group_id`).
- **Base package**: `com.bcra.events` (propuesto; sin bloque `integracion.*` en `project.yaml`, anotar).
- **Puerto**: 8092 (gateway 8090, orquestador 8091 ya asignados).
- **Kafka**: modo KRaft, sin ZooKeeper. Topics con ≥ 3 particiones en local, factor de replicación 1 local / 3 prod.
- **Persistencia**: no aplica al componente (no es base de datos); los consumidores idempotentes viven en H0/H0.2.

## Árbol de paquetes

```
com.bcra.events/
├── EventsApplication.java
├── messaging/
│   ├── producer/   KafkaEventProducer (valida schema, publica con acks=all + idempotence)
│   ├── consumer/   KafkaEventConsumer base (valida al recibir, DLQ en inválido)
│   ├── config/     KafkaProducerConfig, KafkaConsumerConfig, KafkaTopicConfig
│   └── schema/     EventEnvelope, MessageReceivedPayload, MessageReadyToSendPayload, MessageStatusPayload
├── topic/          TopicDefinitions (nombres, particiones, retención)
├── dlq/            DeadLetterPublisher (wa.message.dlq.v1)
├── config/         OpenApiConfig, ActuatorConfig
└── observability/  CorrelationIdFilter (MDC), MetricsConfig
```

## Contratos de eventos

- Esquemas JSON Schema en `shared/contracts/events/<evento>.v<n>.schema.json` con `additionalProperties: false` en el sobre.
- Sobre común: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, `conversationId`, `messageId`, `payload`.
- Headers Kafka: `correlationId`, `eventType`, `eventVersion`. Clave del registro: `conversationId`.
- Productor valida antes de enviar; consumidor valida al recibir; lo inválido → DLQ sin reintento.

## Topics

| Topic | Particiones (local) | Retención | Productor | Consumidor |
|---|---|---|---|---|
| `wa.message.received.v1` | 3 | 7 días | H0 | H0.2 |
| `wa.message.ready-to-send.v1` | 3 | 7 días | H0.2 | H0 |
| `wa.message.status.v1` | 3 | 30 días | H0 | H0.2 / obs |
| `wa.message.dlq.v1` | 1 | 90 días | infra | soporte |

- Clave de partición = `conversationId` (orden por conversación).
- Compresión: snappy. `min.insync.replicas=2` en prod.

## Configuración Kafka

- **Productor**: `acks=all`, `enable.idempotence=true`, `retries` configurado, `max.in.flight.requests.per.connection=5`, transacciones opcionales (`isolation.level=read_committed` si se usan). Outbox en H0.2 para publicar tras cambiar estado.
- **Consumidor**: `ackMode=MANUAL` (commit tras efecto exitoso), `isolation.level=read_committed`, concurrencia ≤ particiones (3), `max.poll.records` acotado.
- **Reintentos**: backoff exponencial (DefaultErrorHandler + DeadLetterPublishingRecoverer), máx. configurado; errores de validación no se reintentan.
- **DLQ**: evento original + headers de la excepción (sin stack trace completo ni datos sensibles).

## Idempotencia

- H0: por `MessageSid` de Twilio (tabla/caché con TTL).
- H0.2: por `messageId` con restricción única en PostgreSQL (insert-if-absent en la misma transacción que el efecto).
- Plataforma: entrega at-least-once; consumidores idempotentes.

## Seguridad

- TLS + SASL en ambientes no locales; ACL por topic (cada componente solo lo suyo) — documentado como pendiente en local.
- Eventos sin secretos ni PII innecesaria; teléfono enmascarado en logs.
- JSON Schema estricto; sin deserialización polimórfica de tipos arbitrarios.
- Sin stack traces hacia afuera.

## Observabilidad

- `correlationId` en MDC y headers de Kafka, propagado punta a punta.
- Métricas: lag por consumidor, throughput, errores, tamaño de DLQ, latencia de ACK, duración de sagas.
- Actuator: `health`, `info`, `prometheus`. Logs JSON.

## ADRs

- `docs/adr/ADR-001-kafka-backbone.md`
- `docs/adr/ADR-002-at-least-once-idempotency.md`
- `docs/adr/ADR-003-partition-by-conversation.md`

## Riesgos / decisiones pendientes

- Retención exacta por topic (tiempo/bytes) para el piloto — a confirmar.
- Política exacta de reintentos (max intentos, backoff) y cuándo enviar a DLQ.
- Mecanismo de registro/validación de esquemas (Schema Registry o validación local).
- Configuración de seguridad entre productores/consumidores para el piloto.
- Estrategia concreta de reproceso desde DLQ (automatizado/manual).
- Base package `com.bcra.events` no está en `project.yaml` → tech-lead debe confirmar y anotar.
