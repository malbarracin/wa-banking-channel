# Contratos de eventos v1

JSON Schema draft-07. Los cuatro archivos `wa.message.*.v1.schema.json` son
autocontenidos y usan el sobre definido en `event-envelope.v1.schema.json`.
El sobre y cada payload rechazan propiedades desconocidas (`additionalProperties: false`).
UUID y fechas RFC 3339 se validan con comprobación de formatos habilitada.

## Catálogo

| Topic | eventType | eventVersion | Clave Kafka | Payload obligatorio | Retención (local) | Productor | Consumidor |
| --- | --- | --- | --- | --- | --- | --- | --- |
| wa.message.received.v1 | MessageReceived | 1 | conversationId | customerAddress, body | 7 días | H0 (Gateway Twilio) | H0.2 (Orquestador) |
| wa.message.ready-to-send.v1 | MessageReadyToSend | 1 | conversationId | customerAddress, body | 7 días | H0.2 (Orquestador) | H0 (Gateway Twilio) |
| wa.message.status.v1 | MessageStatus | 1 | conversationId | status | 30 días | H0 (Gateway Twilio) | H0.2 / observabilidad |
| wa.message.dlq.v1 | DeadLetter | 1 | conversationId | originalTopic, originalPartition, originalOffset, originalValueBase64, errorCode | 90 días | Infraestructura (ErrorHandler) | Soporte |

`customerAddress` es la dirección mínima de entrega que necesita el gateway;
`body` contiene solo el texto del turno. No incluir documentos, credenciales,
saldos ni payloads del proveedor. No registrar estos campos en logs.
`errorCode` de estado es opcional y contiene solo un código, nunca texto de excepción.

## Sobre común (EventEnvelope)

Todos los eventos comparten la estructura del sobre:

```json
{
  "eventId": "e7a62f43-9b82-4c01-b2ab-2c8e7d904321",
  "eventType": "MessageReceived",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T12:00:00Z",
  "correlationId": "b7f3c8a2-9e4d-4b02-8a1c-7d62e8ab9045",
  "conversationId": "conv-123456",
  "messageId": "msg-abcdef",
  "payload": { }
}
```

- `eventId`: UUID único del evento.
- `eventType`: debe coincidir con el tipo esperado por schema/topic.
- `eventVersion`: siempre `1` para v1.
- `occurredAt`: timestamp RFC3339 en UTC.
- `correlationId`: UUID para trazabilidad punta a punta (propagado en headers Kafka).
- `conversationId`: clave de partición (String UTF-8, 1..256 chars). Garantiza orden por conversación.
- `messageId`: identificador único del mensaje (1..256 chars). Usado para deduplicación.
- `payload`: objeto según tipo de evento (sin propiedades desconocidas).

## Ejemplos por evento

### MessageReceived (wa.message.received.v1)
```json
{
  "eventId": "9c7b42a8-81e6-4d02-bf43-902ab761e854",
  "eventType": "MessageReceived",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T12:00:00Z",
  "correlationId": "7a2e8b91-4c63-4f02-9d81-6ab743e90215",
  "conversationId": "conv-123456",
  "messageId": "SM1234567890",
  "payload": {
    "customerAddress": "whatsapp:+5491112345678",
    "body": "Hola, necesito consultar mi saldo"
  }
}
```

### MessageReadyToSend (wa.message.ready-to-send.v1)
```json
{
  "eventId": "62ab874e-9d13-4c02-8f71-2e943ac81572",
  "eventType": "MessageReadyToSend",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T12:01:00Z",
  "correlationId": "7a2e8b91-4c63-4f02-9d81-6ab743e90215",
  "conversationId": "conv-123456",
  "messageId": "out-987654",
  "payload": {
    "customerAddress": "whatsapp:+5491112345678",
    "body": "Su saldo es $1234.56"
  }
}
```

### MessageStatus (wa.message.status.v1)
```json
{
  "eventId": "81f762ab-943d-4c02-8e17-5ac892b74361",
  "eventType": "MessageStatus",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T12:02:00Z",
  "correlationId": "7a2e8b91-4c63-4f02-9d81-6ab743e90215",
  "conversationId": "conv-123456",
  "messageId": "SM1234567890",
  "payload": {
    "status": "delivered"
  }
}
```

