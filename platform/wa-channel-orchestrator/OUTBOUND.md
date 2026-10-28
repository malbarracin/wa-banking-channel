# Publicación outbound (TASK-96)

`OutboundEventPublisher.publish(conversationId, correlationId, messageId, customerAddress, body)`
requiere una transacción activa (`MANDATORY`). Invocarlo desde el handler síncrono del turno
deduplicado, junto con estado/saga. No usar `@Async` ni `AFTER_COMMIT`. El llamador debe
proporcionar un messageId **saliente** estable y globalmente único y crear la conversación primero.
El esquema se valida antes de persistir. Solo se guarda el destinatario y texto exigidos por H0;
no agregar contexto de H9, credenciales ni datos de negocio. Los identificadores se limitan a
128 caracteres por la persistencia existente de conversación/outbox.

El scheduler invoca `OutboxDispatcher.dispatchNext()` en otra transacción. Lee la fila pendiente
más antigua con bloqueo pesimista hasta obtener el ACK de Kafka y confirmar `published_at`.
No lee filas no confirmadas. El despacho es serial global, incluso entre instancias, y un retry
pendiente detiene las siguientes filas: preserva orden a costa de throughput. No usar SKIP LOCKED.
La configuración existente del productor exige `acks=all`, idempotencia y máximo 5 requests en vuelo.
Los headers son correlationId/eventType/eventVersion y la clave es conversationId.
Se valida de nuevo el esquema y la correspondencia con la fila antes del envío.

Entrega at-least-once: si cae después del ACK y antes del commit, se reenvía con el mismo
eventId/messageId. H0 debe deduplicar por messageId; la idempotencia del productor Kafka no
resuelve por sí sola esta ventana entre base y broker.

Los intentos y el próximo vencimiento sobreviven reinicios. Backoff exponencial desde
OUTBOX_BACKOFF_MS con tope 60 s; OUTBOX_MAX_ATTEMPTS limita envíos outbound (default 3).
Un evento inválido no se reintenta. Agotamiento o validación envían un DeadLetter validado
a wa.message.dlq.v1. No se marca terminal hasta recibir ACK de DLQ; fallos de DLQ siguen
reintentándose con backoff, sin perder la fila. No se registran payloads ni excepciones remotas.
Métricas: orchestrator.outbox.published, failures y dlq; MDC correlationId se restaura al salir.

La DLQ lleva solo los headers `correlationId`, `eventType`, `eventVersion` y `errorCode`,
según el contrato H0.1. El `messageId` del sobre conserva el identificador saliente único
para ubicar la fila original en `outbox` por `message_id`, comprobando también
`conversationId` y `correlationId`.
`originalValueBase64` queda vacío por privacidad. Al no existir coordenadas Kafka previas al envío,
originalPartition/originalOffset son 0 como valores convencionales del esquema H0.1: **no son
coordenadas de reproceso** de esta outbox. Soporte debe buscar por `message_id` y revisar
el fallo antes de reactivar la fila en una transacción, limpiando dead_lettered_at/attempts/
next_attempt_at y conservando eventId/messageId. No republicar desde offset 0 ni copiar contenido a logs.
Configurar ACL de productor solo para ready-to-send y DLQ, TLS/SASL fuera de local.

Verificación: `./mvnw -q -B clean verify`; tests con tag integration requieren Docker.
OUTBOX_ENABLED permite detener el scheduler; intervalo, timeout y backoff se configuran
con las variables OUTBOX_* de .env.example. No elimina pendientes al detenerse.
