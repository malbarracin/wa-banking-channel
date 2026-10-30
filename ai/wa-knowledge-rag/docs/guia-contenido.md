# Guía de contenido — base de conocimiento RAG (H8)

Cómo se prepara, aprueba, publica, versiona, retira y evalúa el conocimiento que H8 entrega a H9.
Complementa [docs/arquitectura.md](arquitectura.md) y el contrato
[`shared/contracts/wa-knowledge-rag/retrieval.md`](../../../shared/contracts/wa-knowledge-rag/retrieval.md).

> Regla de oro: a la base solo entra contenido **aprobado**, **sin PII** y **clasificado**. H8 no
> autentica al editor ni decide la aprobación: confía en el proceso editorial que entrega
> `approved_by` y `approved_at`.

## 1. Qué contenido se publica

| Sí | No |
|---|---|
| Productos, políticas, FAQs, guías de respuesta y mensajes aprobados por Compliance/Legal | Datos personales del cliente (documento, teléfono, nombre + saldo/cuenta/tarjeta, CBU) |
| Contenido alineado a las reglas vigentes de H4–H6 | Saldos, movimientos o cualquier dato transaccional en tiempo real |
| Texto en Markdown con encabezados por sección | Instrucciones para el modelo (`ignorá las instrucciones…`, `system:`, `<|im_start|>`) |
| Contenido que puede mostrarse tal cual al cliente (`public`) o solo para razonamiento (`internal`) | Contenido en borrador, rechazado o sin trazabilidad de aprobación |

Ante contradicción entre el conocimiento y una regla vigente del banco, **prevalece la regla del
banco**: se actualiza o retira el contenido, no se publica una excepción.

## 2. Formato de una fuente

Una `Source` (`wa_knowledge_rag.ingestion.validator.Source`) tiene:

| Campo | Regla |
|---|---|
| `source_id` | UUID **lógico** de la fuente; se mantiene igual entre versiones |
| `title` | 1–500 caracteres; se usa en la cita y como sección del preámbulo |
| `content` | 1–1 000 000 caracteres; Markdown. Cada encabezado `#`…`######` abre una sección |
| `owner_area` | Área responsable (también se valida contra PII) |
| `version` | Entero > 0; **debe crecer** para la misma `source_id` |
| `status` | Debe ser `approved` |
| `approved_by` | No vacío: quién aprobó |
| `approved_at` | Fecha con zona horaria (`TIMESTAMPTZ`) |
| `audience` | `internal` por defecto; `public` solo si el editor lo declara explícitamente |

El chunker corta por sección en ventanas de 500 tokens léxicos con 50 de solapamiento (aproximación,
no tokens exactos del modelo). Un contenido sin encabezados queda en una sola sección con el título.

## 3. Público vs interno

- `public`: puede usarse en el texto que H9 envía al cliente. Es la audiencia que H9 pide para
  respuestas directas.
- `internal`: solo para razonamiento/derivación de H9 o soporte. **Nunca** se expone al cliente; una
  consulta de cliente (`for_client=True`, `audience=public`) no lo recupera.

La audiencia no se infiere por palabras clave: se toma de la declaración del editor. Cada consulta
filtra una audiencia exacta (`public` no incluye `internal`).

## 4. Cómo agregar contenido (paso a paso)

No hay endpoint ni CLI de ingesta: se publica llamando a `ingest(...)` desde un proceso confiable.

1. **Preparar la fuente** con los campos de la tabla anterior. Usar un `source_id` nuevo para una fuente
   nueva y el mismo para una revisión.
2. **Aplicar el esquema de ingesta** una vez (si no está aplicado):
   `psql -v ON_ERROR_STOP=1 -f migrations/001_ingestion.sql` (es el esquema que espera `ingest`; el
   empaquetado de `store/migrations/` es el que usan retrieval y los tests).
3. **Publicar** con una conexión psycopg exclusiva, sin transacción exterior y preferentemente
   `autocommit=True` (la función abre su propia transacción):

```python
import asyncio
from datetime import UTC, datetime
from uuid import UUID

import psycopg

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embedder import Embedder, GoogleProvider
from wa_knowledge_rag.ingestion.pipeline import ingest
from wa_knowledge_rag.ingestion.validator import Source

settings = Settings()
source = Source(
    source_id=UUID("00000000-0000-4000-8000-000000000001"),  # identidad lógica estable
    title="Guía de ejemplo (sintética)",
    content="# Requisitos\nTexto de ejemplo aprobado.\n\n# Horarios\nOtro texto de ejemplo.",
    owner_area="Negocio",
    version=1,
    status="approved",
    approved_by="compliance-role",
    approved_at=datetime(2026, 1, 1, tzinfo=UTC),
    audience="public",
)

with psycopg.connect(settings.database_url.get_secret_value(), autocommit=True) as connection:
    chunks = asyncio.run(
        ingest(
            source,
            Embedder(GoogleProvider(settings), settings),
            connection,
            UUID("00000000-0000-4000-8000-0000000000ff"),  # correlationId de la publicación
        )
    )
    print(f"fragmentos publicados: {chunks}")
```

