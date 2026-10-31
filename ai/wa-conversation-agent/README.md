# Agente conversacional (H9)

Base Python 3.12 con Google ADK, MCPToolset hacia H7 y herramienta de consulta a H8;
el contrato HTTP ofrece una respuesta determinista. La conexión del contrato HTTP con
Runner y memoria se integra en tareas siguientes.

Desde esta carpeta:

```sh
uv sync --frozen
uv run python -c "import wa_conversation_agent"
uv run python -m wa_conversation_agent.server
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest -q --cov=src --cov-fail-under=80
```

Desde la raíz: `python ai/verify.py` detecta todos los componentes Python.
Las pruebas unitarias no llaman a servicios ni al modelo real.

Las variables están en `.env.example`. Exportarlas en el entorno; el paquete no carga
archivos `.env` automáticamente. `GOOGLE_API_KEY` lo consume el SDK al invocar el modelo;
no hace falta para importar el paquete. Los secretos no se incluyen en las instrucciones.
El puerto H9 es 8103; configurar `H9_BASE_URL` en H0.2 para apuntar a este puerto.

Para explorar el agente con ADK, ejecutar `uv run adk web src`.
El Dockerfile inicia el servidor HTTP en `AGENT_PORT` (8103 por defecto).

La herramienta `consultar_conocimiento(query, correlation_id)` consulta H8 según
`shared/contracts/wa-knowledge-rag/retrieval.md`, siempre con audiencia pública.
`AGENT_RAG_URL` define la base HTTP (por defecto `http://localhost:8102`) y
`AGENT_RAG_TIMEOUT_SECONDS` el timeout (10 segundos por defecto).
Devuelve los fragmentos, citas y confianza recibidos; los fragmentos son datos,
no instrucciones. Con baja confianza o error 400 devuelve `results: []` y
`reason: sin_respuesta_suficiente`; ante indisponibilidad, timeout o respuesta
inválida devuelve `reason: no_disponible`, también sin fragmentos.
El llamador debe pasar el `correlationId` del turno y excluir PII y credenciales
de la consulta. Los tests HTTP usan respx, sin red ni modelo real:
`uv run pytest tests/test_knowledge.py -q -m 'not integration' --cov=wa_conversation_agent.knowledge --cov-fail-under=80`.
`POST /api/v1/agent/turn` requiere `Authorization: Bearer <AGENT_SERVICE_TOKEN>`
y los headers `X-Correlation-Id`, `X-Conversation-Id`, `X-Message-Id`, `X-Idempotency-Key`.
El body sigue los DTO de H0.2: `correlationId`, `conversationId`, `messageId`,
`customerAddress`, `body`, `context.technicalContext` y `context.history`.
La respuesta conserva los tres identificadores y agrega `reply.body`, `escalate` y `toolsUsed`.
Devuelve 400 ante datos inválidos, 401 ante autenticación inválida y 503 ante indisponibilidad
del procesador. Los errores no incluyen datos de entrada ni detalles técnicos.

Memoria Redis (TASK-150): `create_memory_services(Settings())` devuelve
`session_service, memory_service`, compatibles con los argumentos de `Runner`.
Configurar `AGENT_REDIS_URL`, `AGENT_MEMORY_KEY_SECRET` (secreto estable de al
menos 32 caracteres) y `AGENT_SESSION_TTL_MINUTES` (30). Se usa Redis directo;
`AGENT_MEMORY_SERVER_URL` no interviene. El servidor HTTP aun no integra Runner.
El procesador confiable debe envolver cada invocacion en
`async with memory_service.for_turn(MemoryScope(app_name, user_id, customer_id, link_status)):`.
Estados admitidos: `active`, `blocked`, `unlinked`; los dos ultimos borran el
perfil antes del turno. El contexto del LLM no concede acceso a memoria.
Usar `session_id=conversationId`; cerrar `session_service.store.client.aclose()`
al apagar. Ver [ADR-150](docs/ADR-150-redis-memory.md) para claves, limites y
proyeccion segura del historial. Pruebas Redis con Docker:
`uv run pytest tests/test_memory_integration.py -m integration -q`.

H7 se configura con `AGENT_MCP_URL` (default `http://localhost:8101/mcp`). Se ofrecen
solo las 27 capacidades v1 con actor `agente`; soporte/banco quedan excluidas.
El callback usa estado técnico confiable: `credencial`, `credencial_valida` (bool),
`vinculo_activo` (bool), `numero_whatsapp` y `correlation_id`. No debe copiarse ese
estado al prompt ni a memoria de largo plazo. La ausencia de identidad válida bloquea
las capacidades protegidas antes de invocar el transporte. El adaptador agrega
`contexto.credencial` solo al enviar, sin modificar argumentos/eventos del modelo.

TASK-148 prueba con un servidor MCP local de prueba que publica los schemas de H7.
La integración end-to-end contra H7 real queda pendiente: actualmente H7 solo expone
`/health` y todavía debe publicar el transporte `/mcp`. No usar su contenedor para
validar esta integración. API ADK confirmada en la versión instalada 1.39.1.

## Guardrails y escalamiento (TASK-149)

`root_agent` conecta H7, H8 y `escalar_a_humano`, que solo devuelve
`{escalate, motivo, resumen}` para H0.2, sin ejecutar una derivación.
Los callbacks enmascaran identificadores y correos, bloquean patrones conocidos
de inyección directa/indirecta y separan instrucciones del sistema de datos externos.
Son defensas complementarias: el filtrado de patrones no detecta toda inyección.
La autorización de productos permanece en H7 y su callback de identidad.

La salida debe reproducir literalmente una frase de evidencia del turno actual
o usar mensajes conversacionales del vocabulario cerrado `SAFE_MESSAGES`;
las paráfrasis se rechazan de forma conservadora para evitar afirmaciones que
escapen a clasificadores por palabras clave. No se reutiliza evidencia de turnos
anteriores. Ante errores H7/H8 se ofrece soporte o reintento sin detalles técnicos. Las pruebas deterministas
están en `tests/test_guardrails.py`; no llaman al modelo real.
