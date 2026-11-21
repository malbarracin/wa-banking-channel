# Handoff — plataforma de eventos (`wa-event-platform`)

Contrato operativo para productores y consumidores del canal. Los esquemas viven en
`shared/contracts/events/`.

## Producir

- Clave: `conversationId`. Headers UTF-8: `correlationId`, `eventType`, `eventVersion` (iguales al sobre).
- Productor idempotente: `acks=all`, `enable.idempotence=true`.
- Validar el JSON contra el esquema del `eventType` **antes** de enviar. Inválido = no se publica.
- `eventVersion` del sobre debe coincidir con el sufijo `.v<n>` del topic.

## Consumir

- `enable.auto.commit=false`, `ackMode=MANUAL_IMMEDIATE` después del efecto.
- Una partición, un hilo: no paralelizar dentro de la partición (orden por conversación).
- Validar esquema al recibir; comparar headers con el sobre; `eventVersion` ↔ topic.
- Idempotencia: registrar `(groupId, messageId)` en la misma transacción que el efecto.

## Reintentos y DLQ

- Reintentos **bloqueantes** en el hilo de la partición:
  `DefaultErrorHandler` + `ExponentialBackOffWithMaxRetries(4)` → 0,5 s · 1 s · 2 s · 4 s
  (5 entregas, ~7,5 s). Sin topics de retry.
- No reintentables (van directo a `wa.message.dlq.v1`): parseo JSON, `SchemaValidationException`,
  desajuste headers/sobre, `eventVersion` ≠ topic.
- Reintentables: fallas transitorias (red, base de datos, 5xx).
- Headers en DLQ: `kafka_dlt-original-topic|partition|offset|timestamp`,
  `kafka_dlt-exception-fqcn`, `kafka_dlt-exception-message` (máx. 200, saneado),
  `x-dlq-attempts`, `x-dlq-failed-at`, más `correlationId` / `eventType` / `eventVersion` originales.
- **No** se publica `kafka_dlt-exception-stacktrace`; el stack queda solo en el log del consumidor con
  `correlationId`.

## Runbook — reproceso desde `wa.message.dlq.v1`

1. Identificar partición y rango de offsets (inclusivo / exclusivo). Revisar la causa en logs o headers
   (`kafka_dlt-exception-fqcn`, mensaje saneado). No volcar PII.
2. Si el evento es inválido por esquema: corregir el productor o descartar con registro de la decisión.
   **No** reprocesar en masa eventos inválidos.
3. Si la falla fue transitoria y ya está resuelta, desde `platform/wa-event-platform` con el classpath
   armado (`./mvnw -q -B package dependency:copy-dependencies`):

```sh
java -cp "target/classes:target/dependency/*" com.bcra.events.messaging.dlq.DlqReprocessor <partition> <start> <endExclusive>
```

En Windows usar `;` en el classpath. Argumentos: partición, offset inicial inclusivo, offset final
exclusivo (máximo 1000 posiciones por corrida).

4. La herramienta valida el rango completo contra el esquema **antes** de publicar nada. Republica
   clave y valor originales en `kafka_dlt-original-topic`, conserva headers de contrato y agrega
   `x-dlq-reprocessed-from` (`wa.message.dlq.v1:<partition>:<offset>`). **No borra** la DLQ.
5. Los consumidores deben ser idempotentes por `messageId`: un reproceso puede reentregar el mismo
   mensaje. Inspeccionar el topic de destino antes de repetir el rango.