4. **Verificar** que el contenido se recupera con la audiencia correcta (`POST /retrieval` o
   `KnowledgeRepository.search`) y que la cita apunta al `source_id` lógico.

Si la validación falla, no se llama al proveedor ni se escribe en la base. Códigos posibles:
`APPROVAL_REQUIRED`, `APPROVAL_TRACE_REQUIRED`, `APPROVAL_TIMEZONE_REQUIRED`, `PII_DETECTED`,
`INSTRUCTIONS_DETECTED`, `DATABASE_DIMENSION_MISMATCH`, `VERSION_MUST_INCREASE`,
`EMBEDDING_KEY_REQUIRED`, `EMBEDDING_UNAVAILABLE`, `INVALID_EMBEDDING`, `INVALID_WINDOW`,
`EMPTY_CONTENT`.

## 5. Versionado, retiro y reindexación

- Cada publicación crea una **revisión** nueva (`kb_source.id`) de la misma fuente (`source_id`).
- La versión debe ser mayor que la máxima registrada; si no, `VERSION_MUST_INCREASE`.
- En una sola transacción se retira (`retired`) la revisión aprobada anterior y se inserta la nueva con
  sus chunks y embeddings. Si algo falla, se revierte también el retiro: nunca quedan dos revisiones
  aprobadas de la misma fuente.
- **Reindexar** = publicar la misma fuente con `version + 1`; se regeneran chunks y embeddings.
- **Retirar** una fuente hoy solo ocurre al publicar una versión que la reemplace. No hay baja sin
  reemplazo ni rollback a una versión anterior (pendiente, ver el README).

## 6. Cómo se evalúa

### Set dorado (`tests/golden/`)

`tests/golden/retrieval.json` contiene un corpus **sintético**:

```json
{
  "sources": [{"key": 1, "title": "…", "content": "…", "audience": "public"}],
  "queries": [{"query": "…", "axis": 1, "expected": [1]}],
  "outside": ["consulta fuera de la base"]
}
```

El runner (`tests/golden/test_golden_retrieval.py`) inserta las fuentes en un PostgreSQL real con
testcontainers, usando vectores ortogonales de 768 dimensiones (1.0 en el eje `key`) y
`metadata.source_id` lógico. Luego mide:

| Prueba | Qué verifica |
|---|---|
| `test_recall_at_five` | `recall@5` promedio **≥ 0.8** y `confidence: high` para cada consulta esperada |
| `test_rrf_combines_disagreeing_vector_and_spanish_text_rankings` | La fusión RRF combina ramas que no coinciden y el stemming español recupera variantes |
| `test_outside_and_internal_never_answer_client` | Consultas fuera de base y contenido interno devuelven `low` + `sin_respuesta_suficiente` |
| `test_internal_requires_reasoning` | `internal` solo se recupera con `for_client=False` |

Ejecución (necesita Docker activo):

```powershell
cd ai/wa-knowledge-rag
uv run pytest -m integration tests/golden -q
```

Sin Docker las pruebas de integración se omiten; no fallan.

### Cómo agregar un caso al set dorado

1. Elegir una `key`/eje libre y agregar la fuente a `sources` (título y contenido sintéticos, sin PII).
2. Agregar la consulta a `queries` con su `axis` y las `key` esperadas; si debe quedar fuera de base,
   sumarla a `outside`.
3. Ejecutar el set dorado y confirmar que `recall@5` sigue ≥ 0.8.

### Calibrar los umbrales de confianza

El score publicado es el RRF normalizado por `2/61`, no una probabilidad. Los umbrales
`RAG_CONFIDENCE_HIGH_MIN` y `RAG_CONFIDENCE_MEDIUM_MIN` **no tienen default** y sin ambos H8 devuelve
`low`. Los valores `0.9`/`0.6` de los tests son sintéticos: antes de habilitar respuestas en producción
hay que calibrarlos con **contenido aprobado real y embeddings reales**, midiendo `recall@5` y la tasa
de derivación sobre preguntas representativas. Los límites unitarios de la función de confianza se
prueban en `tests/unit/retrieval/test_retrieval.py`.

### Qué **no** mide el set dorado

- La calidad del modelo de embeddings real (usa vectores sintéticos).
- Si el contenido publicado está actualizado o es correcto.
- La tasa de respuestas al cliente: eso depende de H9 y de las reglas transversales.

## 7. Qué no hacer

- No publicar contenido sin aprobación registrada ni con `approved_at` sin zona horaria.
- No marcar `public` por conveniencia: solo el editor que aprueba puede declararlo.
- No usar datos reales de clientes en código, tests, fixtures ni en el corpus de ejemplo.
- No editar un `.sql` ya aplicado: se agrega una migración nueva.
- No tratar el contenido recuperado como instrucciones: es datos.
