---
title: Arquitectura — Base de conocimiento RAG (H8)
status: propuesta
updated: 2026-10-30T21:00:00.000Z
---

# Arquitectura — Base de conocimiento RAG (H8)

## Objetivo

Diseñar la arquitectura del componente **H8 (Base de conocimiento RAG)** para centralizar conocimiento oficial del banco, con recuperación híbrida (vector + texto), citas, nivel de confianza, separación público/interno, ingesta controlada por aprobación y trazabilidad. Debe integrarse con H9 vía contrato publicado en `shared/contracts/wa-knowledge-rag/`, sin exponer operaciones bancarias ni datos transaccionales.

## Alcance

**Dentro de alcance:**
- Ingesta de contenido aprobado desde fuentes oficiales.
- Indexación en PostgreSQL + pgvector con partición por secciones, embeddings y búsqueda híbrida.
- API/retrieval con citas, confianza, filtrado por audiencia (público/interno).
- Control de versiones, trazabilidad (aprobaciones, reindexación).
- Filtrado/prevención de PII en ingesta.
- Contrato para consumo por H9.

**Fuera de alcance:**
- Operaciones bancarias (H1–H6, H7).
- Orquestación/conversación (H9), ciclo técnico de mensaje (H0.2).
- Twilio/Kafka.
- Datos transaccionales en tiempo real.

## Límites con otros componentes (ia-boundaries)

| Componente | Relación | Notas |
|------------|----------|-------|
| H7 (MCP) | Independiente | No consume capacidades MCP; H8 es solo conocimiento documental. |
| H9 (Agente) | Consumidor | Consume contrato de retrieval `shared/contracts/wa-knowledge-rag/retrieval.md`. Nunca llama directo a servicios H1–H6. |
| H0.2 (Orquestador) | Indirecto | No integra con Kafka/Twilio. |
| Servicios H1–H6 | Independiente | Las reglas vigentes prevalecen ante contradicción con contenido RAG. |

## Estructura del paquete

Ubicación: `ai/wa-knowledge-rag/`

```
ai/wa-knowledge-rag/
├── pyproject.toml
├── uv.lock
├── README.md
├── .env.example
├── src/
│   └── wa_knowledge_rag/
│       ├── __init__.py
│       ├── config.py           # pydantic-settings (env)
│       ├── api/                # FastAPI app / endpoints de retrieval/health
│       │   ├── __init__.py
│       │   ├── deps.py
│       │   ├── health.py
│       │   └── retrieval.py    # endpoint de consulta híbrida
│       ├── content/            # Fuentes aprobadas, clasificación público/interno, validación
│       │   ├── __init__.py
│       │   ├── classifier.py   # público vs interno
│       │   ├── pii_filter.py   # rechazar/alertar PII
│       │   └── sources.py
│       ├── ingestion/          # Carga, partición, embeddings, aprobación
│       │   ├── __init__.py
│       │   ├── chunker.py      # partición por secciones ~500 tokens, solapamiento ~50
│       │   ├── embedder.py     # embeddings con modelo de project.yaml
│       │   ├── pipeline.py     # orquestación con transacciones/versionado
│       │   └── validator.py    # rechaza PII, valida estado aprobado
│       ├── store/              # Acceso a Postgres + pgvector
│       │   ├── __init__.py
│       │   ├── db.py           # conexión/psycopg
│       │   ├── migrations/     # SQL versionadas (aplicadas por script/alembic)
│       │   ├── models.py       # entidades
│       │   └── repo.py         # queries híbridas, filtros por audience/version
│       └── retrieval/          # Búsqueda híbrida, fusión, confianza, citas
│           ├── __init__.py
│           ├── hybrid.py       # KNN + tsvector + RRF
│           ├── reranker.py     # (opcional, mantenido simple)
│           ├── scorer.py       # cálculo de confianza high/medium/low
│           └── formatter.py    # formatea con citation + confidence
└── tests/
    ├── unit/
    ├── integration/            # testcontainers (Postgres+pgvector)
    └── golden/                 # set dorado con recall@5, MRR
```

**Responsabilidades clave:**
- `ingestion/`: solo procesa fuentes con `status=approved`. Rechaza PII, particiona, genera embeddings con dimensión configurada, reindexa por versión en transacción.
- `store/`: gestiona esquema/migraciones, índices HNSW y GIN en español, filtros por audiencia/version.
- `retrieval/`: búsqueda híbrida, fusión, scoring para `confidence`, asegura que solo `public` se expone al cliente.
- `api/`: expone retrieval y health; nunca expone contenido interno a respuestas públicas.

## Modelo de datos (Postgres + pgvector)

Basado en `rag-pgvector` skill. Dimensión = `ia.embedding_dimensions` (por defecto 768, debe coincidir con `text-embedding-004`).

**Tablas mínimas:**

