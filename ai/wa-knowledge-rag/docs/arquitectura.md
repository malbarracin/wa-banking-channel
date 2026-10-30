# Arquitectura del componente — wa-knowledge-rag (H8)

Diseño de referencia: [`specs/conocimiento-rag/arquitectura.md`](../../../specs/conocimiento-rag/arquitectura.md)
y requisitos: [`specs/conocimiento-rag/spec.md`](../../../specs/conocimiento-rag/spec.md).
Este documento describe **lo que el código implementa hoy**.

## Propósito y límites

H8 entrega conocimiento documental aprobado a H9 con citas y confianza. No ejecuta operaciones
bancarias (H7 + H1–H6), no conversa con el cliente (H9), no gestiona el ciclo técnico del mensaje
(H0.2) y no integra Kafka ni Twilio.

## Estructura del paquete

```
ai/wa-knowledge-rag/
├── config.py                      # Settings (pydantic-settings)
├── api/
│   ├── __init__.py                # create_app(), main(), manejo de 400
│   ├── __main__.py                # python -m wa_knowledge_rag.api
│   ├── deps.py                    # RetrievalRequest, DatabaseRetrievalService, contexto de log
│   ├── health.py                  # GET /health
│   └── retrieval.py               # POST /retrieval y errores de negocio
├── ingestion/
│   ├── validator.py               # Source, validate(), IngestionError
│   ├── classifier.py              # audiencia efectiva
│   ├── chunker.py                 # secciones + ventanas de tokens léxicos
│   ├── embedder.py                # Provider, GoogleProvider, Embedder
│   └── pipeline.py                # ingest(): publicación atómica y versionado
├── store/
│   ├── db.py                      # connect(), migrate()
│   ├── repo.py                    # KnowledgeRepository.search() (SQL híbrido)
│   ├── models.py                  # Source, Chunk, SearchHit, Audience
│   └── migrations/001_knowledge.sql
├── retrieval/
│   ├── hybrid.py                  # HybridRetriever
│   ├── scorer.py                  # RRF normalizado y confianza
│   └── formatter.py               # RetrievalResponse, InsufficientResponse, Citation
└── tests/{unit,integration,golden}/
```

## Modelo de datos (PostgreSQL + pgvector)

`kb_source` — una fila por **revisión** de una fuente:

| Columna | Notas |
|---|---|
| `id` | UUID de la revisión (PK) |
| `source_id` | UUID lógico de la fuente; estable entre versiones, agregado por `002_logical_source.sql` |
| `title`, `owner_area` | Metadatos de la fuente |
| `audience` | `public` o `internal` (`CHECK`) |
| `version` | Entero > 0; debe crecer por fuente |
| `approved_by`, `approved_at` | Trazabilidad de aprobación (`approved_at` con zona horaria) |
| `status` | `draft`, `approved` o `retired` |
| `source_uri`, `metadata`, `created_at`, `updated_at` | En el esquema empaquetado |

`kb_chunk` — fragmentos indexados:

| Columna | Notas |
|---|---|
| `id` | UUID del fragmento (PK) |
| `source_id` | FK a `kb_source.id` (la **revisión**) |
| `version`, `ordinal` | Versión y posición; único por `(source_id, version, ordinal)` |
| `content`, `section` | Texto del fragmento y sección de origen |
| `metadata` | JSONB; guarda `source_id` lógico, `title` y `audience` para la cita |
| `embedding` | `vector(768)`; se registra el adaptador de pgvector en la conexión |
| `tsv` | `tsvector` en español, mantenido por trigger `kb_chunk_tsv` |

Índices: `idx_kb_chunk_embedding_hnsw` (HNSW, `vector_cosine_ops`), `idx_kb_chunk_tsv_gin` (GIN),
`idx_kb_chunk_source_version`, `idx_kb_source_audience_status`. El esquema de ingesta agrega el índice
único parcial `kb_source_current` (una sola revisión `approved` por `source_id`).

### Migraciones

- `store/migrations/001_knowledge.sql` se empaqueta y la aplica `connect(settings, apply_migrations=True)`.
  `migrate()` toma un advisory lock (`810200136`), crea `kb_migration` y aplica cada `.sql` una sola vez
  dentro de una transacción; un fallo revierte todo. El entrypoint de Docker la ejecuta al arrancar y la
  API **no** migra en cada request.
