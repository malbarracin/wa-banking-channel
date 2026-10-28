# ADR-008 — Límite entre orquestación de proceso (H0.2) y conversación (H9)

**Estado:** Propuesto  
**Fecha:** 2026-10-28  
**Contexto:** Necesario separar coordinación técnica (estado, timeouts, sagas) de razonamiento conversacional e intención.  
**Decisión:** H0.2 coordina ciclo de vida y flujos; H9 entiende intención, decide respuesta y usa capacidades vía H7. Comunicación por contrato `shared/contracts/wa-conversation-agent/turn.md`.  
**Consecuencias:**
- Límites claros, evita duplicación de lógica.
- Permite evolución independiente.
- Requiere contrato estable y trazabilidad end-to-end.
- H0.2 no implementa herramientas MCP ni reglas de negocio H1–H6.  
**Alternativas consideradas:** H9 orquesta todo (mezcla responsabilidades), o H0.2 hace razonamiento (fuera de alcance).  
**Aplicación:** Seguir `int-boundaries.md`, propagar `correlationId`, coordinar capacidades con trazabilidad.