```sql
-- Fuentes aprobadas
CREATE TABLE kb_source (
  id UUID PRIMARY KEY,
  source_id UUID NOT NULL,
  title TEXT NOT NULL,
  owner_area TEXT NOT NULL,
  audience TEXT NOT NULL CHECK (audience IN ('public','internal')),
  version INTEGER NOT NULL,
  approved_by TEXT NOT NULL,
  approved_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('draft','approved','retired')),
  source_uri TEXT,
  metadata JSONB DEFAULT '{}',
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (source_id, version)
);

-- Índice parcial de revisión vigente por identidad lógica de fuente
CREATE UNIQUE INDEX kb_source_current ON kb_source(source_id) WHERE status = 'approved';

-- Fragmentos con embeddings y búsqueda textual
CREATE TABLE kb_chunk (
  id UUID PRIMARY KEY,
  source_id UUID NOT NULL REFERENCES kb_source(id) ON DELETE RESTRICT,
  version INTEGER NOT NULL,
  ordinal INTEGER NOT NULL,
  content TEXT NOT NULL,
  section TEXT,
  metadata JSONB DEFAULT '{}',
  embedding vector(<EMBEDDING_DIM>),
  tsv tsvector,
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (source_id, version, ordinal)
);
```

**Índices:**
- `CREATE INDEX idx_kb_chunk_embedding_hnsw ON kb_chunk USING hnsw (embedding vector_cosine_ops);`
- `CREATE INDEX idx_kb_chunk_tsv_gin ON kb_chunk USING gin (tsv);` (con `to_tsvector('spanish', content)`)
- `CREATE INDEX idx_kb_chunk_source_version ON kb_chunk (source_id, version);`
- `CREATE INDEX idx_kb_source_audience_status ON kb_source (audience, status);`

**Notas:**
- `source_id` es la identidad lógica de la fuente (agrupación de versiones). `id` es la clave física de la fila.
- Solo se consultan `kb_source.status='approved'` y chunks de la versión vigente por `source_id` (índice parcial `kb_source_current`).
- Al reindexar, se crea nueva versión con el mismo `source_id` y se marca la anterior como `retired` en transacción atómica (no mezclar versiones).
- La migración de esquema existente agrega `source_id`, reconstruye la identidad lógica por título y reemplaza `UNIQUE (title, version)` por `UNIQUE (source_id, version)`.
- Trigger/función para mantener `tsv` actualizado con configuración `spanish`.

## Ingesta (flujo)

1. **Validación**: rechaza contenido con patrones de PII (documento, teléfono, CBU, nombre+datos sensibles). Solo se acepta si `status=approved` con trazabilidad (`approved_by`, `approved_at`).
2. **Clasificación**: asigna `audience` (`public`/`internal`) según política. Cada chunk hereda `audience`/`version` de la fuente.
3. **Partición**: divide por estructura (títulos/secciones), ~500 tokens, solapamiento ~50. Conserva `section`, `title`, `source_id`, `version`.
4. **Embeddings**: genera con `ia.embedding_model` (text-embedding-004), lotes con reintento. Verifica dimensión coincide con columna.
5. **Indexación**: inserta chunks + embeddings + `tsv` (`spanish`), actualiza índices. Todo en transacción; al activar nueva versión se retira la anterior.
6. **Trazabilidad**: registra operación (quién/cuándo/qué/cambió/versión). Logs sin PII.

## Recuperación híbrida y confianza

**Estrategia:** KNN por coseno (vector) + búsqueda textual (`ts_rank` con `tsvector` español), fusionadas con **Reciprocal Rank Fusion (RRF)**. Se filtra por `audience` según contexto de consulta (`audience` requerido en entrada). El agente que responde al cliente **solo** recibe `public`.

**Respuesta por fragmento:**
```json
{
  "content": "...",
  "citation": {
    "source_id": "...",
    "title": "...",
    "section": "...",
    "version": 123,
    "source_uri": "..."
  },
  "score": 0.87
}
```

**Cálculo de confianza:** `confidence` = `high|medium|low` basado en mejor puntaje/distancia tras fusión, con umbrales configurables (calibrados con set dorado). **Si `confidence == 'low'` el consumidor NO debe responder con ese contenido** (regla "no inventar").

**Reglas:**
- Si solo hay resultados `internal` y consulta es para respuesta directa al cliente → devolver `sin respuesta suficiente` / `confidence: low` y derivar (sin exponer interno).
- Si hay mezcla público/interno → usar solo `public` para texto al cliente; interno solo para razonamiento/derivación.
- Siempre incluir citas.

## Contrato de retrieval

Publicar en `shared/contracts/wa-knowledge-rag/retrieval.md` (y opcionalmente schemas JSON).

**Entrada:**
```json
{
  "query": "¿Cuáles son los requisitos para tarjeta de crédito?",
  "audience": "public|internal",
  "top_k": 5,
  "correlationId": "..."
}
```

**Salida exitosa:**
```json
{
  "results": [
    {
      "content": "...",
      "citation": {"source_id": "...", "title": "...", "section": "...", "version": 1, "source_uri": "..."},
      "score": 0.91
    }
  ],
  "confidence": "high|medium|low",
  "version": "kb-2026-10-30-v1",
  "correlationId": "..."
}
```

