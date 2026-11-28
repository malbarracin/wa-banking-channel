---
title: H8 — Arquitectura de la base de conocimiento RAG (wa-knowledge-rag)
status: borrador
source: specs/wa-knowledge-rag/spec.md
updated: 2026-11-27T21:06:24.352Z
---

# Arquitectura — Base de conocimiento RAG (`wa-knowledge-rag`)

Parte de `specs/wa-knowledge-rag/spec.md` (RF-01…RF-10). Diseño, sin código de producto. Decisiones de peso en
`docs/adr/wa-knowledge-rag-000{1,2,3}-*.md`.

## 1. Ubicación y límites

- **Carpeta:** `ai/wa-knowledge-rag/` (todo el componente). **Paquete Python:** `wa_knowledge_rag`.
- **Contrato que publica:** `shared/contracts/wa-knowledge-rag/` → `retrieval.md`,
  `retrieval-request.v1.schema.json`, `retrieval-response.v1.schema.json`, `error.v1.schema.json`.
- **Contratos que consume:** ninguno (no llama a servicios de dominio ni a la capa MCP). Su única dependencia
  externa es el proveedor de embeddings (Google, por `GOOGLE_API_KEY`).
- **Consumidor:** el agente conversacional (`ai/wa-conversation-agent`), por HTTP.
- **No hace:** generar ni completar texto con un LLM, operar productos, recibir identidad del cliente, hablar con
  Twilio/Kafka. Devuelve solo texto aprobado y publicado.

## 2. Estructura

```
ai/wa-knowledge-rag/
  pyproject.toml  uv.lock  .env.example  README.md
  docs/proceso-de-contenido.md      # proceso de actualización y aprobación paso a paso (RF-03/RF-04)
  config/
    approval-policy.yaml            # aprobaciones requeridas por tipo de contenido (default: area_duena + compliance)
    consumers.example.yaml          # forma de la config de consumidores (sin tokens reales)
  content/                          # fuente de verdad del texto, versionada en git
    cuentas/ tarjetas/ seguros/ canal/   # una pieza por archivo .md con frontmatter YAML
    parameters/parametros.yaml      # juego de parámetros del banco (valores marcados prueba/real)
  migrations/                       # SQL versionado (0001_init.sql, …), aplicado por `kb db migrate`
  src/wa_knowledge_rag/
    config.py                       # pydantic-settings (variables KB_*)
    cli.py                          # comando `kb` (typer o argparse): ver §6
    content/                        # carga de archivos: frontmatter → modelo Pydantic `PieceSource`; hash de contenido
    ingestion/
      validate.py                   # metadatos obligatorios (RF-01), PII (RF-09), patrones de instrucción, largo (RF-10 aviso)
      params.py                     # detección `{{nombre}}` y resolución con el juego de parámetros vigente (RF-05)
      chunking.py                   # partición (§4.3)
      embeddings.py                 # puerto `Embedder` + `GoogleEmbedder` (google-genai) + `FakeEmbedder` (tests)
    workflow/
      states.py                     # máquina de estados de la pieza (RF-03)
      approvals.py                  # política de aprobación configurable + regla de aprobación de prueba
      publish.py                    # arma una release (versión del conocimiento), reindexa lo cambiado, rollback
    store/                          # psycopg 3 + pgvector: repositorios por tabla, transacciones
    retrieval/
      hybrid.py                     # vector + texto, fusión RRF
      confidence.py                 # nivel y sugerencia (RF-07)
      service.py                    # orquesta: auth de audiencia → embed → búsqueda → confianza → citas
    security/
      consumers.py                  # autenticación del consumidor y permiso de audiencia interna (RF-08)
      pii.py                        # detectores y enmascarado `***1234` (compartido por ingesta y logs)
    audit/                          # registro de eventos de contenido y de consultas (sin PII)
    api/
      app.py                        # FastAPI: POST /v1/retrieve, GET /health
      schemas.py                    # modelos Pydantic = esquemas del contrato v1
      errors.py                     # contrato de error
    observability/                  # structlog JSON, correlationId en contextvar, métricas Prometheus
  tests/ unit/ integration/ golden/   # golden/: set dorado de consultas → pieza esperada + nivel esperado
```

