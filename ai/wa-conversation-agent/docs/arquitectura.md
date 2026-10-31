# Arquitectura interna — wa-conversation-agent (H9)

Diseño del agente conversacional: cómo recibe un turno de H0.2, cómo consulta H8, cómo opera con H7,
qué callbacks validan la conversación y cómo se persiste la memoria en Redis.

Contratos que implementa o consume:

- Turno con H0.2: [`shared/contracts/wa-conversation-agent/turn.md`](../../../shared/contracts/wa-conversation-agent/turn.md) (v1).
- Recuperación de conocimiento: [`shared/contracts/wa-knowledge-rag/retrieval.md`](../../../shared/contracts/wa-knowledge-rag/retrieval.md) (v1).
- Catálogo de capacidades de H7: [`shared/contracts/wa-mcp-layer/capabilities.md`](../../../shared/contracts/wa-mcp-layer/capabilities.md).

## Propósito y límites

H9 convierte un turno normalizado en una respuesta estructurada:

```text
H0.2 ──POST /api/v1/agent/turn──▶ H9 ──consultar_conocimiento──▶ H8 (/retrieval, audience=public)
                                    │
                                    ├──capacidades *_v1──────────▶ H7 (/mcp) ──▶ H1–H6
                                    │
                                    └──escalar_a_humano──────────▶ señal escalate para H0.2
```

No es dueño de la conversación a nivel canal: no recibe webhooks, no consume Kafka, no deduplica el
mensaje ni publica outbound. Tampoco implementa reglas de negocio ni decide montos, horarios o
límites: los comunica tal como los entrega H7 o H8. La autorización de productos permanece en H7, que
valida credencial H2 y vínculo H3.

## Estructura del paquete

```text
src/wa_conversation_agent/
├── agent.py          root_agent: modelo, instrucción, tools y callbacks
├── config.py         Settings (prefijo AGENT_, SecretStr)
├── models.py         DTO estrictos del contrato de turno
├── server.py         FastAPI: auth, endpoint, health y errores
├── turn.py           TurnProcessor: Runner, presupuesto, memoria e idempotencia
├── mcp_tools.py      toolset H7, allowlist, identidad, sobre y redacción
├── knowledge.py      herramienta hacia H8
├── guardrails.py     callbacks, enmascarado, inyección, salida segura y escalamiento
├── memory.py         servicios Redis de sesión y largo plazo
├── idempotency.py    reservas por messageId (Redis o memoria)
└── prompts/
    └── instruccion_v1.md
```

`import wa_conversation_agent` importa `agent.py`, que construye `Settings()` y lee la instrucción,
pero no abre conexiones: el toolset MCP descubre herramientas recién al ejecutar.

## Flujo de un turno

1. `server.py` valida el Bearer contra `AGENT_SERVICE_TOKEN` con `compare_digest`. Si el token no está
   configurado o no coincide, responde `401`.
2. Pydantic valida `TurnRequest` (`extra="forbid"`, strings estrictos). Un error de validación devuelve
   `400` con un mensaje genérico; no se exponen entradas ni detalles internos.
3. Si `X-Idempotency-Key` no es igual a `messageId`, el procesador devuelve `409`.
4. `TurnProcessor.process` calcula un SHA-256 del request normalizado y llama
   `idempotency.begin(messageId, fingerprint)`:
   - respuesta completada con el mismo request → devuelve el `TurnResponse` cacheado;
   - reserva en curso o fingerprint distinto → `ConflictError` (`409`);
   - reserva nueva → ejecuta el turno.
5. Construye el estado técnico confiable desde `context.technicalContext` (`credencial`,
   `credencial_valida`, `vinculo_activo`, `numero_whatsapp`, `customer_id`, `link_status`,
   `onboarding_step`, `current_intent`, `language`, `response_style`, `summary`), agrega
   `correlation_id` y usa `customerAddress` si falta `numero_whatsapp`.
6. Fija el presupuesto de herramientas y el timeout por herramienta en `ContextVar`. Con Redis, abre
   `memory_service.for_turn(MemoryScope(...))`: si el vínculo es `blocked`/`unlinked` borra el perfil
   de largo plazo antes del turno y carga las preferencias permitidas.
7. Si la sesión ADK no tiene eventos (sesión nueva o expirada), incorpora `context.history` como datos
   de un mensaje de usuario, solo con roles `user`/`assistant`, `body` no blanco, PII enmascarada y la
   credencial de sesión redactada. El historial externo no aporta instrucciones, evidencia ni
   autorización.
