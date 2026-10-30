# ADR-013 — Solo contenido aprobado, separación público/interno

**Estado:** Propuesto  
**Fecha:** 2026-10-30  
**Contexto:** El conocimiento debe ser confiable y seguro. No puede exponer contenido interno al cliente, no debe contener PII/datos transaccionales, y solo contenido aprobado debe ser consultable por el agente para respuestas al cliente.

**Decisión:** 
- **Gating de aprobación:** solo fuentes/chunks con `status='approved'` entran al índice consultable. Borradores, rechazados y retirados nunca se devuelven.
- **Separación público/interno:** cada fuente tiene `audience` (`public|internal`). El filtro por audiencia es obligatorio en la consulta. **Solo `public`** se utiliza para respuestas directas al cliente; `internal` solo para razonamiento/derivación sin exposición.
- **Prevención de PII:** la ingesta rechaza contenido con patrones de documento, teléfono, CBU u otros datos sensibles. Sin datos transaccionales.
- **Trazabilidad:** aprobaciones registradas (`approved_by`, `approved_at`), versionado y reindexado atómico.

**Consecuencias:**
- Cumple requisitos de privacidad y control de publicación.
- Reduce riesgo de fuga de información interna.
- Fuerza proceso de aprobación (Negocio + Compliance/Legal según corresponda).
- Requiere validación en ingesta y filtrado estricto en retrieval.

**Alternativas consideradas:**
- Filtrado solo en prompt: **rechazada** (no confiable, puede filtrarse mal).
- Permitir todo y confiar en agente: **rechazada** (viola principio de seguridad por diseño).

**Aplicación:** Aplicar en `ingestion/validator.py`, `ingestion/pipeline.py`, `retrieval/hybrid.py` y contrato de retrieval.