- `002_logical_source.sql` agrega la identidad lógica y el índice único de revisión vigente.
  Las revisiones heredadas se agrupan por título, como en el esquema 001; desde entonces se usa
  `source_id`. Ingesta y retrieval comparten estas migraciones empaquetadas.

## Flujo de ingesta

`ingest(source, embedder, connection, correlation_id) -> int` (cantidad de chunks publicados):

1. **Validación** (`ingestion/validator.py`, antes de cualquier llamada externa):
   - `status` debe ser `approved` → `APPROVAL_REQUIRED`.
   - `approved_by` no vacío y `approved_at` presente → `APPROVAL_TRACE_REQUIRED`.
   - `approved_at` con zona horaria → `APPROVAL_TIMEZONE_REQUIRED`.
   - Se inspeccionan `title`, `content` y `owner_area` con expresiones regulares:
     - PII (documentos, teléfonos, CBU, nombre + saldo/cuenta/tarjeta) → `PII_DETECTED`.
     - Patrones de instrucciones (`ignorá/olvidá ... instrucciones`, `system:`, `<|im_start|>`) →
       `INSTRUCTIONS_DETECTED`.
2. **Clasificación** (`classifier.py`): la audiencia efectiva es la declarada en la `Source`
   (`internal` por defecto; `public` solo si el editor lo marca explícitamente). No se infiere por
   palabras clave.
3. **Chunker** (`chunker.py`): separa por encabezados Markdown (`#`…`######`); el preámbulo usa el
   título como sección. Ventanas de 500 tokens léxicos con solapamiento de 50 (unidades aproximadas,
   no tokens exactos del modelo). Cada chunk hereda `source_id`, `version`, `title`, `section`,
   `audience` y un `ordinal` global. Contenido vacío → `EMPTY_CONTENT`; ventana inválida →
   `INVALID_WINDOW`.
4. **Embeddings** (`embedder.py`): `Embedder` envía lotes de 32 textos con timeout de 30 s y hasta 3
   intentos con backoff exponencial; `GoogleProvider` usa `RETRIEVAL_DOCUMENT` con
   `EMBEDDING_MODEL`/`EMBEDDING_DIMENSIONS`. Verifica cantidad, dimensión, finitud y vector no nulo
   (`INVALID_EMBEDDING`); sin API key → `EMBEDDING_KEY_REQUIRED`; proveedor caído → `EMBEDDING_UNAVAILABLE`.
   `ingest` exige dimensión 768 (`DATABASE_DIMENSION_MISMATCH`).
5. **Indexación atómica** (`pipeline.py`), en una única transacción:
   - advisory lock por `source_id`;
   - lee la versión máxima y exige que la nueva sea mayor → `VERSION_MUST_INCREASE`;
   - marca `retired` la revisión `approved` anterior;
   - inserta la nueva revisión con estado `approved` y sus chunks con `tsv = to_tsvector('spanish', …)`.
   Un fallo en cualquier paso revierte también el retiro previo. La conexión debe ser exclusiva y sin
   una transacción exterior (preferentemente `autocommit=True`).
6. **Trazabilidad**: log `knowledge_published` con `correlationId`, `revision_id`, `version` y `chunks`.
   Nunca título, contenido, aprobador ni errores del proveedor o de la base.

## Flujo de retrieval

`POST /retrieval` (`api/retrieval.py` → `api/deps.py`):

1. **Validación de entrada** (`RetrievalRequest`, `strict`, sin campos extra, recorte de espacios):
   `query` 1–10000, `audience` exacta, `top_k` 1–100 (5 por defecto), `correlationId` con patrón
   `^[A-Za-z0-9._:-]+$`. Un error de validación (incluido JSON malformado o cuerpo que no es objeto)
   devuelve `400 CONSULTA_INVALIDA`; el `correlationId` se toma del cuerpo si es válido o se genera un
   UUID.
2. **Embedding de la consulta**: `GoogleProvider` con `task_type="RETRIEVAL_QUERY"`. Si falta la API key
   o el embedding no está disponible, se responde `503`.
3. **Versión de la KB**: dentro de una transacción de lectura (`REPEATABLE READ`) se calcula
   `kb-<md5 de id:version de las revisiones aprobadas>`; ese valor es opaco para H9.
