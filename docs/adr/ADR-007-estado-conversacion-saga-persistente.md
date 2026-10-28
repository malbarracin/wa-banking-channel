# ADR-007 — Estado de conversación y saga persistente

## Evidencia de implementación — TASK-98

Flyway V1–V7 administra conversación, saga, ejecuciones, deduplicación y outbox.
Estado/deduplicación/outbox comparten TX; publicación posterior conserva eventId/messageId
ante reentrega. Despacho serial global bloquea las siguientes filas durante retry y exige deduplicación H0.
WAITING permite continuar tras reinicio; timeout PT24H, revisión cada 60000 ms por defecto.
Scheduler y señales usan bloqueo de escritura. Compensación sin confirmación remota queda
FAILED/SUPPORT_REQUIRED: faltan adaptadores H3/H2. El estado Propuesto se conserva hasta aprobación.
Ver [SAGAS](../../platform/wa-channel-orchestrator/SAGAS.md) y
[reproceso](../../platform/wa-channel-orchestrator/DLQ.md).

**Estado:** Propuesto  
**Fecha:** 2026-10-28  
**Contexto:** Flujos como onboarding/re-verificación requieren múltiples pasos, posibles pausas y compensaciones ante fallos.  
**Decisión:** Persistir estado de conversación y instancias de saga en PostgreSQL con Flyway, con versionado optimista y trazabilidad por paso.  
**Consecuencias:**
- Permite reanudar tras reinicio/reintento sin pérdida de estado.
- Facilita compensaciones explícitas y auditoría.
- Requiere migraciones, transaccionalidad y outbox para publicación.
- Evita estado solo en memoria.  
**Alternativas consideradas:** Estado puramente en Kafka/Redis (menos trazable para sagas complejas), o sin persistencia (imposible para pausas).  
**Aplicación:** Entidades ConversationState, SagaInstance, SagaStepExecution; timeouts gestionados por scheduler.