Regla de dependencias: `api` → `retrieval` → `store`; `cli` → `workflow` → `ingestion`/`store`. `retrieval` no
importa `workflow` ni `ingestion` (salvo `embeddings` y `security.pii`).

## 3. Contrato publicado — `retrieval` v1

### 3.1 Petición

`POST /v1/retrieve` — headers:

| Header | Obligatorio | Uso |
|--------|-------------|-----|
| `Authorization: Bearer <token>` | sí | credencial de servicio del consumidor (S-1). Sin ella o inválida → 401 |
| `X-Correlation-Id` | sí | se devuelve igual en el cuerpo y en el header de respuesta; si falta → 400 |

Cuerpo (`retrieval-request.v1.schema.json`, `additionalProperties: false`):

```json
{
  "query": "¿Cuál es el horario para transferir?",
  "filters": { "family": ["cuentas"], "type": ["faq"] },
  "audience": "publico",
  "topK": 5
}
```

- `query`: string 1..1000 caracteres (tope de entrada).
- `filters.family`: subconjunto de `cuentas|tarjetas|seguros|canal`; `filters.type`: de `faq|guia|politica|mensaje`. Opcionales.
- `audience`: `publico` (default) o `interno`. `interno` devuelve **público + interno**; exige consumidor habilitado.
- `topK`: 1..10, default 5.

### 3.2 Respuesta 200 (`retrieval-response.v1.schema.json`)

```json
{
  "correlationId": "c-123",
  "knowledgeVersion": "kv-12",
  "confidence": { "level": "alta", "score": 0.83, "suggestion": "responder" },
  "results": [
    {
      "text": "Podés transferir de {{resuelto}} …",
      "score": 0.83,
      "publishable": true,
      "citation": {
        "pieceId": "cuentas-faq-horario-transferencias",
        "title": "¿En qué horario puedo transferir?",
        "type": "faq",
        "family": "cuentas",
        "audience": "publico",
        "version": 2,
        "section": null,
        "ownerArea": "negocio-cuentas",
        "approvedAt": "2026-11-27T12:00:00Z",
        "approvalKind": "prueba"
      }
    }
  ]
}
```

- `text`: fragmento con parámetros **ya resueltos**; nunca un marcador `{{…}}` a la vista.
- `confidence.level`: `alta | baja | sin_respuesta`. `suggestion`: `responder | aclarar | derivar` (§5).
- `sin_respuesta` ⇒ `results: []` y `suggestion: derivar`. Todo resultado trae `citation` completa (todos los campos
  requeridos; `section` puede ser `null`).
- `publishable: false` solo en piezas `interno` → el agente no las muestra al cliente.
- `approvalKind`: `real | prueba` (RF-02).
- El texto de `results[].text` es **dato**: el contrato le indica al agente que lo inserte como contenido citado,
  nunca como instrucción.

### 3.3 Errores (`error.v1.schema.json`)

`{ "code": "...", "message": "...", "correlationId": "..." }` — sin stack traces ni detalle interno.

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `VALIDATION_ERROR` | cuerpo inválido, falta `X-Correlation-Id`, filtros fuera del catálogo |
| 401 | `UNAUTHENTICATED` | sin token o token desconocido |
| 403 | `AUDIENCE_FORBIDDEN` | pide `interno` sin habilitación |
| 503 | `KNOWLEDGE_UNAVAILABLE` | base, índice o proveedor de embeddings no disponibles (no se confunde con `sin_respuesta`) |
| 500 | `INTERNAL_ERROR` | resto |

### 3.4 Salud