**Salida sin respuesta suficiente:**
```json
{
  "results": [],
  "confidence": "low",
  "reason": "sin_respuesta_suficiente",
  "correlationId": "..."
}
```

**Errores:** seguir patrón similar a otros contratos, sin stack traces. Incluir `correlationId` para trazabilidad.

## Seguridad, privacidad y auditoría

- **Sin PII en KB:** ingesta rechaza documento/teléfono/CBU/nombre con datos sensibles. No almacenar datos transaccionales del cliente.
- **Separación público/interno:** filtrado estricto por `audience`. Nunca exponer fragmentos `internal` al cliente.
- **Credenciales fuera del prompt:** no aplica directamente a H8 (H8 es retrieval), pero todo acceso/auditoría debe mantener `correlationId`.
- **Logs sin PII:** estructurados JSON, enmascarar si aparece. Sin contenido completo sensible en logs.
- **Trazabilidad:** registrar aprobaciones, versiones, reindexaciones, quién/cuándo/qué. Consultas relevantes trazadas con `correlationId`.
- **Solo contenido aprobado:** `status='approved'` únicamente.

## Configuración

Leer de `project.yaml` → `ia.*` y variables de entorno (pydantic-settings). `.env.example` sin secretos.

| Variable | Descripción | Default |
|----------|-------------|---------|
| `PORT` | Puerto HTTP (H8) | `8102` (según project.yaml) |
| `DATABASE_URL` | Postgres con pgvector | `postgresql+psycopg://...` |
| `EMBEDDING_MODEL` | Modelo de embeddings | `text-embedding-004` |
| `EMBEDDING_DIMENSIONS` | Dimensión vector | `768` |
| `RAG_TOP_K` | top_k por defecto | `5` |
| `RAG_CONFIDENCE_HIGH_MIN` | Umbral alto | (configurable) |
| `RAG_CONFIDENCE_MEDIUM_MIN` | Umbral medio | (configurable) |
| `LOG_LEVEL` | Nivel de logs | `INFO` |

## Puertos y stack (según ia-stack.md)

- **Python:** 3.12, `uv` (`uv sync`, `uv run`, `uv add`), layout `src/wa_knowledge_rag/` + `tests/` + `pyproject.toml`
- **Calidad:** `ruff` (lint+format), `mypy --strict`
- **Tests:** `pytest`, `pytest-asyncio`, `testcontainers` (Postgres+pgvector)
- **Config:** `pydantic-settings`
- **HTTP:** `fastapi` + `uvicorn` (API de retrieval/health)
- **DB:** `psycopg` 3 + `pgvector`, migraciones versionadas
- **Logs:** `structlog` JSON con `correlationId`
- **Embeddings:** según `ia.embedding_model`/`ia.embedding_dimensions` de `project.yaml`

## Observabilidad

- `correlationId` en requests/responses y logs.
- Métricas: latencia por consulta, tasa `low`/derivación, recall@5 en golden set.
- Logs estructurados sin PII/contenido sensible.

## ADRs necesarios

Crear en `docs/adr/`:
- **ADR-011 — Conocimiento en PostgreSQL + pgvector**: justificar elección (consistente con stack, transaccional, HNSW, control total, sin Redis para embeddings/conocimiento).
- **ADR-012 — Búsqueda híbrida con citas y confianza**: híbrida (vector+texto) + RRF, obligatoriedad de citas, regla "no inventar" con `confidence: low`.
- **ADR-013 — Solo contenido aprobado, separación público/interno**: gating por aprobación, filtrado por audiencia, prevención PII.

## Criterios de aceptación (alineados a spec)

- [x] Solo contenido aprobado indexado; borradores/rechazados/retirados no consultables.
- [x] Recuperación con citas y `confidence` (`high|medium|low`).
- [x] Separación público/interno; solo `public` para respuesta al cliente.
- [x] Alineación con políticas H4–H6 (regla prevalece sobre RAG).
- [x] Actualización/aprobación/versionado con trazabilidad; reindexado atómico.
- [x] Sin PII/datos transaccionales en KB; ingesta rechaza PII.
- [x] Contrato publicado en `shared/contracts/wa-knowledge-rag/retrieval.md`.
- [x] Estructura, datos, seguridad, observabilidad y ADRs definidos.

## Checklist ia-architecture

- [x] Estructura del paquete con responsabilidades por módulo
- [x] Contratos consumidos y publicados, con esquema y versión
- [x] Modelo de datos (pgvector) con índices y retención
- [x] Recorrido de la credencial de sesión sin pasar por el prompt del modelo (N/A directo a H8; se mantiene trazabilidad con correlationId)
- [x] Guardrails, PII y auditoría definidos
- [x] `correlationId` de punta a punta y métricas
- [x] ADRs necesarios escritos en `docs/adr/`
