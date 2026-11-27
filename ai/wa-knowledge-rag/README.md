# Base de conocimiento del canal WhatsApp (`wa-knowledge-rag`)

Guarda el conocimiento **aprobado** del banco y lo devuelve por HTTP con su cita y una señal de confianza, para que el agente conversacional responda con la política oficial en lugar de improvisar. El texto fuente vive en `content/` (versionado en git); los estados, las aprobaciones, las publicaciones y el registro de consultas viven en PostgreSQL 16 + pgvector.

## Qué hace y qué no hace

Hace:

- ingestar piezas (preguntas frecuentes, guías, políticas y mensajes) con validación de metadatos, datos personales e instrucciones;
- registrar aprobaciones por rol, publicar versiones del conocimiento (`kv-N`), retirar, revertir y sincronizar cambios;
- recuperar fragmentos por similitud vectorial y texto, con citas y nivel de confianza (`alta`, `baja`, `sin_respuesta`);
- separar contenido público de contenido interno y auditar cada consulta sin guardar el texto del cliente.

No hace:

- ejecutar operaciones bancarias ni consultar datos transaccionales del cliente en tiempo real;
- conversar con el cliente, decidir intenciones ni redactar la respuesta final (lo hace el agente conversacional);
- generar texto con un LLM ni entrenar el modelo del proveedor (usa embeddings del proveedor configurado);
- hablar con el canal (Twilio, Kafka) ni aplicar las reglas de negocio: solo las explica;
- publicar contenido no aprobado ni guardar datos personales del cliente.

Límites completos: [`specs/wa-knowledge-rag/spec.md`](../../specs/wa-knowledge-rag/spec.md) §1 y [`specs/wa-knowledge-rag/arquitectura.md`](../../specs/wa-knowledge-rag/arquitectura.md) §1.

## Instalación