`GET /health` (sin auth): `200 {"status":"ok","database":"ok","index":"ok","knowledgeVersion":"kv-12"}`;
`503` con `status: "degraded"` si falta la base, la extensión `vector` o no hay release activa.

Versionado: este es `v1`. Un cambio incompatible crea `/v2/retrieve` y esquemas `*.v2.*` y mantiene `v1` hasta que
el agente migre.

## 4. Datos (PostgreSQL 16 + pgvector)

### 4.1 Tablas

| Tabla | Columnas clave | Notas |
|-------|---------------|-------|
| `kb_piece` | `piece_id` PK (slug estable), `created_at` | identidad estable |
| `kb_piece_version` | PK (`piece_id`,`version`), `title`, `type`, `family`, `audience` (`publico`/`interno`), `owner_area`, `language` (`es`), `status` (`propuesta`,`borrador`,`en_revision`,`aprobada`,`publicada`,`retirada`), `body_template` (texto con `{{param}}`), `source_refs` (jsonb: secciones H3–H6/H9 de origen), `content_hash`, `valid_from`, `valid_to` null, `created_at` | cada cambio de texto/metadato = versión nueva; nunca se borra |
| `kb_approval` | `id`, `piece_id`,`version`, `role` (`area_duena`,`compliance`, …), `approved_at`, `is_test` bool, `note` | rol, nunca nombres de personas |
| `kb_parameter_version` | PK (`name`,`version`), `value`, `is_test` bool, `status`, `approved_at`, aprobaciones como `kb_approval` (con `subject_kind='parameter'`) | RF-05 |
| `kb_release` | `release_no` serial PK, `created_at`, `reason` (`publish`/`retire`/`rollback`/`sync`/`expire`), `parent_release_no`, `is_active` bool (índice único parcial `WHERE is_active`) | `knowledgeVersion = 'kv-' || release_no` |
| `kb_release_item` | (`release_no`, `piece_id`, `version`, `rendered_hash`) | qué versión de cada pieza sirve la release |
| `kb_chunk` | `id`, `piece_id`, `version`, `rendered_hash`, `chunk_no`, `section` null, `text` (resuelto), `embedding vector(768)`, `tsv tsvector` (generada: `to_tsvector('spanish', title || ' ' || text)`), `embedding_model`, metadatos denormalizados (`family`,`type`,`audience`) | se reutiliza entre releases si `rendered_hash` no cambia |
| `kb_audit` | `id`, `at`, `action` (`ingest`,`reject`,`submit`,`approve`,`publish`,`retire`,`rollback`,`sync`), `subject`, `version`, `role`, `release_no`, `detail` jsonb sin PII | RNF auditoría |
| `kb_query_log` | `at`, `correlation_id`, `consumer_id`, `release_no`, `cited` (jsonb `[pieceId@version]`), `level`, `top_score`, `latency_ms`, `query_len` | **sin texto de la consulta** (RF-09) |

Índices: `kb_chunk` HNSW `USING hnsw (embedding vector_cosine_ops)` (`m=16, ef_construction=64`;
`SET hnsw.ef_search=40` por consulta); GIN sobre `tsv`; btree (`family`,`type`,`audience`) y (`piece_id`,`version`,`rendered_hash`).

### 4.2 Release y “publicar sin reiniciar”

Una consulta siempre filtra por los chunks de la release activa (`JOIN kb_release_item` de la fila `is_active`), y
además `valid_from <= now() AND (valid_to IS NULL OR valid_to > now())`. Publicar, retirar o volver atrás crea una
**release nueva** en una transacción (copia los ítems de la anterior y cambia los afectados) y mueve `is_active`.
Rollback = release nueva cuyo contenido es el de una release anterior (la versión del conocimiento cambia, RF-04).
`kb sync` sin cambios aprobados no crea release. Vencimiento de vigencia: lo filtra la consulta al instante; `kb sync`
además crea una release `expire` para que la versión del conocimiento lo refleje.

### 4.3 Partición y embeddings