### DeadLetter (wa.message.dlq.v1)
```json
{
  "eventId": "ab7628f9-41d3-4c02-9e87-123ac965bf42",
  "eventType": "DeadLetter",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T12:05:00Z",
  "correlationId": "7a2e8b91-4c63-4f02-9d81-6ab743e90215",
  "conversationId": "conv-123456",
  "messageId": "wa.message.received.v1:0:12345",
  "payload": {
    "originalTopic": "wa.message.received.v1",
    "originalPartition": 0,
    "originalOffset": 12345,
    "originalValueBase64": "eyJldmVudElkIjoi...",
    "errorCode": "RETRIES_EXHAUSTED"
  }
}
```

## Transporte

Clave Kafka: `conversationId`. Headers UTF-8 obligatorios: `correlationId`,
`eventType`, `eventVersion` (cadena `1`), iguales a los campos del sobre.
El consumidor debe verificar también clave y headers: JSON Schema valida el valor
JSON, no los metadatos del registro Kafka.

## Validación y errores

`EventSchemaValidator.validate(topic, json)` en `platform/wa-event-platform`
implementa la validación con networknt. Maven empaqueta directamente los schemas
de esta carpeta en `contracts/events`, sin copias mantenidas a mano.
Ejecutar `mvnw.cmd -q -B clean verify` desde el componente (Linux: `./mvnw`).

El productor debe validar antes del envío: `InvalidEventException` impide publicar.
El consumidor debe validar antes de ejecutar efectos. Su error handler debe declarar
`addNotRetryableExceptions(InvalidEventException.class)` y recuperar inmediatamente
en `wa.message.dlq.v1`, sin reintentar validación.

## Política de reintentos y DLQ

- **Errores no reintentables**: `InvalidEventException` (fallas de schema/validación) → van directo a DLQ, sin reintentos.
- **Errores reintentables**: otros fallos (transitorios) → reintentos con backoff exponencial (configurables vía `KAFKA_CONSUMER_*`), hasta agotar reintentos configurados.
- **DLQ**: `wa.message.dlq.v1` con sobre `DeadLetter` válido. Conserva `originalValueBase64` (exacto), `originalTopic/partition/offset`. Solo headers permitidos: `correlationId`, `eventType`, `eventVersion`, `errorCode`. Sin stack traces ni mensajes de excepción.
- **Seguridad DLQ**: Base64 no cifra. Requiere ACL restringida, cifrado en tránsito y retención limitada. No reenviar fallos de la propia DLQ hacia ella (detener y alertar).
- **Confirmación**: confirmar registro original solo tras envío confirmado a DLQ; si falla el envío a DLQ, conservar offset para recuperación sin perder el evento.

## Reproceso de DLQ (guía para soporte)

1. **Autorizar**: registrar autorización operativa, coordenadas originales y `correlationId` en el incidente, sin copiar payloads sensibles a tickets/logs.
2. **Inspeccionar**: leer DeadLetter con acceso restringido. Decodificar `originalValueBase64` y validar JSON contra schema de `originalTopic`.
3. **Preparar**: reconstruir clave `conversationId` y headers `correlationId/eventType/eventVersion` desde el sobre original validado. Conservar `messageId` de negocio para deduplicación. Si fue necesario reparar el sobre, registrar relación.
4. **Reenviar**: publicar via `KafkaEventProducer` al topic original, esperando confirmación del futuro. **Nunca** publicar el sobre DeadLetter en topic de negocio ni reenviar a la propia DLQ.
5. **Verificar**: confirmar efecto o duplicado en el consumidor. Registrar resultado y coordenadas. Un replay puede quedar detrás de mensajes recientes (no restaura orden histórico). **No** borrar registros ni mover offsets del grupo de negocio.

**Notas importantes:**
- Si el original es ilegible, generar UUID nuevos y usar identificadores técnicos de recuperación; conservar originales solo si válidos.
- Los consumidores **deben** deduplicar por `messageId`; el reproceso puede repetir entregas.
- El `messageId` técnico del DeadLetter (`topic:partition:offset`) no reemplaza el `messageId` de negocio.

## Evolución

Un cambio incompatible requiere nueva versión y topic, con publicación paralela
hasta migrar consumidores. Un campo opcional exige coordinar el despliegue del
schema con consumidores que rechazan propiedades desconocidas.
