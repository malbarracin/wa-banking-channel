# ADR-011 — Conocimiento en PostgreSQL + pgvector

**Estado:** Propuesto  
**Fecha:** 2026-10-30  
**Contexto:** H8 necesita almacenar y buscar conocimiento documental aprobado con búsqueda semántica. Requiere control transaccional, versionado, filtrado por audiencia, citas y trazabilidad. Redis está reservado para memoria del agente (H9) según `ia-stack.md`/reglas; embeddings y conocimiento no deben guardarse en Redis.

**Decisión:** Usar **PostgreSQL + pgvector** (`pgvector/pgvector:pg17`) con:
- Tablas `kb_source` y `kb_chunk` con `vector(<dim>)` (dimensión = `ia.embedding_dimensions`).
- Índices **HNSW** (`vector_cosine_ops`) para KNN y **GIN** sobre `tsvector` en español para búsqueda textual.
- Migraciones versionadas, reindexado atómico por versión (retirar anterior).
- Extensión `vector` habilitada.

**Consecuencias:**
- Consistente con stack definido (`psycopg` 3, pgvector).
- Transaccionalidad para aprobación/versionado/retiro atómico.
- Control total sobre esquema, índices, filtrado por audiencia/versión.
- Permite búsqueda híbrida (vector + texto) en la misma base.
- Requiere Postgres con pgvector en entorno (docker-compose ya contempla base para IA).

**Alternativas consideradas:**
- Chroma/FAISS/Pinecone: **rechazadas** (prohibidas sin aprobación explícita según `ia-stack.md`).
- Redis para embeddings/conocimiento: **rechazada** (reservado a memoria de sesión/largo plazo de H9).

**Aplicación:** Seguir `rag-pgvector` skill, mantener dimensión sincronizada con `project.yaml`.
