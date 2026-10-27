# ADR-003 — Partición por conversación para preservar orden

**Estado:** Aceptado
**Contexto:** Los mensajes de una misma conversación deben procesarse en orden; procesamiento paralelo dentro de una conversación rompe coherencia de estado.
**Decisión:** Clave de partición de todos los topics = `conversationId`. Una conversación = una partición; concurrencia del listener ≤ particiones; sin procesamiento paralelo dentro de una partición.
**Consecuencias:**
- Orden garantizado por conversación.
- Hot partitions posibles si una conversación genera mucho tráfico (aceptable en piloto).
- Escalabilidad horizontal por conversaciones, no por mensajes.
**Alternativas consideradas:** Partición por `messageId` o round-robin — descartadas por pérdida de orden.
