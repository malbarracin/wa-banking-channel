# Topics del canal

Clave Kafka obligatoria: `conversationId`, serializada como String UTF-8; sin partición explícita.
Una conversación conserva partición y orden mientras no cambie la cantidad de particiones.
No aumentar particiones durante conversaciones activas sin un plan de migración.

| Topic | Particiones local | Réplicas local | Retención inicial | Variable | Productor | Consumidor |
| --- | --- | --- | --- | --- | --- | --- |
| wa.message.received.v1 | 3 | 1 | 7 días | KAFKA_RECEIVED_RETENTION | H0 | H0.2 |
| wa.message.ready-to-send.v1 | 3 | 1 | 7 días | KAFKA_READY_TO_SEND_RETENTION | H0.2 | H0 |
| wa.message.status.v1 | 3 | 1 | 30 días | KAFKA_STATUS_RETENTION | H0 | H0.2 / observabilidad |
| wa.message.dlq.v1 | 3 | 1 | 90 días | KAFKA_DLQ_RETENTION | Infraestructura | Soporte |

TASK-71 exige tres particiones también para DLQ; prevalece sobre la propuesta de una en arquitectura.
Política `delete`, retención por tiempo configurable con duraciones (`7d`, `12h`).
`KAFKA_TOPIC_PARTITIONS` (mínimo 3) y `KAFKA_TOPIC_REPLICAS` controlan las declaraciones.
KafkaAdmin crea los topics y actualiza su retención al arrancar; no reduce particiones ni cambia réplicas existentes.
Los valores son iniciales locales, sujetos a confirmación para el piloto.
Fuera de local: replicación 3, min.insync.replicas=2, TLS + SASL y ACL por componente.