- `faq` y `mensaje`: un chunk por pieza (pregunta + respuesta). `guia` y `politica`: un chunk por sección `##`,
  partida a ≤ 1200 caracteres con solapamiento de 150; `section` = título de la sección.
- Texto a embeber: `title + "\n" + text` resuelto. `task_type` `RETRIEVAL_DOCUMENT` al indexar, `RETRIEVAL_QUERY` al
  consultar.
- Modelo por defecto: **`gemini-embedding-001` con `output_dimensionality=768`** (SDK `google-genai`), configurable con
  `KB_EMBEDDING_MODEL` / `KB_EMBEDDING_DIMENSIONS`. Se eligió en lugar de `text-embedding-004` porque ese modelo fue
  retirado por el proveedor; la dimensión 768 es la del stack. Cambiar modelo o dimensión exige `kb reindex --all`
  (columna `embedding_model` lo detecta: la consulta solo usa chunks del modelo configurado). Ver ADR-0001.
- La consulta se **enmascara** (`security.pii`) antes de embeberla y de loguearla: los datos personales no ayudan a
  recuperar y no salen al proveedor.
- Tests: `FakeEmbedder` determinístico (sin red). El set dorado se corre con el modelo real cuando hay `GOOGLE_API_KEY`.

## 5. Recuperación y confianza

1. Autenticar consumidor; si `audience=interno` y no está habilitado → 403 antes de buscar.
2. Embedding de la consulta (timeout `KB_EMBEDDING_TIMEOUT_S=5`, 1 reintento; si falla → 503).
3. Dos búsquedas sobre la release activa con los filtros: vector (cosine, top 20) y texto
   (`websearch_to_tsquery('spanish', q)`, `ts_rank_cd`, top 20). Fusión **RRF** (`k=60`) para el orden; se agrupa por
   pieza (mejor chunk por pieza) y se cortan `topK`.
4. **Score de confianza** de cada resultado = similitud coseno (`1 - distancia`) de su mejor chunk; si además aparece en
   la búsqueda de texto suma `KB_LEXICAL_BONUS=0.05` (tope 1.0). Es el `score` que se devuelve.
5. Nivel y sugerencia (`retrieval/confidence.py`), umbrales por configuración (RF-07, S-4):

| Condición (s1 = mejor score, s2 = segundo de otra pieza) | `level` | `suggestion` | `results` |
|---|---|---|---|
| no hay candidatos o s1 < `KB_MIN_RELEVANCE` (0.45) | `sin_respuesta` | `derivar` | `[]` |
| s1 ≥ `KB_CONFIDENCE_THRESHOLD` (0.70) | `alta` | `responder` | topK |
| entre ambos y hay ≥2 piezas con s ≥ `KB_MIN_RELEVANCE` y s1 − s2 ≤ `KB_AMBIGUITY_MARGIN` (0.05) | `baja` | `aclarar` | candidatos con cita |
| entre ambos, resto | `baja` | `derivar` | candidatos con cita |

Los valores por defecto son iniciales y **se calibran con el set dorado** (`tests/golden/`), que tiene que cubrir R1
(horario de transferencias → `alta`), R2 (tema no documentado → `sin_respuesta`/`baja` + `derivar`) y una pregunta
ambigua (→ `baja` + `aclarar`). Si la calibración cambia los defaults, se actualizan acá y en `.env.example`.

La base **nunca genera texto**: no hay LLM en este componente.

## 6. Flujo de contenido (comando `kb`)

Fuente del texto: archivos en `content/` (git = historial de redacción). Estado, aprobaciones y releases: en la base
(fuente de verdad de lo publicado). Frontmatter obligatorio de cada pieza:
`id, title, type, family, audience, owner_area, source_refs, valid_from` (+ `valid_to`, `section` opcionales);
`version` lo asigna la ingesta.

