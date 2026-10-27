# ADR-002 — At-least-once + idempotencia como semántica de entrega

**Estado:** Aceptado
**Contexto:** Kafka ofrece at-least-once por defecto; exactly-once es costoso y no necesario si los consumidores son idempotentes.
**Decisión:** Semántica at-least-once en toda la plataforma; cada consumidor implementa idempotencia por clave de idempotencia acordada (`MessageSid` en H0, `messageId` en H0.2).
**Consecuencias:**
- Productores con `enable.idempotence=true` y `acks=all`.
- Consumidores commiten tras el efecto (`ackMode=MANUAL`).
- Reentregas no duplican efectos; se registra el duplicado cuando corresponda.
**Alternativas consideradas:** Exactly-once transactions — descartadas por complejidad y costo para el piloto.

**Aplicación en H0.1 (2026-10-27):** La idempotencia del productor evita duplicados
de reintentos de transporte; no deduplica reenvíos de negocio ni hace atómico Kafka con
una base externa o Twilio. KafkaEventConsumer exige que el efecto deduplique messageId
y persista su resultado en la misma transacción con restricción única antes de retornar.
Un duplicado retorna sin repetir efecto y se confirma; un fallo no se confirma.
Esta persistencia pertenece a H0/H0.2, no está implementada por la base H0.1.
H0.2 debe usar outbox para sus cambios de estado y publicación; un envío externo de H0
requiere su propia estrategia frente a un fallo entre envío y registro del resultado.

DefaultErrorHandler aplica tres reintentos adicionales, backoff inicial 1000 ms,
multiplicador 2 y techo 10000 ms (configurables). InvalidEventException va directamente
a DLQ. DeadLetterPublisher espera confirmación y propaga el fallo si no pudo recuperar.
El replay conserva messageId de negocio y trazabilidad; puede provocar nuevas entregas.
Las transacciones Kafka exactly-once no resolverían por sí solas los efectos externos.

**Verificación:** KafkaMessagingTest comprueba ACK, reentrega tras fallo transitorio y
fallos; KafkaMessagingIntegrationTest comprueba recuperación en Kafka. La deduplicación
persistente debe verificarse en cada consumidor. Ver [handoff](../../platform/wa-event-platform/handoff.md).
