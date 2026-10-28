---
title: Orquestador del Canal (H0.2) — arquitectura
status: borrador
source: specs/orquestador-canal/spec.md
updated: 2026-10-28
---

# Arquitectura — Orquestador del Canal (H0.2)

## Base

- **Componente nuevo**: `platform/wa-channel-orchestrator`
- **Java 21 + Spring Boot 3.x**, Maven con wrapper propio (`./mvnw`)
- **GroupId**: `com.bcra` (de `project.yaml` → `java.group_id`)
- **Base package**: `com.bcra.orchestrator` (propuesto; si falta en `project.yaml` bajo `integracion.orquestador.base_package`, se anota para `tech-lead`)
- **Puerto**: `8091` (según convención; confirmar en `project.yaml` si existe `integracion.ports.orquestador`)
- **Persistencia**: PostgreSQL + Spring Data JPA + Flyway
- **Mensajería**: Spring for Apache Kafka (mismo patrón que gateway/plataforma)

## Límites (según int-boundaries.md)

| Hace | No hace |
|---|---|
| Consumir inbound, deduplicar por `messageId`, serializar por `conversationId`, estado/contexto técnico, invocar H9, timeouts/reintentos, sagas (onboarding/re-verificación), publicar outbound | Payload de Twilio, transporte Kafka, reglas de negocio H1–H6/H3/H2, entender lenguaje, implementar herramientas MCP, lógica de negocio de dominio |

## Árbol de paquetes

```text
com.bcra.orchestrator/
├── OrchestratorApplication.java
├── web/                    # endpoints internos/health si aplica
├── config/                 # Kafka, JPA, WebClient/RestTemplate, Observability
├── messaging/
│   ├── consumer/           # InboundEventConsumer (wa.message.received.v1)
│   ├── producer/           # OutboundEventPublisher (wa.message.ready-to-send.v1)
│   └── config/             # Kafka consumer/producer config
├── dedup/                  # DedupeService + store (JPA) por messageId
├── conversation/
│   ├── model/              # ConversationState (entidad)
│   ├── repository/         # JPA repos
│   └── service/            # ConversationService (carga/inicializa/actualiza)
├── saga/
│   ├── model/              # SagaState, SagaStep, estados/transiciones
│   ├── orchestrator/       # SagaOrchestrator
│   ├── actions/            # acciones por paso
│   └── compensation/       # compensaciones explícitas
├── agent/
│   ├── client/             # H9 client (WebClient/Rest) según contrato
│   └── dto/                # DTOs del contrato wa-conversation-agent/turn
├── outbox/                 # Outbox pattern si publica tras commit (recomendado)
├── persistence/            # entidades, migraciones Flyway
├── security/               # configuración
└── observability/          # CorrelationIdFilter, Metrics
```

## Contratos y topics

Sigue `shared/contracts/events/*` y reglas de H0.1:

| Topic | Productor | Consumidor | Evento | Notas |
|---|---|---|---|---|
| `wa.message.received.v1` | H0 | H0.2 | `MessageReceived` | clave `conversationId`, headers `correlationId,eventType,eventVersion`. Validar esquema al recibir. |
| `wa.message.ready-to-send.v1` | H0.2 | H0 | `MessageReadyToSend` | publicar con clave `conversationId`, headers obligatorios. |
| `wa.message.status.v1` | H0 | H0.2/obs | `MessageStatus` | trazabilidad/seguimiento. |
| `wa.message.dlq.v1` | Infra/errores | Soporte | `DeadLetter` | reintentos agotados/errores no reintentables según política. |

**Contrato H9**: `shared/contracts/wa-conversation-agent/turn.md` (cuando exista). Propagar `correlationId` en headers/MDC extremo a extremo.

## Configuración Kafka

- **Productor**: `acks=all`, `enable.idempotence=true`, `max.in.flight.requests.per.connection=5`, retry configurado. Usar outbox si se publica tras cambiar estado transaccionalmente.
- **Consumidor**: `ackMode=MANUAL_IMMEDIATE` o `MANUAL` tras completar efecto/idempotencia. `concurrency ≤ particiones` (≥3). `max.poll.records` acotado. Validar esquema; inválidos → DLQ sin reintento.
- **Orden**: clave `conversationId`. No procesar en paralelo dentro de misma partición/conversación (usar `ConcurrentKafkaListenerContainerFactory` con concurrencia limitada o `@KafkaListener` con `concurrency` acorde + lógica de serialización).
- **Reintentos/DLQ**: `DefaultErrorHandler` con `DeadLetterPublishingRecoverer`, backoff exponencial, errores de validación no reintentables.

## Idempotencia y deduplicación

- **Clave deduplicación**: `messageId` (de `MessageReceived`). 
- **Almacenamiento**: tabla `orchestrator_message_dedupe` con `messageId` UNIQUE, `conversationId`, `correlationId`, `processed_at`, `event_type`. Insert-if-absent en transacción al inicio del procesamiento. En reentrega duplicada → log con trazabilidad y **no reprocesar** efecto de negocio.
- **Efectos transaccionales**: cambiar estado de conversación/saga y registrar deduplicación en misma transacción cuando aplique; usar outbox para publicación.
- **Orden por conversación**: asegurar serialización por `conversationId` (clave de partición + control de concurrencia).

## Máquina de estados / Saga

**Entidades principales**:
- `Conversation` / `ConversationState`: estado actual, `correlationId`, `conversationId`, `customerAddress` (mínimo), flags, timestamps.
- `SagaInstance`: `sagaId` (UUID), `conversationId`, `type` (ONBOARDING|REVERIFICATION), `status` (PENDING|IN_PROGRESS|WAITING|COMPLETED|FAILED|COMPENSATING|COMPENSATED), `currentStep`, `context` (JSON), `timeoutAt`, `version` (optimistic locking).
- `SagaStepExecution`: trazabilidad por paso, reintentos, resultado.

