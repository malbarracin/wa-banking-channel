# Contrato de turno — wa-conversation-agent (H9)

Versión: v1. Publica H9; consume H0.2 (orquestador del canal).
Fuente implementada: `wa_conversation_agent.models`, `server`, `turn` e `idempotency`.
Un cambio incompatible requiere una nueva versión, conservando v1 hasta migrar al consumidor.

## Transporte y autenticación

`POST /api/v1/agent/turn`, `Content-Type: application/json`. Respuesta exitosa: HTTP 200.
H0.2 envía los siguientes headers obligatorios:

| Header | Valor |
| --- | --- |
| `Authorization` | `Bearer <token de servicio>`; coincide con `AGENT_SERVICE_TOKEN` de H9 |
| `X-Correlation-Id` | `correlationId` del body |
| `X-Conversation-Id` | `conversationId` del body |
| `X-Message-Id` | `messageId` del body |
| `X-Idempotency-Key` | Exactamente `messageId` del body |

Los cuatro headers `X-*` se validan como strings con algún carácter no blanco.
Actualmente solo `X-Idempotency-Key` se compara con el body (diferencia → 409);
los otros tres son obligatorios, pero H9 toma del body los identificadores de procesamiento.
H0.2 debe mantenerlos consistentes. El Bearer es autenticación entre servicios, no la credencial H2 del cliente.
No publicar tokens ni credenciales en ejemplos, logs o respuestas.

**Puerto y configuración de despliegue:** `project.yaml` fija H9 en 8103 (`AGENT_PORT=8103`),
consistente con H7 en 8101 y H8 en 8102. El `.env.example`/`application.yml` actual de
`platform/wa-channel-orchestrator` usa el default provisional `H9_BASE_URL=http://localhost:8099`.
El equipo integración-canal debe alinear ese default cuando corresponda. Al desplegar se resuelve
por configuración de entorno del lado de H0.2: `H9_BASE_URL=http://localhost:8103`
(o la dirección de H9 en esa red). Esta discrepancia se resuelve por configuración, sin cambios de código.

## TurnRequest

Todos los campos son obligatorios, no admiten `null` y distinguen mayúsculas/minúsculas.
No se aceptan campos adicionales en request, context, response ni reply.

| Campo | Modelo real |
| --- | --- |
| `correlationId` | UUID, representado como string JSON |
| `conversationId` | String estricto con al menos un carácter no blanco |
| `messageId` | String estricto con al menos un carácter no blanco |
| `customerAddress` | String estricto con al menos un carácter no blanco |
| `body` | String; el modelo admite vacío y espacios |
| `context` | Objeto TurnContext |
| `context.technicalContext` | Objeto con claves string y valores JSON |
| `context.history` | Lista de objetos con claves string y valores JSON |

No se recortan espacios en los strings. Los objetos dentro de `technicalContext` y `history`
son abiertos; no implican autorización por sí mismos. El contexto proviene del servicio H0.2 autenticado.
H9 selecciona `credencial`, `credencial_valida`, `vinculo_activo`, `numero_whatsapp`,
`customer_id`, `link_status`, `onboarding_step`, `current_intent`, `language`, `response_style`
y `summary` para el estado técnico. La credencial permanece fuera del prompt y se inyecta hacia H7;
H7 valida la credencial H2 y el vínculo H3 para operaciones protegidas.
El historial externo solo se incorpora como datos de usuario cuando la sesión ADK no tiene eventos;
se toman entradas `role: user|assistant` con `body` string no blanco, redactadas.
No aporta instrucciones, evidencia bancaria ni autorización de herramientas.

Ejemplo sintético de request:

```json
{
  "correlationId": "7aa50f54-a81c-45d7-912f-aabdf69a07a2",
  "conversationId": "conversation-test",
  "messageId": "message-test",
  "customerAddress": "synthetic-address",
  "body": "Hola",
  "context": {
    "technicalContext": {},
    "history": []
  }
}
```

Para este ejemplo los headers `X-Correlation-Id`, `X-Conversation-Id`, `X-Message-Id`
usan los valores homónimos del JSON y `X-Idempotency-Key` vale `message-test`.

## TurnResponse