Requiere Python 3.12 y [uv](https://docs.astral.sh/uv/). Desde esta carpeta:

```bash
uv sync
uv run kb --help
```

Los ejecutables del proyecto son `kb` (operación del conocimiento) y `kb-api` (servicio HTTP).

## Configuración

La configuración se lee del entorno con prefijo `KB_` (más `GOOGLE_API_KEY`) y, en local, de un `.env` que no se versiona. Copiá `.env.example` a `.env` y completá los valores de tu entorno. No hay valores reales en el repositorio: `.env.example` es un archivo estricto de pares `CLAVE=valor`, sin comentarios, con los secretos vacíos (los tests lo validan).

| Variable | Default | Para qué sirve | Ejemplo (sin valores reales) |
| --- | --- | --- | --- |
| `KB_PORT` | `8102` | Puerto HTTP de la API. | `8102` |
| `KB_DATABASE_URL` | sin valor | Cadena de conexión a PostgreSQL 16 + pgvector de este componente; vacía deja la API degradada. | `postgresql://<usuario>:<clave>@<host>:5432/<base>` |
| `GOOGLE_API_KEY` | sin valor | Credencial del proveedor de embeddings; se inyecta por entorno, nunca se commitea. | (se define en el entorno) |
| `KB_EMBEDDING_MODEL` | `gemini-embedding-001` | Modelo de embeddings. | `gemini-embedding-001` |
| `KB_EMBEDDING_DIMENSIONS` | `768` | Dimensión del vector (la migración fija `vector(768)`). | `768` |
| `KB_ENVIRONMENT` | `dev` | `dev`, `test` o `real`; en `real` no se aceptan aprobaciones ni parámetros de prueba. | `dev` |
| `KB_CONSUMERS` | sin valor | JSON con los consumidores habilitados: `id`, `token_sha256` (SHA-256 hexadecimal del token de servicio) y `allow_internal`. | `[{"id":"wa-conversation-agent","token_sha256":"<sha256-hex>","allow_internal":true}]` |
| `KB_CONFIDENCE_THRESHOLD` | `0.70` | Puntaje a partir del cual la confianza es `alta`. | `0.70` |
| `KB_MIN_RELEVANCE` | `0.45` | Puntaje mínimo para considerar un candidato. | `0.45` |
| `KB_AMBIGUITY_MARGIN` | `0.05` | Diferencia máxima entre los dos mejores puntajes para pedir aclaración. | `0.05` |
| `KB_LEXICAL_BONUS` | `0.05` | Bonus por coincidencia en búsqueda de texto, con tope `1.0`. | `0.05` |
| `KB_APPROVAL_POLICY_PATH` | `config/approval-policy.yaml` | Archivo YAML con los roles requeridos por tipo de contenido y parámetro. | `config/approval-policy.yaml` |
| `KB_SYNC_INTERVAL` | `24h` | Intervalo de `kb sync --loop` (duración positiva en `s`, `m` u `h`). | `24h` |
| `KB_MAX_CHARS_WHATSAPP` | `1000` | Largo máximo avisado por pieza al ingestar. | `1000` |
| `KB_EMBEDDING_TIMEOUT_S` | `5` | Timeout del proveedor de embeddings. | `5` |

La forma de `KB_CONSUMERS` también está en `config/consumers.example.yaml`. La app solo acepta estas variables: las credenciales de PostgreSQL del compose (`POSTGRES_*`) van en `.env.compose`, no en `.env`. Cada entorno tiene su propia credencial de servicio: en el repositorio solo se guarda el digest configurado en `KB_CONSUMERS`, nunca el token.

## Esquema y migraciones

Con `KB_DATABASE_URL` apuntando a PostgreSQL 16 con la extensión `vector` disponible y permisos para crearla:

```bash
uv run kb db migrate
```

Aplica `migrations/` y registra cada migración en `kb_schema_migration`; se puede repetir. La migración inicial fija `vector(768)`: cambiar la dimensión exige una migración nueva y reindexar los embeddings. Las operaciones que tocan varios repositorios comparten la transacción de `store.database`.

## Operación del contenido

El proceso completo (redactar, ingestar, proponer, aprobar, cargar parámetros, publicar, verificar, retirar, revertir y sincronizar) está en [`docs/proceso-de-contenido.md`](docs/proceso-de-contenido.md). Secuencia mínima en local:

```bash
uv run kb ingest
uv run kb params ingest
uv run kb publish --all-approved
uv run kb coverage
uv run kb coverage --published
uv run kb verify-pii
```

`kb coverage` revisa los temas de `content/coverage.yaml` contra los archivos fuente; con `--published` revisa las preguntas frecuentes públicas vigentes de la publicación activa (requiere `KB_DATABASE_URL`). `kb verify-pii` escanea también las publicaciones históricas. Publicar crea una release nueva y la activa sin reiniciar la API: la próxima consulta ya ve el contenido.

## API de consulta

Con `KB_DATABASE_URL`, `GOOGLE_API_KEY` y `KB_CONSUMERS` configurados:

```bash
uv run kb-api
```

Escucha en `KB_PORT` (8102 por defecto). `POST /v1/retrieve` exige `Authorization: Bearer <token-de-servicio>` y `X-Correlation-Id`; `GET /health` no exige autenticación y `GET /metrics` expone métricas Prometheus. El mismo `X-Correlation-Id` se devuelve en la respuesta y se usa en los logs y en el registro de consultas (sin texto del cliente).

`GET /health` responde 200 con `knowledgeVersion: "kv-N"` cuando hay base, extensión `vector`, índices HNSW/GIN y una publicación activa. Sin publicación activa o con dependencias caídas responde 503 con `status: degraded`; el servicio sigue escuchando y el healthcheck del contenedor lo considera vivo.

Errores frecuentes: `400 VALIDATION_ERROR` (cuerpo inválido o falta `X-Correlation-Id`), `401 UNAUTHENTICATED`, `403 AUDIENCE_FORBIDDEN` (audiencia interna sin permiso), `503 KNOWLEDGE_UNAVAILABLE` (base, índice o embeddings no disponibles: el agente deriva a una persona) y `500 INTERNAL_ERROR`. Una búsqueda sin coincidencias no es un error: devuelve 200 con `sin_respuesta`.

## Docker Compose

Desde esta carpeta, copiá los ejemplos y completá los valores de tu entorno (sin subirlos a git):

```bash
cp .env.example .env
cp .env.compose.example .env.compose
# completar POSTGRES_PASSWORD, GOOGLE_API_KEY y KB_CONSUMERS
docker compose --env-file .env.compose up --build
```

| Servicio | Rol |
| --- | --- |
| `kb-postgres` | PostgreSQL 16 + pgvector (`pgvector/pgvector:pg16`). |
| `wa-knowledge-rag` | Ejecuta `kb db migrate` y luego la API en el puerto 8102 (usuario no root). |
| `wa-knowledge-rag-sync` | Ejecuta `kb sync --loop` cada `KB_SYNC_INTERVAL` (por defecto `24h`). |

El scheduler es un servicio del compose, no un cron del host. Tras el primer arranque publicá el contenido mínimo con aprobaciones de prueba (`docs/proceso-de-contenido.md`) para que `/health` pase a 200.

## Contrato publicado

El agente conversacional consume [`shared/contracts/wa-knowledge-rag/retrieval.md`](../../shared/contracts/wa-knowledge-rag/retrieval.md) (v1): `POST /v1/retrieve` con credencial de servicio y `X-Correlation-Id`, y una respuesta con `knowledgeVersion` (`kv-N`), citas completas, `publishable` y `confidence` (`level` + `suggestion`). Incluye esquemas JSON, ejemplos de respuesta y tabla de errores. Los cambios incompatibles se publican en `/v2/retrieve` y la v1 se mantiene hasta que el consumidor migre.

## Pruebas y verificación

Desde esta carpeta:

```bash
uv run pytest -q                                  # unitarias (sin Docker ni red)
uv run pytest -m integration -q                   # integración y set dorado con FakeEmbedder (requiere Docker)
uv run pytest -m integration -q tests/golden/test_recall.py
GOOGLE_API_KEY=<clave> uv run pytest -q -m evals tests/golden/test_recall_evals.py -s   # modelo real
```

El set dorado (`tests/golden/cases.yaml`) comprueba recuperación, confianza y latencia. El gate con el modelo real exige `GOOGLE_API_KEY` y tiene objetivo de recall@5 ≥ 0.8 y p95 ≤ 800 ms; los umbrales de confianza se calibran con ese set.

Desde la raíz del repositorio:

```bash
python scripts/verify_ia.py
```

Encadena `uv sync --frozen`, `ruff check`, `ruff format --check`, `mypy src` y `pytest` con cobertura mínima del 80 %; el log completo queda en `ai/wa-knowledge-rag/.verify.log` si algo falla.

## Pendientes del banco

- **Valores de parámetros (P-2):** horarios, límites, umbrales, ventanas y teléfono de soporte están pendientes de definición. `content/parameters/parametros.yaml` contiene candidatos sintéticos marcados como `is_test: true`; las piezas que los usan solo se pueden publicar en `dev`/`test`. En un entorno real no se pueden publicar hasta cargar los valores del banco, aprobarlos sin `--test` y volver a renderizar/publicar.
- **Proceso de aprobación (S-3):** mientras el banco no lo defina, `config/approval-policy.yaml` exige `area_duena` y `compliance` por tipo de contenido y para parámetros; se puede cambiar editando ese archivo, sin tocar código. Las aprobaciones registradas con `--test` valen solo en `dev`/`test` y quedan marcadas como `approvalKind: "prueba"` en la cita; en `real` se rechazan.

Las preguntas frecuentes mínimas se redactaron como borrador con lo que dicen H3–H6/H9, sin cifras fijas. Para un entorno real hace falta la aprobación registrada del área dueña y de compliance; no basta con la aprobación de prueba usada en desarrollo.
