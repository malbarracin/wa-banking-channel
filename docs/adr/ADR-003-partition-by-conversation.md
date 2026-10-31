# ADR-003 — Partición por conversación para preservar orden

**Estado:** Aceptado
**Contexto:** Los mensajes de una misma conversación deben procesarse en orden; procesamiento paralelo dentro de una conversación rompe coherencia de estado.
**Decisión:** Clave de partición de todos los topics = `conversationId`. Una conversación = una partición; concurrencia del listener ≤ particiones; sin procesamiento paralelo dentro de una partición.
**Consecuencias:**
- Orden dentro de una partición del mismo topic, con procesamiento secuencial.
- Hot partitions posibles si una conversación genera mucho tráfico (aceptable en piloto).
- Escalabilidad horizontal por conversaciones, no por mensajes.
**Alternativas consideradas:** Partición por `messageId` o round-robin — descartadas por pérdida de orden.

**Aplicación en H0.1 (2026-10-27):** KafkaEventProducer usa conversationId String UTF-8
como clave sin fijar partición. KafkaEventConsumer rechaza clave distinta del sobre.
Todos los topics locales tienen 3 particiones y réplica 1, incluida DLQ (TASK-71 prevalece
sobre la propuesta inicial de una partición en arquitectura). Concurrencia configurada: 3.
H0 debe generar una clave estable; los listeners integradores no deben delegar efectos
a tareas paralelas que rompan la secuencia dentro de la partición.

La garantía depende de mantener la asignación por clave: aumentar particiones puede mover
una conversación. Requiere drenar o migrar conversaciones con un plan coordinado.
No hay orden global entre topics; varios productores deben coordinar su secuencia si
publican simultáneamente para una conversación. El replay desde DLQ llega después de
mensajes nuevos y requiere coordinación del consumidor, no restaura la posición histórica.
Los retries bloqueantes preservan la secuencia de intentos en la partición; recuperar
un registro a DLQ permite continuar los siguientes y deja un hueco que soporte debe resolver.

**Verificación:** KafkaPlatformIntegrationTest comprueba que dos mensajes con la misma
clave conservan partición y orden. Ver [catálogo](../../shared/contracts/events/topics.md)
y [handoff](../../platform/wa-event-platform/handoff.md).
