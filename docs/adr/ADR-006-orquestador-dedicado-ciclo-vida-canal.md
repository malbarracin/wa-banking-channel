# ADR-006 — Orquestador dedicado para el ciclo de vida del canal

## Evidencia de implementación — TASK-98

Consumidor, deduplicación, contexto, cliente H9, saga y outbox están implementados.
Falta conectar los puertos mediante handler síncrono InboundMessage dentro de la TX de
deduplicación; el consumidor aislado no genera respuesta. ACK tras commit y entrega at-least-once.
El estado Propuesto se conserva hasta aprobación de arquitectura.
Ver [handoff](../../shared/contracts/wa-channel-orchestrator/handoff.md).

**Estado:** Propuesto  
**Fecha:** 2026-10-28  
**Contexto:** El canal necesita coordinar flujos multi-componente (inbound → contexto → H9 → respuesta) manteniendo trazabilidad, timeouts, reintentos y sagas.  
**Decisión:** Crear un componente dedicado `platform/wa-channel-orchestrator` (H0.2) separado de H0 (gateway) y H9 (agente conversacional).  
**Consecuencias:**
- Clarifica límites: orquestación de proceso vs conversación (H9). No duplica reglas de negocio de H1–H6.
- Permite máquina de estados/sagas persistentes, pausas/reanudaciones y compensaciones.
- Controla correlación, serialización por conversación, deduplicación y políticas de resiliencia.
- Añade complejidad (nuevo componente, PostgreSQL).  
**Alternativas consideradas:** Poner lógica en gateway (acopla transporte a orquestación), o en H9 (mezcla razonamiento con coordinación).  
**Aplicación:** Seguir límites de `int-boundaries.md`, contrato con H9 vía `shared/contracts/wa-conversation-agent/turn.md`.
