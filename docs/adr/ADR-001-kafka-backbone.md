# ADR-001 — Kafka como backbone asíncrono del canal

**Estado:** Aceptado
**Contexto:** El canal WhatsApp necesita desacoplar ingreso (H0), orquestación (H0.2) y salida (H0) para absorber picos y no bloquear a Twilio.
**Decisión:** Usar Apache Kafka en modo KRaft como backbone asíncrono de todo el canal.
**Consecuencias:**
- Entrega at-least-once; consumidores idempotentes obligatorios.
- Orden por conversación mediante partición por `conversationId`.
- Sin ZooKeeper (KRaft), simplifica operación.
- Kafka no se usa como base de datos; el estado vive en PostgreSQL del orquestador.
**Alternativas consideradas:** RabbitMQ, colas en memoria — descartadas por falta de orden garantizado por clave y replay.
