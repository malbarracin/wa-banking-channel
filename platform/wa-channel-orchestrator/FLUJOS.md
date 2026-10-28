# Flujos y contingencias (H0.2)

Diagramas del ciclo del mensaje tal como está implementado en este componente. Para los
puertos que todavía no tienen handler de producción se marca explícitamente el hueco:
nada de lo que sigue supone un recorrido completo H9 extremo a extremo.

## 1. Flujo implementado hoy (inbound + deduplicación)

```mermaid
sequenceDiagram
    autonumber
    participant TW as Twilio
    participant GW as H0 Gateway
    participant K as Kafka
    participant OR as H0.2 Orquestador
    participant DB as PostgreSQL

    TW->>GW: POST /webhook/twilio (form + firma)
    GW->>K: MessageReceived (clave conversationId, headers del sobre)
    GW-->>TW: 200 vacío o 503 para reintento
    K->>OR: wa.message.received.v1 (grupo wa-channel-orchestrator)
    OR->>OR: valida schema, formatos, headers y clave
    OR->>DB: INSERT message_dedupe ... ON CONFLICT DO NOTHING (misma TX)
    alt messageId ya registrado
        OR->>OR: log de duplicado (sin claves ni contenido)
    else messageId nuevo
        OR->>OR: publica InboundMessage (evento de aplicación síncrono)
        Note over OR: No hay listener de producción todavía:<br/>no invoca H9, ni saga, ni outbox.
    end
    OR-->>K: ACK manual tras el commit
```

Puntos que no se pueden cambiar sin romper garantías:

- La clave de Kafka debe ser `conversationId`; el validador rechaza otra clave.
- El commit de `message_dedupe` y el ACK están separados: si el efecto falla, no hay ACK y la
  deduplicación se revierte; una reentrega posterior al commit se confirma sin repetir el efecto.
- La deduplicación es global por `messageId` (V6 admite hasta 256 caracteres), incluso entre conversaciones.

## 2. Flujo objetivo (handler de turno pendiente)

Este es el recorrido que falta conectar. Se dibuja para que el handler respete la transacción
ya existente; no describe código presente.

```mermaid
sequenceDiagram
    autonumber
    participant OR as H0.2 Orquestador
    participant DB as PostgreSQL
    participant H9 as H9 Agente
    participant K as Kafka

    Note over OR: @EventListener síncrono de InboundMessage<br/>(pendiente de implementación)
    OR->>DB: ConversationService.loadOrCreate (INITIAL o estado persistido)
    OR->>H9: POST /api/v1/agent/turn (X-Idempotency-Key=messageId)
    alt 200 con identificadores correctos
        H9-->>OR: reply.body, escalate, toolsUsed
        OR->>DB: SagaOrchestrator.start/signal y conversation_state
        OR->>DB: OutboundEventPublisher.publish (TX MANDATORY, outbox)
    else TIMEOUT / UNAVAILABLE
        H9-->>OR: H9TurnException (reintentos con la misma clave)
        OR->>DB: contingencia de la saga (signal FAILED) o DLQ/soporte
    else 4xx / 409 / respuesta inválida
        H9-->>OR: REJECTED o INVALID_RESPONSE (sin reintento)
        OR->>DB: contingencia o soporte, sin inventar respuesta
    end
    Note over OR,K: El handler no debe usar @Async ni AFTER_COMMIT:<br/>estado, saga y outbox comparten la TX deduplicada.
```

## 3. Outbox: publicación, reintentos y DLQ

```mermaid
sequenceDiagram
    autonumber
    participant SCH as OutboxScheduler
    participant DIS as OutboxDispatcher
    participant DB as PostgreSQL
    participant K as Kafka

    SCH->>DIS: dispatchNext() cada OUTBOX_POLL_INTERVAL_MS
    DIS->>DB: fila más antigua pending (published_at y dead_lettered_at NULL), con bloqueo
    alt schema o metadatos inválidos
        DIS->>K: wa.message.dlq.v1 (errorCode=SCHEMA_VALIDATION_FAILED)
        DIS->>DB: marca dead_lettered_at tras ACK de DLQ
    else intentos agotados (attempts >= OUTBOX_MAX_ATTEMPTS)
        DIS->>K: wa.message.dlq.v1 (errorCode=RETRIES_EXHAUSTED)
        DIS->>DB: marca dead_lettered_at tras ACK de DLQ
    else envío confirmado
        DIS->>K: wa.message.ready-to-send.v1 (clave conversationId, headers)
        DIS->>DB: published_at y commit
    else error de envío o de DLQ
        DIS->>DB: attempts+1 y next_attempt_at con backoff exponencial
    end
```

