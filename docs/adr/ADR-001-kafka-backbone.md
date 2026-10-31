# ADR-001 — Kafka como backbone asíncrono del canal

**Estado:** Aceptado
**Contexto:** El canal WhatsApp necesita desacoplar ingreso (H0), orquestación (H0.2) y salida (H0) para absorber picos y no bloquear a Twilio.
**Decisión:** Usar Apache Kafka en modo KRaft como backbone asíncrono de todo el canal.
**Consecuencias:**
- Entrega at-least-once; consumidores idempotentes obligatorios.
- Orden por conversación mediante partición por `conversationId`.
- Sin ZooKeeper (KRaft), simplifica operación.
- Kafka no se usa como base de datos; el estado vive en PostgreSQL del orquestador.
**Alternativas consideradas:** Integración HTTP síncrona, que acopla disponibilidad y latencia;
colas en memoria, que pierden persistencia tras reinicio; RabbitMQ, que requeriría diseñar
enrutamiento y retención/replay equivalentes. No se afirma que otros brokers carezcan de orden.

**Aplicación en H0.1 (2026-10-27):** Spring for Apache Kafka declara los cuatro topics del
[catálogo](../../shared/contracts/events/topics.md); KRaft local de un nodo, 3 particiones
y réplica 1, incluso DLQ. Schemas JSON versionados se validan localmente y se empaquetan
desde shared/contracts/events; no se requiere Schema Registry en esta implementación.
Retenciones iniciales: 7 días inbound/outbound, 30 estados y 90 DLQ, configurables.
Fuera de local se requieren TLS/SASL, ACL, replicación 3 y min.insync.replicas=2.
El ACK del webhook precede al procesamiento de negocio; H0 debe observar la confirmación
de publicación y resolver su idempotencia. H0.1 no contiene listeners de negocio.

**Verificación:** KafkaPlatformIntegrationTest comprueba topics y retención con KRaft
embebido; KafkaMessagingIntegrationTest valida envío y recuperación DLQ. Comandos y
límites operativos en [handoff](../../platform/wa-event-platform/handoff.md).