**Principios**:
- Estados y transiciones explícitas, persistidas con Flyway.
- Timeouts por paso/flujo con scheduler (`@Scheduled`/`TaskScheduler`) para detectar expirados.
- Compensaciones explícitas por paso con capacidad de fallo.
- Pausa/reanudación: estado persistido permite reanudar tras reinicio.
- Evitar estado desconocido vía timeouts y manejo de errores.

**Flujos piloto**:
- **Onboarding**: coordinar con H3/H2 según necesidad, sin duplicar reglas; puede pausar esperando input/validación.
- **Re-verificación**: similar, con compensaciones si falla paso intermedio.

## Invocación a H9

- Cliente HTTP/WebClient hacia H9 siguiendo `shared/contracts/wa-conversation-agent/turn.md`.
- Propagar `correlationId`, `conversationId`, contexto mínimo necesario.
- Timeouts configurables (connect/read) + reintentos con backoff (no reintentar en ciertos errores). En timeout → aplicar política (retry o derivación controlada) sin dejar flujo en estado desconocido.
- Trazabilidad completa de request/response (sin PII innecesaria en logs).
- Si H9 solicita capacidades (tools MCP H7), coordinar según contrato con trazabilidad/timeouts.

## Publicación outbound

- Publicar `MessageReadyToSend` a `wa.message.ready-to-send.v1` con clave `conversationId`, headers `correlationId,eventType,eventVersion`.
- Validar esquema antes de publicar. Sin PII innecesaria.
- Fallos de publicación → reintentos/DLQ según política H0.1.
- Preferir outbox pattern para garantizar publicación tras commit transaccional.

## Seguridad

- Sin secretos/PII innecesaria en eventos/logs. Enmascarar teléfonos en logs.
- Validación estricta de eventos entrantes; inválidos → DLQ sin reintento.
- Propagación de `correlationId` extremo a extremo.
- Errores sin detalles técnicos sensibles hacia clientes/externo.
- TLS/SASL en Kafka fuera de local (documentado).
- Configuración vía variables de entorno (solo `.env.example` commiteado).

## Observabilidad

- `correlationId` en MDC y headers Kafka, propagado de punta a punta.
- Métricas: mensajes consumidos/duplicados, latencia por paso, invocaciones H9, timeouts, sagas activas/completadas/fallidas, DLQ, publicaciones outbound.
- Actuator: `health`, `info`, `prometheus`. Logs JSON estructurados.
- Auditoría mínima de transiciones de saga.

## Configuración (variables propuestas)

| Variable | Requerida | Descripción |
|---|---|---|
| `SERVER_PORT` | no | default `8091` |
| `SPRING_DATASOURCE_*` | sí | PostgreSQL |
| `KAFKA_BOOTSTRAP_SERVERS` | sí | Bootstrap Kafka |
| `KAFKA_CONSUMER_*`, `KAFKA_PRODUCER_*` | según | idempotencia/acks/ackMode/concurrencia |
| `H9_BASE_URL` | sí | URL de agente H9 |
| `H9_TIMEOUT_MS` | no | timeout invocación H9 |
| `SAGA_TIMEOUT_CHECK_INTERVAL_MS` | no | intervalo scheduler timeouts |
| `ORCHESTRATOR_DEDUPE_TTL_HOURS` | no | TTL deduplicación (si aplica) |

## Flyway migrations (iniciales)

- `V1__create_conversation_state.sql` - estado de conversación
- `V2__create_saga_instance.sql` - instancia de saga con versionado
- `V3__create_saga_step_execution.sql` - trazabilidad de pasos
- `V4__create_message_dedupe.sql` - deduplicación por messageId (UNIQUE)
- `V5__create_outbox.sql` (opcional pero recomendado) - outbox para publicación transaccional

## ADRs requeridos

- `docs/adr/ADR-006-orquestador-dedicado-ciclo-vida-canal.md`
- `docs/adr/ADR-007-estado-conversacion-saga-persistente.md`
- `docs/adr/ADR-008-limite-orquestacion-vs-conversacion-h9.md`

## Riesgos / decisiones pendientes (alineados a preguntas abiertas)

- **Clave deduplicación**: usar `messageId` exclusivamente; persistir en `message_dedupe` con UNIQUE. Confirmar si se necesita combinación con `conversationId` en algún caso.
- **Timeouts/reintentos H9**: definir valores por entorno (timeouts, max reintentos, backoff). Política clara para timeout vs error vs derivación.
- **Estados mínimos saga**: definir máquina de estados para ONBOARDING/REVERIFICATION con transiciones válidas y compensaciones explícitas.
- **DLQ vs reintento vs soporte**: criterios por tipo de error (validación → DLQ sin reintento; transitorio → reintento; irrecuperable de negocio → derivación/compensación con trazabilidad).
- **Contexto técnico persistido**: mínimo necesario (correlationId, conversationId, customerAddress enmascarado, flags técnicos, estado saga). Evitar PII; definir TTL si aplica.

## Verificación

- `./mvnw verify` debe pasar con JaCoCo ≥ 80%
- Tests: unitarios (dedup, saga, orquestación), integración con Testcontainers (Kafka + PostgreSQL), contratos
- Validación de esquemas en productor/consumidor
- Comprobación de idempotencia ante reentregas
- Comprobación de orden por conversación
- Timeouts/compensaciones cubiertos
