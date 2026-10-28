# ADR-007 — Estado de conversación y saga persistente

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
