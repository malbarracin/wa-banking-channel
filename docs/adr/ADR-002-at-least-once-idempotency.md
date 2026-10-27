# ADR-002 — At-least-once + idempotencia como semántica de entrega

**Estado:** Aceptado
**Contexto:** Kafka ofrece at-least-once por defecto; exactly-once es costoso y no necesario si los consumidores son idempotentes.
**Decisión:** Semántica at-least-once en toda la plataforma; cada consumidor implementa idempotencia por clave de idempotencia acordada (`MessageSid` en H0, `messageId` en H0.2).
**Consecuencias:**
- Productores con `enable.idempotence=true` y `acks=all`.
- Consumidores commiten tras el efecto (`ackMode=MANUAL`).
- Reentregas no duplican efectos; se registra el duplicado cuando corresponda.
**Alternativas consideradas:** Exactly-once transactions — descartadas por complejidad y costo para el piloto.
