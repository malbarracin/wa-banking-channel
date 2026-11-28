# ADR wa-knowledge-rag-0001 — Conocimiento en PostgreSQL + pgvector con embeddings de Google

- Estado: aceptada
- Fecha: 2026-11-27
- Componente: `ai/wa-knowledge-rag`

## Contexto

La base de conocimiento tiene que recuperar contenido aprobado por contexto, con citas, versiones, separación
público/interno y vuelta atrás. El stack del equipo fija PostgreSQL + pgvector para conocimiento y Redis solo para la
memoria del agente. El modelo de embeddings por defecto del stack (`text-embedding-004`) ya no lo sirve el proveedor.

## Decisión

- Postgres 16 + pgvector, base propia del componente. Columna `vector(768)`, índice HNSW con `vector_cosine_ops`,
  más `tsvector` en español con índice GIN para búsqueda híbrida (fusión RRF).
- Embeddings con `gemini-embedding-001` y `output_dimensionality=768` (SDK `google-genai`), configurable por entorno.
  Cada chunk guarda `embedding_model`; cambiar de modelo o dimensión exige reindexar todo.
- Nada de conocimiento ni embeddings en Redis.

## Consecuencias

- Las transacciones de Postgres permiten publicar y volver atrás de forma atómica (ver ADR 0002).
- Dependencia del proveedor de embeddings en cada consulta: si falla, la consulta devuelve 503
  `KNOWLEDGE_UNAVAILABLE` (nunca `sin_respuesta`).
- Los tests unitarios usan un embedder falso determinístico; la calidad se mide con el set dorado y el modelo real.
