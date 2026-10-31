# Entrega de renovación H2 → H3 (v1)

Transporte interno HTTPS. Los JWT son credenciales confidenciales: cuerpos y respuestas no se registran,
no se incluyen en URLs, eventos públicos ni mensajes al cliente. La autenticación de posesión usa el
JWT emitido por H2. Las restricciones adicionales de identidad de servicio aplican igualmente a estos endpoints.

El scheduler de H2 guarda una entrega por credencial elegible en PostgreSQL (`entrega_renovacion`):
identificador de la anterior, nuevo `jti`, emisión y expiración. No guarda el JWT ni secretos.
La clave HS256 y estos claims permiten reconstruir el mismo JWT en cada entrega, incluso tras reiniciar
H2. La clave de firma debe mantenerse disponible durante la vigencia de las credenciales emitidas.

1. H3 llama `POST /api/v1/credenciales/renovacion/obtener` con
   `{"credencial":"JWT_ACTUAL"}`. H2 valida firma, identidad, vigencia, riesgo y estado de H1.
   Devuelve **200**, `Cache-Control: no-store`,
   `{"credencial":"JWT_NUEVO","fechaExpiracion":"2026-11-01T12:00:00Z"}`.
   **404** significa que aún no hay entrega preparada: conservar el actual y consultar más tarde.
2. H3 guarda durablemente el reemplazo junto con el actual, con estado de confirmación pendiente.
   Todavía usa el actual para operaciones. El nuevo no autoriza operaciones antes de confirmarlo.
3. H3 llama `POST /api/v1/credenciales/renovacion/confirmar` con
   `{"credencial":"JWT_NUEVO"}`. Presentar el nuevo demuestra su recepción.
   H2 bloquea la anterior, comprueba estado vigente sin riesgo, activa el nuevo, revoca el anterior
   con `REEMPLAZO` y audita todo en una transacción. Verifica también estado de H1. Devuelve **204**.
4. H3 usa el nuevo y descarta el anterior. Si pierde la respuesta de confirmación, conserva el nuevo
   pendiente y repite el paso 3 antes de seguir operando. La confirmación repetida devuelve **204**
   mientras el nuevo siga vigente; no vuelve a emitir, revocar ni auditar éxito.

Los métodos `CredencialesServiceClient.obtenerRenovacion` y `confirmarRenovacion` implementan estas
llamadas en H3. Se mantienen separados para que el responsable del almacenamiento confirme únicamente
después de guardar el token; el adaptador no confirma automáticamente al deserializar la respuesta.

La entrega no se consume al obtenerla: pérdidas de respuesta y reinicios permiten repetir el paso 1
con el mismo resultado. La unicidad por anterior y el bloqueo pesimista evitan reemplazos concurrentes.
Un fallo de inserción, auditoría o validación de H1 durante la confirmación revierte toda la rotación.
Una revocación, señal de riesgo o expiración de la anterior antes de confirmar impide activar la entrega;
se requiere el flujo de autenticación habitual. No se prolonga la vigencia del JWT anterior si H3 está caído.

Ambos endpoints aceptan exclusivamente JSON con `credencial` no vacía. Errores con el contrato común
`code`, `message`, `details`, `timestamp`: **400** VALIDATION_ERROR/BAD_REQUEST, **401** UNAUTHORIZED,
**404** NOT_FOUND (obtener), **409** CONFLICT ante conflicto de persistencia, **500** INTERNAL_ERROR.
Los errores de transporte/HTTP del adaptador H3 se propagan al responsable de reintentar y no se silencian.

Pruebas: `RenovacionIntegrationTest` (H2, entrega HTTP, pérdidas/reintentos, rollback y revocación)
y `ExternalClientsTest` (H3, recepción, confirmación separada, timeout y respuesta inválida).
