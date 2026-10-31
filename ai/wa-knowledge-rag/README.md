# wa-knowledge-rag (H8)

Base de conocimiento RAG del canal bancario: centraliza **contenido aprobado** del banco, lo indexa en
PostgreSQL + pgvector y lo recupera con **citas** y **nivel de confianza** para que el agente
conversacional (H9) responda sin inventar. Separa estrictamente contenido **público** (usable en
respuestas al cliente) de **interno** (solo razonamiento/derivación de H9 o soporte).

Python 3.12, administrado con [uv](https://docs.astral.sh/uv/). Diseño:
[`specs/conocimiento-rag/arquitectura.md`](../../specs/conocimiento-rag/arquitectura.md).

## Estado del componente

- Implementado: configuración por entorno, ingesta (validación, clasificación, chunker, embeddings,
  publicación transaccional con versionado), store PostgreSQL + pgvector con migraciones versionadas,
  recuperación híbrida (KNN coseno + texto español + RRF), confianza `high`/`medium`/`low`, formatter
  con citas, API FastAPI (`GET /health`, `POST /retrieval`) y contrato publicado en
  [`shared/contracts/wa-knowledge-rag/retrieval.md`](../../shared/contracts/wa-knowledge-rag/retrieval.md).
- **No hay endpoint ni CLI de ingesta**: publicar contenido es una llamada a la función Python
  `wa_knowledge_rag.ingestion.pipeline.ingest(...)` (ver
  [docs/guia-contenido.md](docs/guia-contenido.md)).
- **No hay umbrales de confianza por defecto**: sin `RAG_CONFIDENCE_HIGH_MIN` y
  `RAG_CONFIDENCE_MEDIUM_MIN` calibrados, toda consulta devuelve `confidence: low` y cero resultados.

## Qué hace y qué no hace

| Hace | No hace |
|---|---|
| Ingesta de contenido **solo aprobado**, con validación de PII/instrucciones y chunker por secciones | No aprueba contenido ni autentica al editor: `approved_by`/`approved_at` son datos de entrada de un proceso editorial confiable |
| Indexa en PostgreSQL + pgvector con embeddings del modelo configurado y `tsvector` en español | No ejecuta operaciones bancarias ni consulta datos transaccionales (eso es H7 + H1–H6) |
| Recupera de forma híbrida (vector + texto) y fusiona con RRF | No genera texto ni llama a un LLM: devuelve fragmentos con cita y score |
| Devuelve `confidence` (`high`/`medium`/`low`) y `sin_respuesta_suficiente` cuando no hay evidencia | No decide la respuesta al cliente: ante `low` o error, H9 no debe inventar (deriva o pide aclaración) |
| Separa audiencia `public` / `internal` y, para cliente, solo devuelve `public` | No expone contenido interno al cliente; tampoco es un mecanismo de autorización (la audiencia pedida no habilita nada) |
| Versiona el conocimiento y publica una revisión nueva retirando la anterior en una transacción | No integra Kafka, Twilio ni el ciclo técnico del mensaje (H0/H0.1/H0.2) |

## Módulos

| Módulo | Responsabilidad |
|---|---|
| `config.py` | `Settings` con pydantic-settings; no abre conexiones ni carga modelos al importar |
| `ingestion/` | `validator` (aprobación, PII, instrucciones), `classifier` (audiencia), `chunker` (secciones + ventanas), `embedder` (lotes con reintento), `pipeline` (publicación atómica y versionado) |
| `store/` | `db` (conexión + migraciones versionadas), `repo` (SQL híbrido con filtros), `models` (entidades) |
| `retrieval/` | `hybrid` (orquesta búsqueda), `scorer` (RRF normalizado y confianza), `formatter` (contrato v1 con citas) |
| `api/` | `__init__` (app FastAPI), `health`, `retrieval` (endpoint), `deps` (request, servicio y contexto de log) |

Detalle de flujos, SQL, confianza y límites: [docs/arquitectura.md](docs/arquitectura.md).
Cómo se carga, aprueba, versiona y evalúa el contenido: [docs/guia-contenido.md](docs/guia-contenido.md).

## Cómo levantarlo

### Con uv (local)

Requiere Python 3.12, `uv` en el `PATH` y un PostgreSQL con pgvector (por ejemplo el `kb-postgres` del
compose de esta carpeta).

```powershell
cd ai/wa-knowledge-rag
Copy-Item .env.example .env   # completar DATABASE_URL y GOOGLE_API_KEY
uv sync --frozen
uv run python -m wa_knowledge_rag.api
```

La API queda en `http://localhost:8102`:

```powershell
curl.exe http://localhost:8102/health
```

`GET /health` responde `{"status":"ok"}` sin tocar la base ni el modelo. `POST /retrieval` sí necesita
`DATABASE_URL` y `GOOGLE_API_KEY`: si falta alguna o la base no responde, devuelve `503`. Los umbrales
de confianza son independientes: sin ellos responde `200` con `confidence: low` y cero resultados.

En Windows, si `uv` falla por un lock global denegado, usar un cache local:
`$env:UV_CACHE_DIR="ai/wa-knowledge-rag/.uv-cache"` (la carpeta está excluida de ruff y git).

### Con Docker Compose (solo H8)

El compose de esta carpeta levanta `wa-knowledge-rag` + `kb-postgres` (`pgvector/pgvector:pg17`).
Desde `ai/wa-knowledge-rag`:

```powershell
Copy-Item .env.example .env   # completar GOOGLE_API_KEY y credenciales de la base
docker compose --env-file .env up -d --build
curl.exe http://localhost:8102/health
```

El servicio publica `${RAG_HOST_PORT:-8102}:8102`, espera a que `kb-postgres` esté healthy y su
entrypoint aplica las migraciones empaquetadas antes de arrancar la API y detiene el arranque si fallan.
`kb-postgres` publica `${KB_DB_HOST_PORT:-5438}:5432`.

### Con el stack completo de IA

En `ai/docker-compose.yml` el servicio `wa-knowledge-rag` se construye desde `./wa-knowledge-rag` y
depende de `kb-postgres`. Desde `ai/`:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
curl.exe http://localhost:8102/health
```

## Variables de entorno

Se leen del entorno y, opcionalmente, de un `.env` local (se ignoran las variables vacías y las
desconocidas). `.env` no se versiona: en el repo solo va `.env.example` con nombres y ejemplos.

| Variable | Para qué | Ejemplo |
|---|---|---|
| `PORT` | Puerto HTTP de la API | `8102` |
| `DATABASE_URL` | Cadena de conexión a PostgreSQL con pgvector (secreto) | `postgresql://usuario:clave@localhost:5438/wa_knowledge` |
| `GOOGLE_API_KEY` | API key del proveedor de embeddings (secreto) | *(vacío)* |
| `EMBEDDING_MODEL` | Modelo de embeddings | `text-embedding-004` |
| `EMBEDDING_DIMENSIONS` | Dimensión del vector; **debe ser 768** (la fija el esquema) | `768` |
| `RAG_TOP_K` | `top_k` por defecto al usar `HybridRetriever` sin `top_k` explícito | `5` |
| `RAG_CONFIDENCE_HIGH_MIN` | Umbral de confianza `high` sobre score 0–1 (requiere calibración) | *(vacío)* |
| `RAG_CONFIDENCE_MEDIUM_MIN` | Umbral de confianza `medium`; debe ser menor que el alto | *(vacío)* |
| `LOG_LEVEL` | Nivel de log (`DEBUG`/`INFO`/`WARNING`/`ERROR`/`CRITICAL`) | `INFO` |

Variables que usa **solo** el compose (están en `ai/.env.example`): `DB_USERNAME`, `DB_PASSWORD`,
`KB_DB_NAME` (`wa_knowledge`), `RAG_HOST_PORT` (`8102`) y `KB_DB_HOST_PORT` (`5438`). El compose arma
`DATABASE_URL` a partir de ellas.

`DATABASE_URL` y `GOOGLE_API_KEY` se guardan como `SecretStr` y nunca se registran. `RAG_TOP_K` no lo
aplica la API HTTP: `POST /retrieval` usa el `top_k` del request (5 si no se envía). Los umbrales, si
se informan, deben estar entre 0 y 1 y cumplir `RAG_CONFIDENCE_MEDIUM_MIN < RAG_CONFIDENCE_HIGH_MIN`.

## Cómo probarlo

```powershell
cd ai/wa-knowledge-rag
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest -q --cov=src --cov-fail-under=80
```

- **Unitarios** (por defecto): no necesitan Docker, red ni modelo; usan dobles de proveedor y de base.
  `pytest` excluye el marker `evals`.
- **Integración con PostgreSQL real**: `uv run pytest -m integration -q`, con Docker activo. Levanta un
  contenedor `pgvector/pgvector:pg17` aislado con testcontainers; sin Docker esas pruebas se omiten.
- **Set dorado de retrieval**: `uv run pytest -m integration tests/golden -q`. Mide `recall@5` (mínimo
  0.8), stemming español, citas y consultas fuera de base sobre un corpus sintético. Necesita Docker y
  **no** mide la calidad del modelo de embeddings real.
- **Gate del equipo** desde la raíz: `python ai/verify.py` corre sync, ruff, mypy y pytest con cobertura
  mínima 80% en todos los componentes Python de IA.

## Contrato publicado

Fuente de verdad para H9: [`shared/contracts/wa-knowledge-rag/retrieval.md`](../../shared/contracts/wa-knowledge-rag/retrieval.md) (v1).

- **Entrada** `POST /retrieval`: `query` (1–10000), `audience` (`public`/`internal`, exacta), `top_k`
  (1–100, opcional, 5 por defecto) y `correlationId` (`^[A-Za-z0-9._:-]+$`, 1–128). Sin campos extra ni
  conversión de tipos.
- **Salida 200 con evidencia**: `results[]` con `content`, `citation` (`source_id` lógico, `title`,
  `section`, `version`, `source_uri`) y `score` 0–1; más `confidence` (`high`/`medium`), `version`
  (`kb-<hash de revisiones aprobadas>`) y `correlationId`.
- **Salida 200 sin evidencia**: `results: []`, `confidence: "low"`, `reason: "sin_respuesta_suficiente"`.
  H9 **no debe inventar**.
- **Errores**: `400 CONSULTA_INVALIDA` y `503 SERVICIO_NO_DISPONIBLE`, sin stack traces ni detalles
  internos. Los ejemplos publicados se validan contra los modelos en
  `tests/unit/api/test_published_contract.py`.
- **Versionado**: un cambio incompatible se publica como v2 conviviendo con v1 hasta que H9 migre.

## Operación

- **Health**: `GET /health` → `{"status":"ok"}`. No consulta base ni proveedor; es el `HEALTHCHECK` del
  `Dockerfile` y del compose.
- **Logs** (JSON de structlog, con `correlationId`):
  - `knowledge_retrieved`: `correlationId`, `audience`, `confidence`, `result_count`.
  - `knowledge_published`: `correlationId`, `revision_id`, `version`, `chunks`.
  - `retrieval_completed` / `retrieval_failed`: `correlationId`, `confidence` o `code`.
  Nunca incluyen consulta, contenido, embeddings, aprobador ni credenciales.
- **Métricas**: no hay endpoint propio; latencia, tasa de `low` y de errores se agregan desde los logs
  estructurados por `confidence`/`code`.
- **Trazabilidad**: `correlationId` viaja de H9 a H8 y a los logs. Si un `400` llega sin
  `correlationId` válido, H8 genera un UUID para trazar el rechazo.
- **Errores frecuentes**:
  - `503` en `/retrieval` con `/health` en verde → falta `GOOGLE_API_KEY` o `DATABASE_URL`, el proveedor
    de embeddings falla, o la base no responde.
  - Siempre `confidence: low` → umbrales sin configurar o mal calibrados para el contenido real.
  - `IngestionError` con `PII_DETECTED`/`INSTRUCTIONS_DETECTED` → el contenido a publicar contiene
    datos personales o frases que parecen instrucciones; corregir la fuente.
  - `IngestionError` con `VERSION_MUST_INCREASE` → se intentó publicar una versión menor o igual a la
    vigente para esa fuente.
  - `IngestionError` con `DATABASE_DIMENSION_MISMATCH` → `EMBEDDING_DIMENSIONS` distinto de 768.

## Limitaciones conocidas (documentadas, no implementadas)

- Ingesta y retrieval usan las migraciones empaquetadas de `store/migrations/`.
  `002_logical_source.sql` actualiza instalaciones del esquema 001 conservando las revisiones.
- **Sin baja explícita**: una fuente se retira solo al publicar una versión nueva (la anterior pasa a
  `retired`). No hay operación para retirar sin reemplazo ni para rollback a una versión anterior.
- **Sin reindexación masiva**: se republica cada fuente con una versión nueva.
- **Sin CLI ni endpoint de ingesta**: solo la función Python `ingest(...)`.