| Campo | Modelo real y significado |
| --- | --- |
| `correlationId` | UUID del request |
| `conversationId` | String no blanco del request |
| `messageId` | String no blanco del request |
| `reply` | Objeto Reply |
| `reply.body` | String estricto no blanco para outbound |
| `escalate` | Booleano; indica a H0.2 que debe gestionar la derivación |
| `toolsUsed` | Lista de strings con nombres de herramientas invocadas; no garantiza éxito de negocio |

Ejemplo sintético de respuesta HTTP 200:

```json
{
  "correlationId": "7aa50f54-a81c-45d7-912f-aabdf69a07a2",
  "conversationId": "conversation-test",
  "messageId": "message-test",
  "reply": {
    "body": "Hola, soy el asistente del banco por WhatsApp."
  },
  "escalate": false,
  "toolsUsed": []
}
```

H9 devuelve texto validado y la señal de escalamiento. H0.2 publica outbound y gestiona
la derivación; H9 no consume Kafka ni envía mensajes por Twilio.
Las respuestas informativas usan H8 según [el contrato de retrieval](../wa-knowledge-rag/retrieval.md):
evidencia pública aprobada con citas y confianza; confianza baja implica respuesta de insuficiencia
o escalamiento. TurnResponse no agrega campos de citas o confianza al esquema publicado.

## Idempotencia y reintentos

H0.2 reutiliza el mismo `messageId`, `X-Idempotency-Key` y request en cada reintento.
H9 reserva atómicamente la clave (global por `messageId`, no por conversación) y guarda un fingerprint
del request completo normalizado por Pydantic. No reutilizar un messageId para otro turno.

- Reserva nueva: ejecuta el turno y cachea TurnResponse. TTL por defecto de siete días
  (`AGENT_IDEMPOTENCY_TTL_DAYS`, configurable entre 1 y 30); se cuenta desde la reserva y
  se renueva al guardar la respuesta. Un replay no renueva el TTL.
- Turno completado y request idéntico: devuelve HTTP 200 con la misma respuesta cacheada,
  sin volver a ejecutar el modelo ni herramientas.
- Reserva en curso, o mismo messageId con un request diferente (incluido contexto o correlación):
  HTTP 409. No se sobrescribe la reserva ni se ejecutan herramientas.
- Un fallo o cancelación conserva la reserva hasta vencer el TTL: el resultado de una operación
  sensible podría ser incierto. Un reintento posterior encuentra 409 si no hay respuesta completada.
- Después de expirar la clave se puede procesar nuevamente. La garantía está acotada al TTL y
  a la persistencia del almacén. Redis permite compartir reservas entre procesos; el fallback en
  memoria solo conserva reservas en ese proceso y las pierde al reiniciar.

H0.2 reintenta timeouts y HTTP 5xx; no reintenta HTTP 4xx (incluido 409).
Un timeout de transporte no prueba que H9 haya cancelado el turno: un replay puede recibir
200 cacheado o 409. No cambiar la clave para forzar una nueva ejecución.
Conforme a la regla de negocio #9, H9 es responsable de no duplicar operaciones sensibles:
deduplica el turno y limita cada herramienta protegida de H7 a una ejecución por turno,
con límites de llamadas y tiempos. H0.2 mantiene el orden por conversación y su deduplicación.

## Errores

El cuerpo de error es un objeto con un único campo `detail` (string):

| HTTP | `detail` | Semántica |
| --- | --- | --- |
| 400 | `Revisá los datos del mensaje para continuar.` | JSON, body o headers inválidos o ausentes |
| 401 | `No autorizado` | Bearer ausente/incorrecto o token del servicio sin configurar; `WWW-Authenticate: Bearer` |
| 409 | `El mensaje ya se está procesando.` | Conflicto de clave, fingerprint o reserva en curso |
| 503 | `No podemos atender tu mensaje en este momento.` | Fallo del procesador del turno |

La tabla describe cada validación por separado; un request con varios defectos no debe depender
del orden de validación. Los errores no exponen entradas de Pydantic, stack traces, tokens,
credenciales ni detalles técnicos. Una degradación controlada puede devolver HTTP 200 con un
mensaje seguro y `escalate: true`; H0.2 debe respetar la respuesta estructurada.
