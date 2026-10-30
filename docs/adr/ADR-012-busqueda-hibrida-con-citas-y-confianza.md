# ADR-012 — Búsqueda híbrida con citas y confianza

**Estado:** Propuesto  
**Fecha:** 2026-10-30  
**Contexto:** Necesitamos recuperación precisa con trazabilidad, minimizando alucinaciones. La spec exige citas obligatorias cuando aplica, nivel de confianza y regla "no inventar" ante baja confianza.

**Decisión:** Implementar **búsqueda híbrida** (KNN por coseno sobre embeddings + búsqueda textual con `tsvector` en español), fusionada con **Reciprocal Rank Fusion (RRF)**. Devolver por resultado `citation` (source_id, title, section, version, source_uri) y `confidence` (`high|medium|low`) calculado con umbrales configurables. **Si `confidence == 'low'` el consumidor no debe responder con ese contenido.**

**Consecuencias:**
- Mejor cobertura (semántica + palabras clave en español).
- Trazabilidad/auditoría obligatoria.
- Refuerza guardrails anti-alucinación ("no inventar").
- Requiere calibración con set dorado (`recall@5`, MRR).
- Umbrales parametrizables por entorno.

**Alternativas consideradas:**
- Solo vectorial: menor recall en búsquedas con términos exactos/políticas.
- Solo textual: menor recall semántico.
- Reranker pesado: puede agregarse después, mantener simple inicialmente.

**Aplicación:** Seguir contrato `shared/contracts/wa-knowledge-rag/retrieval.md` y reglas transversales.
