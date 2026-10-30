# Base de conocimiento RAG (H8)

Base Python 3.12 para contenido aprobado, recuperación con citas y confianza,
y separación de contenido público e interno. Diseño: `specs/conocimiento-rag/arquitectura.md`.
Esta entrega prepara configuración y tests; ingesta, pgvector, retrieval y API
se implementan en las siguientes tareas del hito.

Desde esta carpeta:

```sh
uv sync --frozen
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest -q --cov=src --cov-fail-under=80
```

Desde la raíz: `python ai/verify.py` verifica todos los componentes de IA.
Los tests base no necesitan Docker ni acceso al modelo. `tests/golden/` queda
reservado para el set dorado de retrieval.

Copiar `.env.example` a `.env` local o exportar las variables del entorno.
`Settings` usa los nombres del contrato H8 sin prefijo adicional: `PORT` (8102),
`DATABASE_URL`, `GOOGLE_API_KEY`, `EMBEDDING_MODEL` (text-embedding-004),
`EMBEDDING_DIMENSIONS` (768), `RAG_TOP_K` (5), `RAG_CONFIDENCE_HIGH_MIN`,
`RAG_CONFIDENCE_MEDIUM_MIN` y `LOG_LEVEL` (INFO).
Los valores vacíos se ignoran. Los secretos no tienen valor por defecto y usan
`SecretStr`; no registrar sus valores ni guardar `.env` en el repositorio.
Los umbrales quedan sin definir hasta calibrarlos; cuando se informan deben
estar entre 0 y 1, con el medio menor que el alto. Los servicios futuros deberán
exigir sus credenciales y umbrales antes de operar.

H8 no opera productos bancarios ni consume Kafka o Twilio. El contenido del
cliente y recuperado se trata como datos, nunca como instrucciones. La ingesta
rechaza PII, patrones de instrucciones y contenido no aprobado. No usar datos reales en fixtures.

### Publicación de contenido

Aplicar una vez `migrations/001_ingestion.sql` con `psql -v ON_ERROR_STOP=1 -f`:
crea pgvector y las tablas/índices de ingesta (vector de 768 dimensiones).
`Source` exige identidad lógica UUID, versión positiva, título, contenido, área,
estado `approved`, aprobador y fecha con zona horaria. La aprobación es un dato
de entrada de un proceso editorial confiable; esta biblioteca no autentica al editor.
`audience` es `internal` por defecto; solo el editor que aprueba puede marcar
explícitamente `public`. No se infiere publicidad por palabras clave del contenido.

`ingest(source, Embedder(GoogleProvider(settings), settings), connection, correlation_id)`
valida antes de llamar al proveedor. Usar una conexión psycopg exclusiva, sin una
transacción exterior, preferentemente `autocommit=True`; la función abre su propia
transacción. El proveedor genera lotes de hasta 32 textos con timeout de 30 segundos
y hasta tres intentos. Los tokens del chunker son unidades léxicas aproximadas,
no tokens exactos del modelo: ventanas de 500 con solapamiento de 50 por sección.

La migración distingue `kb_source.source_id` (identidad lógica de la fuente) de
`kb_source.id` (UUID de cada revisión); `kb_chunk.source_id` referencia la revisión.
Los metadatos del chunk conservan la identidad lógica para la cita. Retrieval debe
unir con `kb_source` y filtrar `status='approved'` y audiencia antes de recuperar.
La versión debe crecer; un lock por fuente y el índice único de revisión vigente
evitan publicaciones concurrentes inconsistentes. Un fallo de escritura revierte
también el retiro previo. Las aprobaciones quedan en DB; los logs solo incluyen
UUIDs técnicos, versión y cantidad de fragmentos, nunca aprobador ni contenido.

Integración aislada con Docker: `uv run pytest -m integration -q`; verifica publicación,
retiro, rechazo de versión repetida y rollback real sobre `pgvector/pgvector:pg17`.
Sin Docker se omite únicamente esta prueba. No hace falta API key para los tests.