| Comando | Efecto |
|---------|--------|
| `kb db migrate` | aplica `migrations/` |
| `kb ingest [ruta]` | valida y registra versión nueva si cambió `content_hash`, en estado `borrador` (o `propuesta` con `--proposal`, para soporte). Rechaza: metadato faltante (dice cuál), PII, patrones de instrucción (“ignorá las instrucciones”, “system:”, “actuá como”…), parámetro desconocido. Avisa (no bloquea) si supera `KB_MAX_CHARS_WHATSAPP` (1000) |
| `kb submit <id>` | `propuesta`/`borrador` → `en_revision` |
| `kb approve <id> --role <rol> [--test]` | registra aprobación de la versión actual; pasa a `aprobada` cuando tiene todas las de `approval-policy.yaml` |
| `kb params ingest` / `kb params approve <name> --role … [--test]` | juego de parámetros (RF-05) |
| `kb publish <id>\|--all-approved` | verifica aprobaciones, parámetros con valor aprobado, regla de entorno; renderiza, embebe solo lo cambiado, crea release |
| `kb retire <id>` | saca la pieza de la release (nueva release) |
| `kb rollback [--to kv-N \| <id> --to-version N]` | release nueva con el contenido anterior |
| `kb sync` | actualización periódica: `ingest` de lo cambiado + `publish --all-approved` + vencimientos; sin cambios → no hace nada. Frecuencia `KB_SYNC_INTERVAL` (default 24 h) la dispara un scheduler externo (cron o servicio del compose: lo decide devops); `kb sync --loop` como alternativa local |
| `kb verify-pii` | escanea todo lo publicado; sale ≠ 0 si encuentra algo (RF-09, CI) |
| `kb list [--family …] [--status …]` | inventario; `kb coverage` controla los temas mínimos de RF-02 contra `content/coverage.yaml` |

Reglas de entorno (`KB_ENVIRONMENT = dev | test | real`): en `real`, `publish` rechaza piezas o parámetros cuya
aprobación sea solo de prueba (`is_test`) y rechaza `--test` en `approve`. Cambiar un parámetro aprobado → `publish`
re-renderiza y reindexa las piezas que lo usan (`rendered_hash` cambia).

Contenido mínimo (D-1): las piezas de RF-02 las redacta el equipo en `content/` con `source_refs` hacia las secciones
de H3–H6/H9 y sin cifras; en dev/test se aprueban con `--test`. `content/coverage.yaml` lista los temas mínimos por
familia y qué pieza cubre cada uno.

## 7. Seguridad

- **Consumidores (RF-08, S-1):** `KB_CONSUMERS` (JSON por entorno) =
  `[{"id":"wa-conversation-agent","token_sha256":"<hex>","allow_internal":true}]`. Se compara el SHA-256 del bearer en
  tiempo constante. El token en claro vive solo en el entorno del agente. La habilitación de `interno` la decide esta
  config, nunca el texto de la pregunta. No hay credencial de sesión del cliente: el componente no recibe identidad.
- **Envenenamiento / inyección indirecta:** solo entra contenido aprobado; la ingesta bloquea PII y patrones de
  instrucción; cada versión queda con aprobaciones y auditoría. El contrato marca `text` como dato citado.
- **PII:** `security/pii.py` detecta DNI/CUIT, teléfono, correo, PAN (Luhn), CBU/CVU (22 dígitos) y alias; en ingesta
  rechaza salvo que el valor venga de un parámetro aprobado; en logs enmascara a `***1234`.
- **Consumo:** `query` ≤ 1000, `topK` ≤ 10, timeout del embedding, `statement_timeout` de Postgres 2 s.
- **Secretos:** `GOOGLE_API_KEY`, `KB_DATABASE_URL` (con contraseña), `KB_CONSUMERS` solo por entorno;
  `.env.example` con nombres y valores vacíos. Dependencias fijadas, `uv.lock`, `pip-audit` en CI.

## 8. Observabilidad

