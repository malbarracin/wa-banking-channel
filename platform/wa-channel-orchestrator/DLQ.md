# DLQ y reproceso

## Identificar y corregir

Soporte autorizado consulta DeadLetter en wa.message.dlq.v1 con correlationId/errorCode.
Headers únicos: correlationId/eventType/eventVersion/errorCode. No hay source/outboxEventId.
originalValueBase64 está vacío por privacidad. Corregir causa antes de reactivar:
SCHEMA_VALIDATION_FAILED requiere revisar contrato/productor; RETRIES_EXHAUSTED, disponibilidad/configuración.
No copiar payloads a logs ni tickets. Usar TLS/SASL y ACL de soporte fuera de local.

## Inbound — originalTopic received

Recuperar registro por payload.originalTopic/originalPartition/originalOffset con herramientas
Kafka autorizadas, lectura asignada sin alterar offsets del grupo de producción. Comprobar offset exacto.
El conversationId/clave del DeadLetter es un UUID derivado del transporte, no la conversación original.
Validar schema, clave y headers del original antes de republicarlo al topic original con conversationId
y headers de contrato, conservando messageId/correlationId. No publicar el sobre DeadLetter como inbound.
No resetear el grupo ni borrar message_dedupe: si ya hubo commit la reentrega se ignora;
si hubo rollback la deduplicación se revirtió y puede reprocesarse.
Inbound retiene 7 días y DLQ 90: si expiró el original, recuperarlo de una fuente autorizada;
no se puede reconstruir desde la DLQ.

## Outbox — originalTopic ready-to-send

Partition/offset 0 son valores convencionales del schema, **no coordenadas de reproceso**.
Buscar outbox.message_id por el messageId del sobre y contrastar conversationId/correlationId.
Detener scheduler con OUTBOX_ENABLED=false y reiniciar todas las instancias antes de intervenir.
Revisar orden: tras marcar una fila terminal pueden haber avanzado salidas posteriores.

Ejemplo en psql autorizado; sustituir identificadores por los comprobados del DeadLetter:

```sql
\set replay_message_id 'identificador-saliente'
\set replay_conversation_id 'identificador-conversacion'
\set replay_correlation_id '00000000-0000-0000-0000-000000000000'
BEGIN;
SELECT event_id, message_id, conversation_id, correlation_id,
       attempts, published_at, dead_lettered_at
FROM outbox
WHERE message_id = :'replay_message_id'
  AND conversation_id = :'replay_conversation_id'
  AND correlation_id = :'replay_correlation_id'::uuid
FOR UPDATE;
```

Continuar solo con exactamente una fila, published_at NULL y dead_lettered_at no NULL;
ante discrepancia ejecutar ROLLBACK. Corregida la causa y revisado el orden, en la misma TX:

```sql
UPDATE outbox
SET dead_lettered_at = NULL, attempts = 0, next_attempt_at = NULL
WHERE message_id = :'replay_message_id'
  AND conversation_id = :'replay_conversation_id'
  AND correlation_id = :'replay_correlation_id'::uuid
  AND published_at IS NULL AND dead_lettered_at IS NOT NULL;
-- Exigir UPDATE 1; de lo contrario ROLLBACK.
COMMIT;
```

Conservar eventId/messageId/payload; H0 deduplica ante entrega incierta posterior al ACK.
No reactivar filas publicadas. Schema inválido requiere corrección revisada con el productor;
limpiar intentos sin corregir causa repite el fallo. Habilitar OUTBOX_ENABLED, reiniciar instancias,
comprobar published_at o nueva DLQ, métricas y lag. Registrar solo identificadores/resultados.

## H9 y sagas

H9TurnException entrega razones seguras; el cliente no publica contingencia/DLQ por sí solo.
Ante 409/resultado incierto confirmar deduplicación H9 antes de reintentar herramientas.
Timeout de saga usa scheduler; revisar saga_step_execution.result_code='SUPPORT_REQUIRED'
y confirmar efectos H3/H2. No cambiar estados por SQL para afirmar compensaciones no confirmadas.
Ver [H9](H9.md), [SAGAS](SAGAS.md) y [OUTBOUND](OUTBOUND.md).
