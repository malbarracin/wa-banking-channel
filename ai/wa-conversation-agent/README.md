# Agente conversacional (H9)

Cara del banco en WhatsApp: recibe turnos ya normalizados del orquestador (H0.2), conduce la
conversación, informa con contenido aprobado de H8 (RAG, con citas y confianza) y opera con las
capacidades publicadas de H7 (MCP, validando identidad y vínculo). Devuelve una respuesta
estructurada; no habla con Twilio ni consume Kafka.

Python 3.12 con Google ADK 1.39.1, administrado con [uv](https://docs.astral.sh/uv/).
Especificación: [`specs/agente-conversacional/spec.md`](../../specs/agente-conversacional/spec.md).
Contrato con H0.2: [`shared/contracts/wa-conversation-agent/turn.md`](../../shared/contracts/wa-conversation-agent/turn.md).

## Estado del componente

- Implementado: configuración por entorno, `root_agent` con instrucción versionada, herramienta hacia
  H8 (`consultar_conocimiento`), toolset MCP hacia H7 con las 27 capacidades v1, callbacks/guardrails,
  escalamiento (`escalar_a_humano`), memoria Redis (sesión y largo plazo), procesador de turno con
  límites de herramientas y timeouts, idempotencia por `messageId`, API `POST /api/v1/agent/turn` y
  `GET /health`, evalsets ADK y contrato publicado.
- Pendiente: integración end-to-end contra H7 real, que todavía no publica el transporte `/mcp`
  (hoy solo `/health`). Ver [Limitaciones conocidas](#limitaciones-conocidas).

## Qué hace y qué no hace

| Hace | No hace |
|---|---|
| Recibe el turno normalizado de H0.2, autentica el token de servicio, valida el DTO y aplica idempotencia por `messageId` | No recibe webhooks de Twilio, no produce/consume Kafka ni gestiona la máquina de estados del mensaje (H0, H0.1, H0.2) |
| Clasifica y conduce consultas informativas, operaciones y soporte | No implementa lógica de negocio ni datos de H1–H6: opera solo por las capacidades publicadas de H7 |
| Informa con contenido aprobado de H8, con citas y confianza; no inventa ante `low` | No define el catálogo MCP (H7) ni el contenido de la base de conocimiento (H8) |
| Valida identidad y vínculo antes de operar productos sensibles (vía estado técnico y callback) | No valida credenciales H2 ni vínculos H3 por su cuenta: H7 es la autoridad |
| Propone derivación a una persona (`escalate`) con contexto resumido | No ejecuta la derivación real ni contacta operadores: H0.2 la gestiona |
| Mantiene continuidad con memoria Redis de sesión y preferencias de largo plazo | No persiste texto libre, credenciales, saldos ni PII en memoria |
| Devuelve `reply.body`, `escalate` y `toolsUsed` según el contrato de turno | No publica mensajes outbound ni define políticas de montos, horarios o límites (los comunica tal cual) |

## Módulos

| Módulo | Responsabilidad |
|---|---|
| `agent.py` | Define `root_agent` (modelo, instrucción versionada, herramientas y callbacks) sin abrir conexiones al importar |
| `config.py` | `Settings` con pydantic-settings (prefijo `AGENT_`) y secretos como `SecretStr` |
| `models.py` | DTO estrictos del contrato de turno (`TurnRequest`, `TurnResponse`, `Reply`) |
| `server.py` | API FastAPI: autenticación Bearer, `POST /api/v1/agent/turn`, `GET /health` y traducción de errores |
| `turn.py` | `TurnProcessor`: Runner ADK, estado técnico confiable, presupuesto/timeout de herramientas, proyección de eventos y escalamiento forzado |
| `mcp_tools.py` | Toolset MCP hacia H7, allowlist v1, validación de identidad, sobre de contexto y redacción de credenciales |
| `knowledge.py` | Herramienta `consultar_conocimiento` contra el contrato v1 de H8 |
| `guardrails.py` | Callbacks de entrada/salida, enmascarado de PII, defensas de inyección, vocabulario seguro y `escalar_a_humano` |
| `memory.py` | Servicios de sesión y largo plazo sobre Redis con proyección segura y HMAC |
| `idempotency.py` | Reservas por `messageId`: Redis para despliegues y memoria para desarrollo |
| `prompts/instruccion_v1.md` | Instrucción del banco versionada que recibe el modelo |

Detalle de flujos, callbacks y memoria: [docs/arquitectura.md](docs/arquitectura.md).
Guía de operación: [docs/operacion.md](docs/operacion.md).
Guía del agente (intenciones, mensajes y evalsets): [docs/guia-agente.md](docs/guia-agente.md).
Despliegue: [docs/deployment.md](docs/deployment.md).
Decisión de memoria Redis: [docs/ADR-150-redis-memory.md](docs/ADR-150-redis-memory.md).

## Cómo levantarlo

### Con uv (local)

Requiere Python 3.12, `uv` en el `PATH` y `GOOGLE_API_KEY` para invocar el modelo. H7 y H8 son
opcionales para arrancar; sin ellos el agente responde con mensajes de indisponibilidad.

```powershell
cd ai/wa-conversation-agent
uv sync --frozen
uv run python -c "import wa_conversation_agent"
uv run python -m wa_conversation_agent.server
```

El paquete **no carga archivos `.env` automáticamente**: las variables se leen del entorno. En
PowerShell, exportarlas antes de arrancar (valores de ejemplo, sin secretos reales):

```powershell
$env:GOOGLE_API_KEY = "..."                        # API key del proveedor del modelo
$env:AGENT_SERVICE_TOKEN = "..."                   # token que enviará H0.2
$env:AGENT_REDIS_URL = "redis://localhost:6379"    # opcional en local; sin Redis usa memoria en proceso
$env:AGENT_MEMORY_KEY_SECRET = "..."               # obligatorio con Redis; mínimo 32 caracteres
uv run python -m wa_conversation_agent.server
```

La API queda en `http://localhost:8103`:

```powershell
curl.exe http://localhost:8103/health
```

`GET /health` responde `{"status":"ok"}` sin autenticación y sin comprobar Gemini, H7, H8 ni Redis.
El turno sí requiere `Authorization: Bearer <AGENT_SERVICE_TOKEN>`.

Si `uv` falla al adquirir su cache global (lock denegado en Windows), usar un cache dentro del
workspace: `$env:UV_CACHE_DIR="ai/wa-conversation-agent/.uv-cache"`.

### Desarrollo del agente con ADK

```powershell
uv run adk web src
```

Abre la interfaz web de ADK para conversar con `root_agent`. Usa el modelo real y las conexiones
configuradas (H7/H8); no apuntar a servicios con datos reales.

### Con Docker Compose

Desde la raíz, copiar `ai/.env.example` a `ai/.env` y completar `GOOGLE_API_KEY`,
`AGENT_SERVICE_TOKEN` y `AGENT_MEMORY_KEY_SECRET` (mínimo 32 caracteres). No versionar `.env`.

Stack de IA completo (incluye Redis y Agent Memory Server):

```powershell
docker compose -f ai/docker-compose.yml --env-file ai/.env up -d --build wa-conversation-agent
docker compose -f ai/docker-compose.yml --env-file ai/.env ps
curl.exe http://localhost:8103/health
```

Compose propio de H9 (reutiliza el stack compartido por `extends` y la red `ai_default`):

```powershell
docker compose -f ai/wa-conversation-agent/docker-compose.yml --env-file ai/.env up -d --build
curl.exe http://localhost:8103/health
```

No iniciar las dos variantes al mismo tiempo. Validar la configuración sin levantar contenedores:
`docker compose -f ai/docker-compose.yml --env-file ai/.env config --quiet`. Detalle y URLs internas:
[docs/deployment.md](docs/deployment.md).

## Variables de entorno

Todas se leen del entorno (no hay carga automática de `.env`). En el repo solo va `.env.example` con
nombres y valores de ejemplo. Los secretos nunca se registran ni se incluyen en respuestas.

| Variable | Para qué | Ejemplo |
|---|---|---|
| `AGENT_PORT` | Puerto HTTP de la API | `8103` |
| `AGENT_MODEL` | Modelo de Google ADK | `gemini-2.5-flash` |
| `GOOGLE_API_KEY` | API key del SDK de Google (secreto; **no** lleva prefijo `AGENT_`) | *(vacío)* |
| `AGENT_MCP_URL` | Endpoint Streamable HTTP de H7 | `http://localhost:8101/mcp` |
| `AGENT_RAG_URL` | URL base de H8 | `http://localhost:8102` |
| `AGENT_RAG_TIMEOUT_SECONDS` | Timeout de la consulta a H8 | `10` |
| `AGENT_REDIS_URL` | Redis para sesión, largo plazo e idempotencia (secreto) | `redis://localhost:6379` |
| `AGENT_MEMORY_KEY_SECRET` | Clave HMAC de las claves de memoria e idempotencia (secreto, **mínimo 32 caracteres**) | *(vacío)* |
| `AGENT_SESSION_TTL_MINUTES` | TTL de inactividad de la sesión en Redis (1–1440) | `30` |
| `AGENT_MEMORY_SERVER_URL` | Reservado para Agent Memory Server; **no lo usa la implementación actual** | `http://agent-memory-server:8000` |
| `AGENT_SERVICE_TOKEN` | Token Bearer que debe enviar H0.2 (secreto) | *(vacío)* |
| `AGENT_MAX_TOOL_CALLS_PER_TURN` | Tope de llamadas a herramientas por turno (1–50) | `8` |
| `AGENT_TOOL_TIMEOUT_SECONDS` | Timeout de cada herramienta, incluidas las remotas | `10` |
| `AGENT_TURN_TIMEOUT_SECONDS` | Timeout total del turno | `4` |
| `AGENT_IDEMPOTENCY_TTL_DAYS` | TTL de la reserva por `messageId` (1–30) | `7` |

Variables que usa **solo** el compose (están en `ai/.env.example`): `AGENT_HOST_PORT` (mapeo del
puerto host, por defecto `8103`), `AGENT_REDIS_HOST_PORT` (`6379`) y `AGENT_MEMORY_HOST_PORT`
(`8000`). `AGENT_PORT`, `AGENT_MCP_URL`, `AGENT_RAG_URL` y `AGENT_REDIS_URL` se fijan en el compose
para la red interna.

Sin `AGENT_REDIS_URL`, H9 usa sesión en memoria e idempotencia en proceso y no tiene memoria de
largo plazo: sirve solo para desarrollo local y no sobrevive reinicios.

## Cómo probarlo

```powershell
cd ai/wa-conversation-agent
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest -q --cov=src --cov-fail-under=80
```

- **Unitarios** (por defecto): no necesitan Docker, red ni modelo real. `pytest` excluye el marker
  `evals`; usan modelos ADK falsos, `respx` y `fakeredis`.
- **Integración con Redis real**: `uv run pytest -m integration -q`, con Docker activo (testcontainers).
  Sin Docker esas pruebas se omiten.
- **Evalsets con modelo real**: `uv run pytest -m evals tests/evals -q`, con `GOOGLE_API_KEY`. Sin
  clave los casos se omiten. El arnés conserva modelo y callbacks reales, sustituye el transporte de
  H7 por herramientas con los esquemas publicados y H8 por respuestas HTTP de
  `tests/evals/service_fixtures.json`; no usa Redis ni ejecuta operaciones reales.
- **Validación de artefactos de eval sin modelo**: `uv run pytest tests/test_evalsets.py -q`.
- **Contrato publicado**: `uv run pytest tests/test_published_contract.py -q` contrasta los ejemplos
  de `turn.md` con los modelos y verifica referencias y campos.
- **Gate del equipo** desde la raíz: `python ai/verify.py` (sync, ruff, formato, mypy y pytest con
  cobertura mínima 80% en todos los componentes Python de IA).

### Cómo correr `adk eval`

La CLI usa el agente de producción y necesita un H7 con Streamable HTTP y un H8 de prueba sirviendo
los datos de `tests/evals/service_fixtures.json`; no aplica los dobles del arnés pytest. Nunca
apuntar a servicios con datos reales.

```powershell
uv run adk eval src/wa_conversation_agent tests/evals/onboarding.evalset.json `
  --config_file_path tests/evals/test_config.json --print_detailed_results
```

Repetir cambiando el archivo por cada uno de los diez `*.evalset.json`. Umbrales en
`tests/evals/test_config.json`: `tool_trajectory_avg_score = 1.0` y `response_match_score = 0.8`.
Detalle del arnés y de las fixtures: [tests/evals/README.md](tests/evals/README.md) y
[docs/guia-agente.md](docs/guia-agente.md).

## Contrato publicado

Fuente de verdad para H0.2: [`shared/contracts/wa-conversation-agent/turn.md`](../../shared/contracts/wa-conversation-agent/turn.md) (v1).

- **Transporte**: `POST /api/v1/agent/turn`, JSON, con `Authorization: Bearer <token de servicio>` y
  los headers `X-Correlation-Id`, `X-Conversation-Id`, `X-Message-Id` y `X-Idempotency-Key` (igual a
  `messageId`).
- **Request**: `correlationId`, `conversationId`, `messageId`, `customerAddress`, `body`,
  `context.technicalContext` y `context.history`. Sin campos adicionales ni `null`.
- **Response 200**: los tres identificadores, `reply.body`, `escalate` y `toolsUsed`.
- **Errores**: `400` datos inválidos, `401` autenticación inválida, `409` idempotencia en curso o
  request distinto con la misma clave, `503` procesador no disponible. Sin detalles técnicos.
- **Idempotencia**: reserva global por `messageId` con fingerprint del request y TTL de siete días;
  un replay exacto devuelve la respuesta cacheada sin reinvocar herramientas.
- **Conocimiento**: las respuestas informativas usan
  [`shared/contracts/wa-knowledge-rag/retrieval.md`](../../shared/contracts/wa-knowledge-rag/retrieval.md):
  audiencia `public`, citas obligatorias y confianza. `TurnResponse` no agrega campos de citas.

Un cambio incompatible requiere una versión nueva del contrato, conservando v1 hasta que H0.2 migre.

## Operación

- **Health**: `GET /health` → `{"status":"ok"}`, sin autenticación. Es el `HEALTHCHECK` del
  `Dockerfile` y del compose; no certifica disponibilidad de Gemini, H7, H8 ni Redis.
- **Trazabilidad**: el `correlationId` viaja del header y el body a la respuesta, a la consulta de H8
  y al sobre de contexto de H7. **H9 no implementa un logger propio**: la correlación se completa con
  los logs estructurados de H8 y la auditoría de H7. Ver
  [docs/operacion.md](docs/operacion.md#logs-y-trazabilidad).
- **Idempotencia**: reintentos de H0.2 reutilizan el mismo `messageId` y `X-Idempotency-Key`; un
  replay exacto devuelve la respuesta cacheada, y un request distinto o una reserva en curso devuelve
  `409` (H0.2 lo trata como `REJECTED` y no reintenta).
- **Degradación**: si H8 no responde o devuelve una respuesta inválida, el agente responde con el
  mensaje de indisponibilidad; si se agotan el tiempo o el presupuesto de herramientas, responde con
  el mensaje de derivación y `escalate=true`. Nunca expone stack traces ni detalles internos.

## Limitaciones conocidas

1. **Integración real con H7 pendiente**: H7 todavía no publica su transporte `/mcp` (hoy solo expone
   `/health`). TASK-148 validó la integración contra un servidor MCP local que publica los esquemas de
   H7. No usar el contenedor de H7 para validar el agente hasta que publique `/mcp`.
2. **Discrepancia de puerto con H0.2**: `project.yaml` fija H9 en `8103` (`AGENT_PORT=8103`), pero el
   `.env.example`/`application.yml` actual de `platform/wa-channel-orchestrator` usa el default
   provisional `H9_BASE_URL=http://localhost:8099`. Se resuelve por configuración de entorno del lado
   de H0.2 (`H9_BASE_URL=http://localhost:8103` o la dirección de H9 en la red), sin cambios de
   código. Está documentado también en
   [`turn.md`](../../shared/contracts/wa-conversation-agent/turn.md#transporte-y-autenticación).
3. **Sin logs estructurados ni métricas propias**: H9 no emite eventos de log con `correlationId` ni
   expone un endpoint de métricas; la observabilidad depende de los logs de H7/H8, de los access logs
   del servidor y de la respuesta del turno.
4. **Agent Memory Server reservado**: `AGENT_MEMORY_SERVER_URL` se configura pero no interviene; la
   memoria implementada usa Redis directo (ver [ADR-150](docs/ADR-150-redis-memory.md)).