- `X-Correlation-Id` → contextvar → todos los logs (structlog JSON) y `kb_query_log`; se devuelve en header y cuerpo.
- Log por consulta: `correlationId`, `consumerId`, `knowledgeVersion`, piezas citadas, `level`, `topScore`,
  `latencyMs`, consulta enmascarada.
- Métricas (`/metrics`, Prometheus): `kb_retrieve_latency_seconds` (histograma), `kb_retrieve_total{level}`,
  `kb_embedding_errors_total`, `kb_active_release`, `kb_published_pieces{family,audience}`.
- Latencia objetivo (S-5): p95 ≤ 800 ms por consulta en el runtime local con el proveedor de embeddings respondiendo;
  la búsqueda en Postgres sola ≤ 100 ms p95.

## 9. Configuración y runtime

| Variable | Default | Uso |
|----------|---------|-----|
| `KB_PORT` | `8102` | puerto HTTP (convención del programa) |
| `KB_DATABASE_URL` | — | `postgresql://…` (Postgres con pgvector, solo de este componente) |
| `GOOGLE_API_KEY` | — | embeddings |
| `KB_EMBEDDING_MODEL` / `KB_EMBEDDING_DIMENSIONS` | `gemini-embedding-001` / `768` | |
| `KB_ENVIRONMENT` | `dev` | `dev|test|real` |
| `KB_CONSUMERS` | — | §7 |
| `KB_CONFIDENCE_THRESHOLD` / `KB_MIN_RELEVANCE` / `KB_AMBIGUITY_MARGIN` / `KB_LEXICAL_BONUS` | 0.70 / 0.45 / 0.05 / 0.05 | §5 |
| `KB_APPROVAL_POLICY_PATH` | `config/approval-policy.yaml` | RF-03, S-3 |
| `KB_SYNC_INTERVAL` | `24h` | RF-04 |
| `KB_MAX_CHARS_WHATSAPP` | `1000` | RF-10 (aviso) |
| `KB_EMBEDDING_TIMEOUT_S` | `5` | |

Stack: Python 3.12, uv, FastAPI + uvicorn, pydantic-settings, psycopg 3 + `pgvector`, `google-genai`, structlog,
`prometheus-client`, pytest + pytest-asyncio + testcontainers (`pgvector/pgvector:pg16`), ruff, mypy --strict.
El puerto local de Postgres y el servicio del scheduler los fija devops en el compose.

## 10. Trazabilidad requisito → diseño

| Requisito | Dónde |
|-----------|-------|
| RF-01 | frontmatter + `ingestion/validate.py`; `kb_piece_version` |
| RF-02 | `content/` + `content/coverage.yaml` + `kb coverage`; `approvalKind` en la cita |
| RF-03 | `workflow/states.py`, `approvals.py`, `config/approval-policy.yaml`, comandos §6, `docs/proceso-de-contenido.md` |
| RF-04 | `kb_piece_version`, `kb_release*`, `kb sync`, `kb rollback`, vigencia |
| RF-05 | `kb_parameter_version`, `ingestion/params.py` |
| RF-06 | `POST /v1/retrieve`, §3.2, §5 |
| RF-07 | `retrieval/confidence.py`, §5 |
| RF-08 | `security/consumers.py`, `audience`, `publishable` |
| RF-09 | `security/pii.py`, `kb verify-pii`, `kb_query_log` sin texto |
| RF-10 | aprobación de compliance (política), aviso de largo |
| RNF | §3.4 salud, §3.3 503, §8, §9 |

## 11. Orden sugerido de construcción

1. Base del proyecto Python + migraciones + contrato `shared/contracts/wa-knowledge-rag/` (desbloquea al agente).
2. Ingesta y validación (metadatos, PII, instrucciones, parámetros).
3. Flujo de aprobación, releases, publish/retire/rollback/sync.
4. Recuperación híbrida + confianza + API.
5. Contenido mínimo (`content/` + coverage) y `docs/proceso-de-contenido.md`.
6. Set dorado y calibración de umbrales; tests de integración con testcontainers.
