# Handoff — plataforma de eventos (`wa-event-platform`)

Contrato operativo para el gateway (H0) y el orquestador (H0.2). Los esquemas JSON Schema y el
catálogo de topics viven solo en [`../events/`](../events/). Este componente no publica una librería
Java compartida: cada vecino valida y serializa contra esos archivos.

| Topic | `eventType` | Productor | Consumidor | `group.id` |
| --- | --- | --- | --- | --- |
| `wa.message.received.v1` | `MessageReceived` | Gateway | Orquestador | `wa-channel-orchestrator` |
| `wa.message.ready-to-send.v1` | `MessageReadyToSend` | Orquestador | Gateway | `wa-twilio-gateway` |
| `wa.message.status.v1` | `MessageStatus` | Gateway | Orquestador / observabilidad | `wa-channel-orchestrator` |
| `wa.message.dlq.v1` | el del evento original | consumidor que agota reintentos | soporte (reproceso) | — |

Detalle de particiones, retención y DLQ: [`../events/topics.md`](../events/topics.md).

## Sobre, headers y clave

Valor del registro: JSON UTF-8 como texto (`StringSerializer` / `StringDeserializer`). El `eventType`
del sobre decide el esquema; `FAIL_ON_UNKNOWN_PROPERTIES=true`. Prohibida la deserialización
polimórfica y `spring.json.trusted.packages=*`.

```json
{
  "eventId": "9f1c1a2e-5b7d-4c3a-8f21-0a1b2c3d4e5f",
  "eventType": "MessageReceived",
  "eventVersion": 1,
  "occurredAt": "2026-11-21T14:03:12.482Z",
  "correlationId": "3b0f8e14-7c2a-4d55-9e01-11b22c33d44e",
  "conversationId": "conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b",
  "messageId": "SM1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6",
  "payload": { }
}
```

| Pieza | Regla |
| --- | --- |
| Clave Kafka | `conversationId` (string, nunca nula). Preserva el orden por conversación. |
| Headers UTF-8 (obligatorios) | `correlationId`, `eventType`, `eventVersion` — deben coincidir con el sobre. |
| `eventVersion` | Igual al sufijo `.v<n>` del topic y a la versión del esquema. |
| `messageId` | Clave de deduplicación de negocio (ver Idempotencia). |
| `correlationId` | Nace en el gateway al recibir el webhook y se propaga sin cambios (headers + MDC). |

Esquemas: [`envelope.v1.schema.json`](../events/envelope.v1.schema.json),
[`message-received.v1.schema.json`](../events/message-received.v1.schema.json),
[`message-ready-to-send.v1.schema.json`](../events/message-ready-to-send.v1.schema.json),
[`message-status.v1.schema.json`](../events/message-status.v1.schema.json). Copiar el catálogo al
classpath en el build (`../../shared/contracts/events` → `contracts/events`) y cargar desde
`classpath:contracts/events/*.schema.json`.

## Producir

Configuración mínima del productor:

```
acks=all
enable.idempotence=true
max.in.flight.requests.per.connection=5
retries=2147483647
delivery.timeout.ms=120000
request.timeout.ms=30000
compression.type=lz4
key.serializer=org.apache.kafka.common.serialization.StringSerializer
value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

Secuencia al publicar:

1. Completar `eventId` y `occurredAt` si faltan; exigir `correlationId`, `conversationId` y `messageId`.
2. Verificar que `eventVersion` coincida con el sufijo `.v<n>` del topic.
3. Serializar a JSON y **validar contra el esquema del `eventType`**. Inválido = no se publica
   (bug del productor).
4. Enviar con clave `conversationId` y los tres headers.
5. No hacer fire-and-forget: confirmar con timeout cuando el flujo lo exige (p. ej. ACK del webhook)
   o registrar el fallo y contarlo.

Quién publica qué: el gateway genera `correlationId` y `conversationId`, publica `MessageReceived`
(`messageId` = MessageSid del proveedor) y `MessageStatus`; el orquestador publica
`MessageReadyToSend` (`messageId` propio, `replyToMessageId` del inbound) y propaga el
`correlationId` sin cambiarlo.

## Consumir

Configuración mínima del consumidor:

```
enable.auto.commit=false
auto.offset.reset=earliest
max.poll.records=10
ackMode=MANUAL_IMMEDIATE
concurrency ≤ particiones del topic (3 en local)
key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

Orden: una conversación cae siempre en la misma partición y una partición la procesa un solo hilo.
**Prohibido** paralelizar dentro de la partición (sin pool por registro, sin `@Async` en el listener).

En cada recepción:

1. Parsear el JSON y validar contra el esquema del `eventType`.
2. Comparar headers con el sobre; rechazar desajustes y `eventVersion` que no corresponda al topic.
3. Poner `correlationId`, `conversationId`, `messageId` y `eventType` en el MDC; limpiar al salir.
4. Aplicar el efecto de negocio y **recién entonces** confirmar el offset (`MANUAL_IMMEDIATE`).
5. Ante fallo: reintentos / DLQ según la política de abajo.

## Idempotencia

Entrega at-least-once: **todo consumidor es idempotente**.

