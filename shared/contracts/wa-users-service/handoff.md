# Servicio de usuarios v1

Puerto interno: **8080**. Base: `/api/v1/users`. Las respuestas son JSON; el alta devuelve `Location` con la ruta del usuario creado.

Enviar `Authorization: Bearer <token>` con un JWT firmado por el gateway/IAM según [gateway-trust.md](gateway-trust.md). El servicio obtiene la identidad y el rol de los claims verificados, sin confiar en headers de actor. `X-Correlation-Id` es opcional: acepta entre 1 y 64 caracteres ASCII alfanuméricos, punto, guion o guion bajo; si falta o es inválido se genera un UUID. La respuesta devuelve el identificador efectivo, que se registra en auditoría y se propaga al evento.

| Operación | Roles permitidos |
| --- | --- |
| Alta, actualización, cambio de estado | OPERACIONES |
| Consulta por ID o documento | OPERACIONES, SERVICIO_INTERNO |
| Historial de auditoría | OPERACIONES, SOPORTE |

La ausencia de token o un token inválido produce 401 `INVALID_TOKEN`; un token vencido produce 401 `TOKEN_EXPIRED`; claims insuficientes o un rol sin permiso producen 403 `INSUFFICIENT_CLAIMS`. El documento se devuelve enmascarado. Para buscar por documento se envía su número completo como parámetro: proteger la URL y evitar registrarla en logs.

El alta crea usuarios `ACTIVO`. Transiciones admitidas: `ACTIVO` a `SUSPENDIDO` o `BAJA_LOGICA`; `SUSPENDIDO` a `ACTIVO` o `BAJA_LOGICA`. `BAJA_LOGICA` es terminal; una transición al mismo estado se rechaza. Un usuario `SUSPENDIDO` o `BAJA_LOGICA` no puede iniciar vínculos de canal; el consumidor debe aplicar esta regla.

Tras confirmar la transacción de un cambio de estado exitoso se invoca el publicador `wa.users.status-changed`, con `{ "userId": "00000000-0000-4000-8000-000000000001", "estadoAnterior": "ACTIVO", "estadoNuevo": "SUSPENDIDO", "correlationId": "ejemplo-1" }`. La implementación actual solo registra el evento en el log: todavía no lo entrega a un broker ni garantiza entrega durable.

Supuestos pendientes de confirmar: Compliance debe validar los datos mínimos de alta (tipo y número de documento, nombre completo, fecha de nacimiento, email y teléfono); debe acordarse el identificador de consulta (actualmente UUID interno y búsqueda por documento); el banco debe confirmar los campos actualizables (nombre de tratamiento, email, teléfono y preferencias).