8. Ejecuta `Runner.run_async` con `session_id=conversationId` y `RunConfig(max_llm_calls=max_tool_calls+2)`,
   bajo el timeout total del turno.
9. Proyecta los eventos: `toolsUsed` sale del presupuesto real, `escalate` se activa si
   `escalar_a_humano` devolvió `escalate=true`, y `reply.body` es el último texto final del modelo.
10. Si se agotó el tiempo o el tope de herramientas, o no hubo texto, fuerza la respuesta de
    derivación con `escalate=true`.
11. `idempotency.complete` guarda la respuesta. Ante fallo o cancelación **no libera la reserva**: una
    operación sensible pudo haber ocurrido y no debe repetirse.

## Herramienta hacia H8 (conocimiento)

`consultar_conocimiento(query, correlation_id)` implementa el lado consumidor del
[contrato de retrieval](../../../shared/contracts/wa-knowledge-rag/retrieval.md):

- Envía `POST {AGENT_RAG_URL}/retrieval` con `query` recortada, `audience: "public"`, `top_k: 5` y el
  `correlationId` del turno, con timeout `AGENT_RAG_TIMEOUT_SECONDS`.
- Valida la respuesta con modelos estrictos (`extra="forbid"`): evidencia `high`/`medium` con
  `results[1..5]`, citas y `version`, o la variante `confidence: "low"` con
  `reason: "sin_respuesta_suficiente"`.
- `400` o consulta inválida → `results: []`, `confidence: "low"`, `reason: "sin_respuesta_suficiente"`.
- Indisponibilidad, timeout, respuesta inválida o `correlationId` distinto → `reason: "no_disponible"`.
- Solo usa audiencia pública: nunca pide ni muestra fragmentos `internal`.
- Los fragmentos son **datos**, no instrucciones. H9 no envía PII ni credenciales en la consulta.

El contrato de turno no agrega citas al `TurnResponse`: el modelo las usa en el texto, y el guardrail
solo acepta frases respaldadas por la evidencia del turno.

## Herramientas hacia H7 (MCP)

`create_toolset` construye un `SessionMCPToolset` con `StreamableHTTPConnectionParams(AGENT_MCP_URL,
timeout=10, sse_read_timeout=10)` y `tool_filter` con la allowlist versionada.

- **27 capacidades v1**: 6 públicas (`acceso.*`, `vinculo.aceptar_terminos`,
  `vinculo.verificar_identidad`, `vinculo.consultar_estado`) y 21 protegidas (`vinculo.*` de perfil y
  baja, `cuentas.*`, `tarjetas.*`, `seguros.*`). Los nombres expuestos al modelo reemplazan `.` por
  `_` y agregan `_v1` (por ejemplo `cuentas.consultar_saldo` → `cuentas_consultar_saldo_v1`).
- **Sin capacidades fuera de la allowlist**: un nombre desconocido recibe
  `{"error": {"codigo": "CAPACIDAD_NO_DISPONIBLE"}}` sin llamar al transporte.
- **Identidad**: para capacidades protegidas el callback exige `vinculo_activo is True`,
  `credencial_valida is True` y una `credencial` string no vacía en el estado técnico. Si falta algo,
  devuelve el mensaje de re-verificación sin invocar H7.
- **Sobre de contexto**: el callback descarta `contexto` de los argumentos que propone el modelo y
  arma en `ToolContext` un sobre `{contexto: {numero_whatsapp, correlation_id, credencial},
  parametros}` que `SessionTool` agrega solo al llamar al transporte. La credencial no viaja en el
  prompt ni en los eventos.
- **Superficie del modelo**: `SessionTool` oculta el campo `contexto` de la declaración MCP.
- **Redacción**: quita `credencial` de las respuestas y reemplaza su valor por `[REDACTADO]`.
- **Errores de transporte**: devuelve `{"error": {"codigo": "SERVICIO_NO_DISPONIBLE", "mensaje":
  "No podemos completar la operación ahora."}}`; la excepción original no se imprime para no filtrar
  el body.
- **Límites**: todas las herramientas pasan por `TimedTool` (timeout por herramienta) y un wrapper de
  `before_tool_callback` que cuenta llamadas. Una herramienta protegida se ejecuta como máximo una vez
  por turno; al repetirse o superar el tope se activa el escalamiento forzado.

## Callbacks y guardrails