4. **Búsqueda** (`store/repo.py`): el SQL filtra **antes** del ranking:
   - `s.status='approved'`, audiencia exacta, `c.version = s.version`;
   - `version` opcional restringe la selección a la vigente (nunca habilita retiradas);
   - excluye fuentes con una versión aprobada más nueva del mismo `source_id` lógico.
   Genera candidatos por KNN coseno (`embedding <=> query`) y por texto español
   (`ts_rank(tsv, plainto_tsquery('spanish', query))`), con `max(20, top_k*4)` candidatos por rama.
5. **Fusión RRF** (`repo.py`): `1/(60 + rank)` por rama y suma; un fragmento presente en ambas ramas
   supera a uno presente en una sola.
6. **Confianza y formato** (`retrieval/scorer.py`, `formatter.py`): el score publicado normaliza el RRF
   por su máximo teórico `2/61` (0–1). Con el mejor score se calcula la confianza:
   `>= RAG_CONFIDENCE_HIGH_MIN` → `high`; `>= RAG_CONFIDENCE_MEDIUM_MIN` → `medium`; resto → `low`.
   Sin ambos umbrales, o con score fuera de `(0, 1]`, siempre `low`. En `low` se devuelve únicamente
   `sin_respuesta_suficiente` (sin fragmentos); en `high`/`medium` se ordenan por score descendente y se
   omiten los fragmentos individuales de confianza `low`.
7. **Audiencia y cliente** (`retrieval/hybrid.py`): `for_client=True` (la API lo activa para
   `audience=public`) impide consultar `internal` y devuelve `low` sin buscar. `audience=internal` solo
   se usa para razonamiento/derivación de H9/soporte (`for_client=False`), nunca para texto directo al
   cliente.
8. **Trazabilidad**: log `knowledge_retrieved` con `correlationId`, `audience`, `confidence` y
   `result_count`; `retrieval_completed`/`retrieval_failed` a nivel API. Sin consulta, contenido,
   embeddings ni credenciales.

`HybridRetriever` no llama a ningún modelo ni genera texto: recibe el embedding ya calculado. El
contenido recuperado se trata siempre como **datos**, nunca como instrucciones.

## Endpoints

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/health` | `200 {"status":"ok"}`; sin dependencias externas |
| `POST` | `/retrieval` | `200` contrato v1 (evidencia o `sin_respuesta_suficiente`), `400 CONSULTA_INVALIDA`, `503 SERVICIO_NO_DISPONIBLE` |

Cualquier excepción durante el retrieval se traduce a `503` sin detalles internos. El contrato completo
está en [`shared/contracts/wa-knowledge-rag/retrieval.md`](../../../shared/contracts/wa-knowledge-rag/retrieval.md).

## Seguridad y privacidad

- Sin PII en la base: la ingesta rechaza documentos, teléfonos, CBU y nombre + dato sensible.
- `DATABASE_URL` y `GOOGLE_API_KEY` son `SecretStr`; `.env` no se versiona.
- Logs JSON sin consulta, contenido, embeddings, aprobador ni credenciales; solo identificadores
  técnicos, audiencia, confianza y cantidades.
- Separación `public`/`internal` aplicada en SQL (audiencia exacta) y en la capa de retrieval.
- `correlationId` de punta a punta para auditoría.

## Configuración

`Settings` (ver tabla completa en el [README](../README.md#variables-de-entorno)): `PORT`,
`DATABASE_URL`, `GOOGLE_API_KEY`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS` (768 fijo por el esquema),
`RAG_TOP_K`, `RAG_CONFIDENCE_HIGH_MIN`, `RAG_CONFIDENCE_MEDIUM_MIN`, `LOG_LEVEL`. Los umbrales no
tienen default: se calibran con el set dorado y contenido real.

## Pruebas

| Nivel | Comando | Qué cubre |
|---|---|---|
| Unitario | `uv run pytest -q` | Validación, chunker, embeddings con dobles, pipeline transaccional, confianza, formatter, contrato HTTP y ejemplos publicados |
| Integración | `uv run pytest -m integration -q` | Esquema, índices, trigger `tsv`, filtros de audiencia/vigencia y publicación atómica sobre `pgvector/pgvector:pg17` |
| Set dorado | `uv run pytest -m integration tests/golden -q` | `recall@5`, stemming español, citas lógicas y consultas fuera de base (corpus sintético) |

Los tests de integración se omiten sin Docker. El set dorado **no** calibra embeddings reales: sus
vectores son ortogonales y sintéticos.
