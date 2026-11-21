# Decisiones de arquitectura (ADR)

Una decisión por archivo, numerada y con estado. Una decisión aceptada no se edita: si cambia, se escribe un ADR
nuevo que la supersede y se marca la anterior como `Superada por ADR-XXXX`.

| ADR | Decisión | Estado | Alcance |
| --- | --- | --- | --- |
| [ADR-0001](0001-kafka-backbone-asincrono.md) | Kafka como backbone asíncrono del canal | Aceptada | Canal |
| [ADR-0002](0002-at-least-once-con-idempotencia.md) | Entrega at-least-once con consumidores idempotentes | Aceptada | Canal |
| [ADR-0003](0003-particion-por-conversacion.md) | Partición por `conversationId` para preservar el orden | Aceptada | Canal |
| [ADR-0004](0004-contratos-json-schema-versionados.md) | Contratos como JSON Schema versionados, sin registro ni librería compartida | Aceptada | Canal |
| [ADR-0005](0005-reintentos-bloqueantes-y-dlq-unica.md) | Reintentos bloqueantes con backoff y una sola DLQ | Aceptada | Canal |
| [ADR-0006](0006-conversation-id-opaco-y-determinista.md) | `conversationId` opaco y determinístico | Aceptada | Canal |