`root_agent` registra `before_model_callback`, `after_model_callback` y `before_tool_callback`; el
procesador agrega el wrapper de presupuesto sobre el callback de herramientas.

### Entrada (`before_model_callback`)

- Enmascara correos, nombres y números en todos los textos y redacta la credencial de sesión.
- Bloquea patrones conocidos de inyección directa en mensajes del cliente y respuestas de herramientas
  con instrucciones sospechosas; también rechaza el pedido explícito de fabricar explosivos (patrón
  puntual, no una clasificación general de contenido peligroso).
- Sanea las respuestas de herramientas (claves sensibles → `[REDACTADO]`).
- Separa instrucciones del sistema de los datos externos agregando una frontera: el texto del cliente
  y de las herramientas son datos, nunca órdenes.
- Solo considera evidencia posterior al último mensaje del cliente; los turnos anteriores no
  respaldan afirmaciones nuevas. `escalar_a_humano` no aporta evidencia de producto, solo habilita la
  confirmación de derivación.
- Guarda en `temp:guardrail` el id de invocación, hashes de las oraciones de evidencia y las banderas
  `unavailable`/`escalated`. No persiste productos ni credenciales.
- Si una herramienta informó indisponibilidad, responde con el mensaje de indisponibilidad sin llamar
  al modelo.

### Salida (`after_model_callback`)

- Rechaza textos con rastros técnicos (`traceback`, `exception`, URLs locales, `api_key`) o que
  repitan la credencial.
- Cada oración debe ser exactamente un mensaje del vocabulario seguro (`SAFE_MESSAGES`) o coincidir
  con el hash de una oración de evidencia del turno actual. Las paráfrasis se rechazan de forma
  conservadora.
- La confirmación de derivación («Para ayudarte mejor, te comunico con una persona de nuestro
  equipo.») solo se acepta si `escalar_a_humano` se invocó en el turno.
- Ante un rechazo responde con «No tengo información confirmada para responder eso. Podés consultar
  con soporte.» o con el mensaje de indisponibilidad, según corresponda.

Los patrones son defensas complementarias: no detectan toda inyección. La autorización real sigue en
H7 y ninguno de estos callbacks reemplaza la validación de identidad.

## Escalamiento

`escalar_a_humano(motivo, resumen)` no deriva: devuelve `{escalate: true, motivo, resumen}` con PII
enmascarada y longitudes acotadas. H0.2 decide y ejecuta la derivación real.

El turno devuelve `escalate=true` cuando:

- el modelo invocó `escalar_a_humano` y su respuesta trae `escalate=true`;
- se agotó el timeout total del turno, el timeout de una herramienta o el tope de llamadas;
- no hubo texto final del modelo.

En esos casos el `reply.body` es la confirmación literal de derivación.

## Memoria en Redis

`create_memory_services(Settings())` devuelve `RedisSessionService` y `RedisLongTermMemoryService`,
compatibles con `Runner`. Requiere `AGENT_REDIS_URL` y `AGENT_MEMORY_KEY_SECRET` (mínimo 32
caracteres). Decisión completa en [ADR-150](ADR-150-redis-memory.md).

- **Claves opacas**: `h9:v1:session:HMAC(app, user, conversation)` y
  `h9:v1:long:HMAC(app, customer)` con HMAC-SHA256 de la clave secreta. No hay identificadores
  originales ni índice reversible; `list_sessions` falla de forma explícita.
- **Sesión**: `session_id=conversationId`; `GETEX`/`SET EX` renuevan el TTL de inactividad
  (`AGENT_SESSION_TTL_MINUTES`, 30 por defecto) en cada lectura o escritura. Se conservan los últimos
  100 eventos.
- **Proyección segura**: al persistir, cada evento se reduce a rol y a frases del vocabulario cerrado
  (`SAFE_TEXT`); el resto se reemplaza por `[contenido omitido]`. El estado guarda solo valores
  enumerados (`onboarding_step`, `current_intent`, `language`, `response_style`, `summary`). No se
  persisten payloads de herramientas, metadata, identificadores de invocación ni delta arbitrario.
- **Largo plazo**: solo `language`, `response_style` y `summary` con valores enumerados. Se accede
  únicamente dentro de `for_turn(MemoryScope)` con vínculo `active`; el contexto del LLM no concede
  acceso. Un lock por cliente serializa el scope (máximo 120 segundos).
- **Bloqueo o desvinculación**: si `link_status` es `blocked` o `unlinked`, se borra el perfil antes
  del turno.