| Caso | Clave | Comportamiento |
| --- | --- | --- |
| Reentrega del mismo registro (rebalanceo, reinicio) | `eventId` | Cortar temprano y confirmar. |
| Mismo mensaje publicado dos veces (p. ej. reintento del webhook) | `messageId` | Confirmar sin repetir el efecto. |

Contrato: registrar `(groupId, messageId)` como procesado **en la misma transacción que el efecto**,
con restricción única; si la inserción choca, es duplicado → confirmar sin repetir.

- Orquestador: tabla en PostgreSQL con `UNIQUE (consumer_group, message_id)`.
- Gateway: store de MessageSid (inbound) y de `messageId` (outbound), TTL ≥ 24 h.
- El store en memoria de la plataforma es solo referencia: no sobrevive a un reinicio y no sirve
  para un consumidor de producción.

## Reintentos y DLQ

Reintentos **bloqueantes** en el hilo de la partición (sin topics de retry), para no romper el orden
por conversación:

```
DefaultErrorHandler + ExponentialBackOffWithMaxRetries(4)
  intervalo inicial 500 ms · multiplicador 2.0 · intervalo máximo 8 s
  → 0,5 s · 1 s · 2 s · 4 s  (5 entregas en total, ~7,5 s)
DeadLetterPublishingRecoverer → wa.message.dlq.v1
```

| Tipo | Destino |
| --- | --- |
| No reintentables (parseo JSON, fallo de esquema, desajuste headers/sobre, `eventVersion` ≠ topic, violaciones de contrato del consumidor) | Directo a `wa.message.dlq.v1` |
| Reintentables (red, base de datos, 5xx de un servicio) | Backoff; al agotar → DLQ |

La DLQ conserva clave y bytes del registro original (aunque no sea JSON válido) y agrega headers de
falla. No tiene esquema de payload ni se valida al publicarse.

| Header | Contenido |
| --- | --- |
| `kafka_dlt-original-topic` / `-original-partition` / `-original-offset` / `-original-timestamp` | Origen |
| `kafka_dlt-exception-fqcn` | Clase de la excepción |
| `kafka_dlt-exception-message` | Mensaje truncado a 200 caracteres y saneado |
| `x-dlq-attempts` | Entregas consumidas antes de rendirse |
| `x-dlq-failed-at` | ISO-8601 UTC |
| `correlationId`, `eventType`, `eventVersion` | Los del evento original, intactos |

No se publica `kafka_dlt-exception-stacktrace`. El detalle técnico queda en el log del consumidor con
`correlationId`. Sin secretos ni datos personales innecesarios en headers ni logs (teléfonos
enmascarados: `+` + 3 primeros dígitos + `*` + 2 últimos).

## Runbook — reproceso desde `wa.message.dlq.v1`

Herramienta: `com.bcra.events.messaging.dlq.DlqReprocessor` en `platform/wa-event-platform`.

1. Identificar partición y rango de offsets (inicio inclusivo, fin exclusivo). Revisar la causa en
   logs o headers (`kafka_dlt-exception-fqcn`, mensaje saneado). No volcar datos personales.
2. Si el evento es inválido por esquema: corregir el productor o descartar con registro de la
   decisión. **No** reprocesar en masa eventos inválidos.
3. Si la falla fue transitoria y ya está resuelta, desde `platform/wa-event-platform` con el
   classpath armado (`./mvnw -q -B package dependency:copy-dependencies`):

```sh
java -cp "target/classes:target/dependency/*" com.bcra.events.messaging.dlq.DlqReprocessor <partition> <start> <endExclusive>
```

En Windows usar `;` en el classpath. Argumentos: partición, offset inicial inclusivo, offset final
exclusivo (máximo 1000 posiciones por corrida). La configuración de Kafka sale de las variables de
entorno del componente (ver `.env.example` en esa carpeta).

4. La herramienta hace dos pasadas: recoge el rango, valida **todo** el rango contra el esquema
   (headers de contrato + valor) y recién entonces publica. Si algún registro es inválido, **no
   publica ninguno**. Republica clave y valor originales en `kafka_dlt-original-topic`, conserva
   `correlationId` / `eventType` / `eventVersion` y agrega `x-dlq-reprocessed-from`
   (`wa.message.dlq.v1:<partition>:<offset>`). **No borra** la DLQ.
5. Los consumidores deben ser idempotentes por `messageId`: un reproceso puede reentregar el mismo
   mensaje. Inspeccionar el topic de destino antes de repetir el rango.

## Checklist para H0 / H0.2

- [ ] Schemas de `shared/contracts/events/` en el classpath; validación al producir y al consumir.
- [ ] Productor con `acks=all` + idempotencia; clave `conversationId`; tres headers alineados al sobre.
- [ ] Consumidor con ack manual después del efecto; sin paralelismo dentro de la partición.
- [ ] Idempotencia transaccional por `(groupId, messageId)`.
- [ ] Misma política de reintentos bloqueantes y DLQ; sin header de stack trace en DLQ.
- [ ] `correlationId` de punta a punta; teléfonos enmascarados en logs; sin secretos en eventos.
