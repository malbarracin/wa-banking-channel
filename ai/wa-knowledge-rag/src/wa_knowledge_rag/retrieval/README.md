# Retrieval v1

`HybridRetriever(KnowledgeRepository(connection), settings).retrieve(...)` recibe
`query`, `embedding`, `audience`, `correlation_id`, `kb_version` y `top_k` opcional.
El embedding debe venir del modelo configurado; retrieval no llama a un LLM.
`kb_version` identifica la publicación consultada y lo proporciona el llamador.
Serializar el modelo retornado con `model_dump(mode="json", by_alias=True)` para
obtener exactamente los campos del contrato `retrieval.md` (incluido `correlationId`).

El store filtra aprobación, vigencia y audiencia antes de KNN coseno y `ts_rank`
en español; fusiona ambas listas con RRF de constante 60 y rangos desde 1.
El score publicado divide el RRF bruto por su máximo teórico `2/61`. Los umbrales
`RAG_CONFIDENCE_HIGH_MIN` y `RAG_CONFIDENCE_MEDIUM_MIN` operan sobre esta escala 0–1.
Se usa el mejor score para confianza; fragmentos por debajo de medium se omiten.
Sin ambos umbrales configurados, sin candidatos o con confianza low, se devuelve
solo la respuesta `sin_respuesta_suficiente` sin contenido.

RRF mide acuerdo de rankings, no veracidad ni probabilidad de relevancia. Un
candidato presente únicamente en una rama alcanza como máximo 0.5. Los umbrales
0.9/0.6 de los tests son sintéticos y deben calibrarse con conocimiento aprobado
y embeddings reales antes de habilitar respuestas en producción; no se fijan
defaults de producción. Un acuerdo de rankings también puede ser irrelevante.

Por defecto `for_client=True` impide consultar internal y devuelve low. Para
razonamiento/derivación se requiere `for_client=False` y `audience="internal"`;
esa respuesta es exclusiva de H9/soporte, nunca del canal directo al cliente.
Cada consulta usa una audiencia exacta. No se ejecuta ni interpreta el contenido
como instrucciones. Los logs solo incluyen correlationId, audiencia, confianza
y cantidad; no incluyen consulta, contenido, embeddings ni credenciales.

Verificación: gates habituales y `uv run pytest -m integration tests/golden -q`.
El golden set usa PostgreSQL/pgvector con testcontainers y vectores ortogonales
sintéticos: mide recall@5 (mínimo 0.8), stemming español, citas lógicas y consultas
fuera de base. Necesita Docker; no mide la calidad del modelo de embeddings real.