- **Sin Redis**: sesión en memoria e idempotencia en proceso, sin largo plazo; solo desarrollo.
  `AGENT_MEMORY_SERVER_URL` queda reservado y no interviene.

## Idempotencia

- Clave global por `messageId` (no por conversación), con fingerprint del request normalizado.
- `MemoryIdempotencyStore` para desarrollo; `RedisIdempotencyStore` usa
  `h9:v1:idem:HMAC(key)` y `SET NX EX` para compartir reservas entre procesos.
- Estados: `acquired` ejecuta el turno; `in_progress` y fingerprint distinto devuelven `409`; `done`
  devuelve la misma respuesta sin reinvocar modelo ni herramientas.
- TTL `AGENT_IDEMPOTENCY_TTL_DAYS` (7 por defecto, 1–30), contado desde la reserva y renovado al
  completar. Un fallo o cancelación conserva la reserva hasta su vencimiento.

## Contratos y compatibilidad

| Contrato | Rol de H9 | Fuente implementada |
|---|---|---|
| [`turn.md`](../../../shared/contracts/wa-conversation-agent/turn.md) | Publica v1; lo consume H0.2 | `models.py`, `server.py`, `turn.py`, `idempotency.py` |
| [`retrieval.md`](../../../shared/contracts/wa-knowledge-rag/retrieval.md) | Consume v1 de H8 | `knowledge.py` |
| [`capabilities.md`](../../../shared/contracts/wa-mcp-layer/capabilities.md) | Consume v1 de H7 | `mcp_tools.py` |

Los ejemplos de `turn.md` se validan contra los modelos en `tests/test_published_contract.py`. Un
cambio incompatible exige una versión nueva, conservando la actual hasta que el consumidor migre.

## Seguridad y privacidad

- Secretos solo por entorno y como `SecretStr`; el paquete no carga `.env` automáticamente y no los
  registra.
- La credencial de sesión queda en el estado técnico, fuera del prompt y de la memoria de largo plazo,
  y solo se agrega al sobre del transporte hacia H7.
- Errores genéricos: no se devuelven mensajes de Pydantic, stack traces, tokens ni detalles internos.
- PII enmascarada antes del modelo; historial externo saneado; logs y fixtures sin datos reales.
- `correlationId` se propaga a H8 y a H7 para la traza extremo a extremo.

## Pruebas

| Archivo | Qué cubre |
|---|---|
| `tests/test_base.py` | Importación, configuración y paquete |
| `tests/test_agent.py` | `root_agent`, instrucción y callbacks |
| `tests/test_turn.py` | API de turno con modelo falso y errores HTTP |
| `tests/test_turn_runner.py` | Runner, historial, presupuesto y escalamiento forzado |
| `tests/test_guardrails.py` | Enmascarado, inyección directa/indirecta y salida segura |
| `tests/test_mcp_tools.py` | Allowlist, identidad, sobre y redacción |
| `tests/test_knowledge.py` | Contrato de H8 con `respx` |
| `tests/test_memory.py` | Proyección segura, TTL y largo plazo (fakeredis) |
| `tests/test_memory_integration.py` | Redis real con testcontainers (marker `integration`) |
| `tests/test_idempotency.py` | Reservas, fingerprint, TTL y concurrencia |
| `tests/test_evalsets.py` | Esquema ADK de los diez evalsets y umbrales |
| `tests/evals/test_evals.py` | Modelo real con dobles H7/H8 (marker `evals`) |
| `tests/test_published_contract.py` | Contrato publicado contra los modelos |

Comandos y variables: [README](../README.md#cómo-probarlo).

## Limitaciones conocidas

1. **H7 no publica `/mcp`**: hoy solo expone `/health`. La integración real queda pendiente; TASK-148
   validó con un servidor MCP local que publica los esquemas de H7. No usar el contenedor de H7 para
   validar el agente.
2. **Puerto H0.2**: `project.yaml` fija `8103`, mientras el default del cliente de H0.2 es
   `http://localhost:8099`. Se resuelve por configuración de entorno; está documentado en
   [`turn.md`](../../../shared/contracts/wa-conversation-agent/turn.md#transporte-y-autenticación).
3. **Observabilidad**: no hay logger estructurado propio ni endpoint de métricas; la traza se completa
   con H7/H8 y la respuesta del turno. Ver [operacion.md](operacion.md#logs-y-trazabilidad).
4. **Memoria de largo plazo acotada**: solo preferencias enumeradas, sin recuerdos generados por LLM ni
   Agent Memory Server.