El despacho es serial global: mientras una fila espera reintento, las siguientes no avanzan
(se preserva el orden por conversación). Una caída entre el ACK de Kafka y el commit reenvía
el mismo `eventId`/`messageId`: H0 debe deduplicar. `OUTBOX_ENABLED=false` detiene el scheduler
sin borrar pendientes.

## 4. Inbound: errores, reintentos y DLQ

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka
    participant OR as InboundEventConsumer
    participant ERR as DefaultErrorHandler
    participant DLQ as SafeDeadLetterRecoverer

    K->>OR: registro
    OR->>OR: validación de schema/headers/clave
    alt inválido (InvalidEventException / ValidationException)
        OR-->>ERR: error no reintentable
        ERR->>DLQ: DeadLetter (errorCode=SCHEMA_VALIDATION_FAILED)
        DLQ->>K: wa.message.dlq.v1 (headers seguros)
        ERR->>K: confirma el registro original (commitRecovered)
    else error transitorio
        OR-->>ERR: excepción
        ERR->>OR: reintenta con backoff (máx. 3, 1 s → 10 s)
        ERR->>DLQ: al agotar, DeadLetter (errorCode=RETRIES_EXHAUSTED)
    end
```

El DLQ de inbound no conserva el valor original: `originalValueBase64` queda vacío y
`conversationId`/`messageId` del sobre derivan de `topic:partition:offset`. La recuperación
usa esas coordenadas para releer el registro original (ver [DLQ.md](DLQ.md)).

## 5. Máquinas de estado

Las sagas y sus compensaciones están en [SAGAS.md](SAGAS.md). La conversación usa
`conversation_state.state` (`INITIAL` al crear; luego el estado técnico que fije el handler)
con versión optimista y `last_message_id`.

## 6. Contingencias

| Situación | Respuesta del componente | Evidencia persistida / visible |
|---|---|---|
| Schema, headers o clave inválidos | DLQ sin reintentos | `errorCode=SCHEMA_VALIDATION_FAILED` en DLQ; métrica `orchestrator.kafka.dlq.published` |
| Error transitorio inbound | 3 reintentos con backoff; luego DLQ | `errorCode=RETRIES_EXHAUSTED`; sin ACK hasta resolver |
| `messageId` duplicado | ACK sin repetir efectos | fila existente en `message_dedupe`; log `Duplicate inbound ignored` |
| H9 timeout o 5xx | Reintenta con la misma `X-Idempotency-Key`; luego `TIMEOUT`/`UNAVAILABLE` | log con `outcome` y duración; sin cuerpos ni tokens |
| H9 4xx, 409 o 429 | `REJECTED`, sin reintento | excepción de dominio segura; no se publica outbound |
| Respuesta H9 incompleta o de otro turno | `INVALID_RESPONSE` | no se interpreta como respuesta válida |
| Falta `H9_SERVICE_TOKEN` | `NOT_CONFIGURED` antes de enviar | no sale request con datos |
| Paso de saga falla o vence | `COMPENSATING` en orden inverso, incluido el paso incierto | `saga_step_execution` con `SUPPORT_REQUIRED` si no se confirma |
| Compensación sin adaptador o rechazada | `FAILED` (no se afirma reversión) | `result_code='SUPPORT_REQUIRED'` |
| Outbox agota intentos o schema inválido | DLQ y fila terminal | `dead_lettered_at`; métricas `outbox.dlq`/`outbox.failures` |
| Falla el envío a DLQ desde outbox | Reintenta la fila con backoff; no la pierde | `attempts`/`next_attempt_at` en `outbox` |
| Reinicio del proceso | Estado, sagas, intentos y pendientes sobreviven | tablas Flyway; `WAITING` se puede continuar |
| Duplicado de salida en H0 | H0 deduplica por `messageId` | reentrega at-least-once documentada en [OUTBOUND.md](OUTBOUND.md) |

## 7. Trazabilidad

`correlationId` viaja en el header del evento inbound, en MDC mientras se procesa, en los
headers del DLQ y en las llamadas a H9. Todos los bloques que toman la correlación restauran
el valor previo del MDC al salir. Los logs son ECS y no incluyen cuerpos, teléfonos, tokens
ni mensajes de excepción remotos.
