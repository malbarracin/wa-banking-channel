# ADR-150: persistencia Redis con proyeccion segura

TASK-150 requiere TTL por inactividad, borrado al bloquear/desvincular y exclusion
de credenciales, documentos, saldos y PII. Tiene precedencia sobre ADR-010,
que proponia cachear credenciales en la memoria de sesion.

Se inspeccionaron las APIs instaladas de google-adk 1.39.1 y adk-redis 0.0.10.
RedisSessionMemoryService pasa texto y state_delta al backend sin filtrarlos,
captura errores de persistencia en append_event y no ofrece el contrato de
borrado de perfil requerido en RedisLongTermMemoryService. El TTL del backend
autohospedado se configura al crear Working Memory; no garantiza aqui la
renovacion atomica en cada lectura. Usar esas clases sin adaptacion no cumple
las garantias de esta tarea.

Se implementan BaseSessionService y BaseMemoryService sobre redis.asyncio.
adk-redis[memory] queda fijado en 0.0.10 y uv.lock registra la resolucion.
No se usa Agent Memory Server ni generacion automatica de recuerdos por LLM;
AGENT_MEMORY_SERVER_URL queda reservado, sin uso en esta implementacion.

Claves: h9:v1:session:HMAC(app, user, conversation) y
h9:v1:long:HMAC(app, customer), con locks temporales :lock. HMAC-SHA256 usa
AGENT_MEMORY_KEY_SECRET (minimo 32 caracteres, secreto estable por despliegue).
Los valores no contienen identificadores originales. No hay KEYS ni indice
reversible; list_sessions falla explicitamente por no estar habilitada la enumeracion.
session.id sigue siendo conversationId en la interfaz ADK.

GETEX y SET EX renuevan 1800 segundos (AGENT_SESSION_TTL_MINUTES=30).
La escritura serializada conserva los ultimos 100 eventos y el estado permitido.
Se conserva orden, rol y solamente frases de vocabulario cerrado; el resto del
texto se sustituye por [contenido omitido]. No se guardan payloads de herramientas,
metadata, identificadores de invocacion ni delta arbitrario. Esto sacrifica
historial textual libre para garantizar que secretos opacos tampoco se persisten.
El historial recibido desde H0.2 sigue disponible durante el turno.

Largo plazo acepta solo language, response_style y summary con valores enumerados.
Los resumenes son categorias, no texto libre ni datos de productos. La ampliacion
del vocabulario requiere revision de privacidad. No se infieren vinculos desde el
prompt ni desde session.state. for_turn recibe MemoryScope del procesador confiable;
ContextVar aisla invocaciones concurrentes. Sin scope activo no hay acceso.
Al bloquear/desvincular se borra el perfil antes de ejecutar el turno. Un lock
por cliente serializa el scope (maximo 120 segundos); Runner debe tener timeout
inferior y respetar el orden del contexto de vinculo provisto por H0.2.

Runner se integra en su tarea posterior; debe usar los servicios de la fabrica,
abrir for_turn incluso para turnos bloqueados/desvinculados y cerrar el cliente
Redis compartido al apagar. Si el contexto no proporciona el cliente previamente
vinculado, no inventar su identificador: el procesador debe resolverlo antes.
