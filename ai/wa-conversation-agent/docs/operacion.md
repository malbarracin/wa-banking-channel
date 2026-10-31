# Operación — wa-conversation-agent (H9)

Qué mirar y qué hacer cuando el agente no responde, H7 o H8 fallan, o un reintento choca con la
idempotencia. Para el diseño interno ver [arquitectura.md](arquitectura.md); para el contrato con H0.2,
[`shared/contracts/wa-conversation-agent/turn.md`](../../../shared/contracts/wa-conversation-agent/turn.md).

## Arranque y health

```powershell
docker compose -f ai/docker-compose.yml --env-file ai/.env up -d --build wa-conversation-agent
curl.exe http://localhost:8103/health
```

`GET /health` responde `{"status":"ok"}` sin autenticación. Comprueba solo que el proceso HTTP
atiende: **no** consulta Gemini, H7, H8 ni Redis. El `Dockerfile` y el compose lo usan como
`HEALTHCHECK` (cada 30 s). Un `/health` en verde con turnos en `503` indica una dependencia caída, no
un problema del proceso.

El turno requiere `Authorization: Bearer <AGENT_SERVICE_TOKEN>`; si el token no está configurado o no
coincide, todas las llamadas al turno reciben `401` (el health sigue abierto).

## Diagnóstico rápido

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `GET /health` no responde | Proceso detenido, puerto ocupado o contenedor sin arrancar | Revisar `docker compose ps` y los logs del servicio; confirmar `AGENT_PORT`/mapeo `8103` |
| `401 No autorizado` en el turno | Bearer ausente, distinto o `AGENT_SERVICE_TOKEN` vacío | Alinear el token de H0.2 con el de H9 |
| `400` en el turno | JSON, body o headers inválidos o ausentes | Revisar el DTO y los cuatro headers `X-*`; el detalle es genérico a propósito |
| `409 El mensaje ya se está procesando.` | Reserva en curso, `X-Idempotency-Key` distinto de `messageId` o mismo `messageId` con otro request | No cambiar la clave; ver [Idempotencia y reintentos](#idempotencia-y-reintentos) |
| `503 No podemos atender tu mensaje en este momento.` | Excepción no controlada del procesador (Redis, MCP, modelo, etc.) | Revisar logs; un reintento con el mismo `messageId` puede devolver `409` |
| `200` con mensaje de indisponibilidad | H8 o una herramienta de H7 fallaron durante el turno | Ver [H8 no responde](#h8-no-responde-o-sin-evidencia) y [H7 no responde](#h7-no-responde-o-no-publica-mcp) |
| `200` con mensaje de derivación y `escalate=true` | Se agotó un timeout, el tope de herramientas o el modelo no produjo texto | H0.2 debe gestionar la derivación |
| Respuestas siempre genéricas | Guardrails rechazando paráfrasis sin evidencia | Revisar que H8 devuelva evidencia suficiente y que la respuesta reproduzca sus frases |

## H7 no responde o no publica `/mcp`

H7 es la única vía para operar productos. H9 usa `AGENT_MCP_URL` (por defecto
`http://localhost:8101/mcp`) con Streamable HTTP.

- **Limitación actual**: H7 todavía expone solo `/health`; el transporte `/mcp` está pendiente. Sin
  ese transporte H9 no puede descubrir ni invocar capacidades, y la integración end-to-end no puede
  validarse. TASK-148 se validó con un servidor MCP local que publica los esquemas de H7. **No usar el
  contenedor de H7 para validar el agente** hasta que publique `/mcp`.
- **Fallo al descubrir herramientas o al invocar** (transporte caído, timeout, 5xx): si el fallo
  ocurre al llamar una capacidad, la herramienta devuelve `SERVICIO_NO_DISPONIBLE` y el agente
  responde con «Lo siento, no podemos completar tu consulta ahora. Podés reintentar o pedir ayuda a
  soporte.» Si el fallo impide ejecutar el turno, el servidor responde `503` genérico.
- **Identidad insuficiente**: si `vinculo_activo` o `credencial_valida` no son `true`, o falta la
  credencial, H9 **no llama** a H7 y responde «Por seguridad, volvé a verificar tu identidad para
  continuar». Esto no es una falla de H7: el estado técnico que envía H0.2 está incompleto o vencido.
- **Capacidad fuera de la allowlist**: `{"error":{"codigo":"CAPACIDAD_NO_DISPONIBLE"}}`. H9 solo usa
  las 27 capacidades v1 aprobadas; `identidad.*`, `acceso.revocar_credenciales_masivo`,
  `acceso.consultar_historial_credencial` y `vinculo.consultar_historial` quedan fuera.

Para verificar que H7 expone `/mcp`, consultar su propio `GET /health` y el catálogo publicado en
[`shared/contracts/wa-mcp-layer/capabilities.md`](../../../shared/contracts/wa-mcp-layer/capabilities.md).

## H8 no responde o sin evidencia

H9 llama a H8 con `audience: "public"`, `top_k: 5`, el `correlationId` del turno y
`AGENT_RAG_TIMEOUT_SECONDS` (10 s por defecto).

| Respuesta de H8 | Resultado en H9 | Mensaje al cliente |
|---|---|---|
| `200` con `confidence` `high`/`medium` y `results` | El modelo responde con evidencia del turno | Texto respaldado por una frase de la evidencia o mensaje seguro |
| `200` con `confidence: "low"` y `reason: "sin_respuesta_suficiente"` | No hay evidencia; no se inventa | Pide aclaración u ofrece derivación |
| `400 CONSULTA_INVALIDA` | Se trata como sin evidencia | Pide aclaración u ofrece derivación |
| `503`, timeout, respuesta inválida o `correlationId` distinto | Se trata como indisponibilidad | «Lo siento, no podemos completar tu consulta ahora. Podés reintentar o pedir ayuda a soporte.» |

`GET /health` de H8 no consulta la base ni el proveedor de embeddings: un `/health` en verde no
garantiza que `/retrieval` responda. Si H8 falla de forma persistente, revisar `GOOGLE_API_KEY`,
`DATABASE_URL` y los umbrales de confianza del lado de H8.

La consulta nunca debe incluir PII ni credenciales. H9 siempre pide audiencia pública; los fragmentos
internos no se usan para texto al cliente.

## Timeouts, límites y escalamiento

| Límite | Variable | Default | Efecto al agotarse |
|---|---|---|---|
| Timeout por herramienta (H7, H8 y locales) | `AGENT_TOOL_TIMEOUT_SECONDS` | 10 s | Escalamiento forzado |
| Timeout total del turno | `AGENT_TURN_TIMEOUT_SECONDS` | 4 s | Escalamiento forzado |
| Llamadas a herramientas por turno | `AGENT_MAX_TOOL_CALLS_PER_TURN` | 8 | Escalamiento forzado |
| Ejecución de una capacidad protegida | fija | 1 por turno | Escalamiento forzado |

Cuando se agota un límite, el turno devuelve `200` con `reply.body` igual a «Para ayudarte mejor, te
comunico con una persona de nuestro equipo.» y `escalate=true`; `toolsUsed` conserva las herramientas
invocadas hasta ese punto. H0.2 publica la respuesta y gestiona la derivación: H9 no deriva por sí
mismo. Para diagnosticar, revisar latencia de H7/H8 y si el modelo repite llamadas.

## Logs y trazabilidad

H9 **no implementa un logger propio**: no emite eventos estructurados ni un endpoint de métricas. La
trazabilidad se arma con estas piezas:

1. **Respuesta del turno**: `correlationId`, `conversationId`, `messageId` y `toolsUsed`. Es la fuente
   principal para vincular una llamada con su resultado.
2. **Hacia H8**: el `correlationId` viaja en el body de `POST /retrieval`; H8 lo registra en sus logs
   estructurados (`knowledge_retrieved`, `retrieval_completed`/`retrieval_failed`) sin consulta ni
   contenido.
3. **Hacia H7**: el sobre de contexto incluye `correlation_id` (derivado del `correlationId` del
   turno) en cada llamada a una capacidad; H7 emite el evento de auditoría `invocacion_capacidad` con
   `correlation_id`, capacidad y resultado.
4. **Idempotencia**: la reserva se guarda en Redis bajo una clave HMAC de `messageId`
   (`h9:v1:idem:<hmac>`). No es reversible ni enumerable: para encontrar un turno usar el
   `correlationId`/`messageId` que conserva H0.2, no las claves de Redis.
5. **Access logs**: uvicorn registra método, ruta y estado, pero no el `correlationId` por defecto.

Para investigar un caso: tomar el `correlationId` del registro de H0.2, buscar ese turno en la
respuesta de H9 y en los logs de H8, y correlacionarlo con la auditoría de H7. Nunca registrar ni
pegar en un ticket credenciales, teléfonos, documentos, cuerpos de mensaje ni variables de entorno. Los
argumentos de las herramientas no se imprimen: el adaptador MCP evita la instrumentación que podría
volcar el sobre con la credencial.

Si se necesita más detalle, subir el nivel de log de los servicios de H7/H8 (no de H9, que no emite
logs propios) y reproducir con datos sintéticos.

## Idempotencia y reintentos

La clave es `messageId` (`X-Idempotency-Key` debe ser idéntico) y es **global**, no por conversación.
H9 guarda un fingerprint del request completo.

- **Replay exacto** (mismos `messageId`, headers y body): `200` con la misma respuesta cacheada, sin
  volver a ejecutar el modelo ni las herramientas, incluso si el turno original terminó con
  escalamiento.
- **Reserva en curso** (el turno original sigue procesándose): `409`.
- **Mismo `messageId` con un request distinto** (incluido `correlationId`, `conversationId` o
  contexto): `409`; no se sobrescribe la reserva.
- **Fallo o cancelación**: la reserva **permanece** hasta el TTL. El reintento con la misma clave
  devuelve `409` si no hay respuesta completada, porque una operación sensible pudo haber ocurrido.
- **TTL**: `AGENT_IDEMPOTENCY_TTL_DAYS` (7 por defecto, 1–30). Se cuenta desde la reserva y se renueva
  al guardar la respuesta; un replay no lo renueva.

Reglas para H0.2 y para quien opera:

- Reintentar timeouts y `5xx` con **la misma** clave y el mismo body.
- No reintentar `4xx`; `409` se trata como `REJECTED`, sin reintento.
- **No cambiar la clave** para forzar una nueva ejecución: eso puede duplicar una operación.
- Sin Redis, la reserva vive en el proceso y se pierde al reiniciar; en despliegues con varios
  procesos o reinicios, usar Redis compartido.

## Errores HTTP del turno

| HTTP | `detail` | Semántica |
|---|---|---|
| `400` | `Revisá los datos del mensaje para continuar.` | JSON, body o headers inválidos o ausentes |
| `401` | `No autorizado` | Bearer ausente/incorrecto o token sin configurar |
| `409` | `El mensaje ya se está procesando.` | Conflicto de idempotencia |
| `503` | `No podemos atender tu mensaje en este momento.` | Fallo del procesador del turno |

Los errores no exponen entradas de Pydantic, stack traces, tokens ni credenciales. Una degradación
controlada puede devolver `200` con un mensaje seguro y `escalate=true`.

## Redis y memoria

- Producción requiere `AGENT_REDIS_URL` y `AGENT_MEMORY_KEY_SECRET` con al menos 32 caracteres. Sin
  Redis, H9 usa sesión e idempotencia en memoria y no tiene largo plazo: solo desarrollo local.
- Un fallo de Redis durante el turno se traduce en `503` genérico; la reserva de idempotencia puede
  quedar tomada hasta su TTL.
- La sesión expira por inactividad (`AGENT_SESSION_TTL_MINUTES`, 30 por defecto). Al expirar, H9
  reconstruye continuidad con el `context.history` que envíe H0.2, si existe.
- `blocked`/`unlinked` borran el perfil de largo plazo antes del turno. `list_sessions` no está
  habilitado: no se puede enumerar conversaciones.
- `AGENT_MEMORY_SERVER_URL` está reservado y no interviene; la memoria la implementa H9 sobre Redis
  directo. Detalle en [ADR-150](ADR-150-redis-memory.md).

## Checklist de verificación

```powershell
# 1. Proceso y health
curl.exe http://localhost:8103/health

# 2. Turno sintético (sin datos reales)
$headers = @{
  Authorization = "Bearer $env:AGENT_SERVICE_TOKEN"
  "X-Correlation-Id" = "7aa50f54-a81c-45d7-912f-aabdf69a07a2"
  "X-Conversation-Id" = "conversation-test"
  "X-Message-Id" = "message-test"
  "X-Idempotency-Key" = "message-test"
}
$body = @{
  correlationId = "7aa50f54-a81c-45d7-912f-aabdf69a07a2"
  conversationId = "conversation-test"
  messageId = "message-test"
  customerAddress = "synthetic-address"
  body = "Hola"
  context = @{ technicalContext = @{}; history = @() }
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri http://localhost:8103/api/v1/agent/turn `
  -Headers $headers -ContentType "application/json" -Body $body

# 3. Gates del componente
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest -q --cov=src --cov-fail-under=80

# 4. Configuración de compose, sin levantar contenedores
docker compose -f ai/docker-compose.yml --env-file ai/.env config --quiet
```

## Limitaciones conocidas

1. **H7 sin `/mcp`**: no hay integración real todavía; solo `/health`. La operación contra productos
   no puede validarse hasta que H7 publique el transporte.
2. **Puerto 8103 frente a 8099**: el default de H0.2 es provisional y se corrige por entorno
   (`H9_BASE_URL`). Documentado en
   [`turn.md`](../../../shared/contracts/wa-conversation-agent/turn.md#transporte-y-autenticación).
3. **Sin logs estructurados ni métricas propias**: la observabilidad depende de H7/H8 y de la
   respuesta del turno.
