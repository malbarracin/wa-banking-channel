# Servicio de vínculos del canal

El servicio administra el vínculo entre un número de WhatsApp y un cliente, sus preferencias y su historial de auditoría. No emite credenciales ni ejecuta operaciones bancarias. El contrato HTTP completo, incluidos cuerpos y errores, está en [openapi.yaml](openapi.yaml).

## Operaciones

Todas las rutas comienzan con `/api/v1/bindings/{whatsappNumber}`, donde el número usa formato E.164:

- `GET /api/v1/bindings/{whatsappNumber}`: consultar estado del vínculo, sin barra final.
- `POST /terms`: aceptar o rechazar términos.
- `POST /verify`: verificar identidad y activar el vínculo.
- `GET /profile` y `PATCH /preferences`: consultar el perfil y modificar preferencias.
- `POST /block` y `POST /unbind`: bloquear o desvincular.
- `GET /history`: consultar el historial para soporte.

## Ejecución y configuración

Desde `services/wa-channel-service`, ejecutar `./mvnw spring-boot:run` (en Windows, `mvnw.cmd spring-boot:run`). El puerto predeterminado es 8080 y se ajusta con `SERVER_PORT`. La base de datos se configura mediante `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD`. Swagger está en `/swagger-ui.html`.

Las propiedades `bank.channel.*` se configuran por entorno; los valores incluidos en el servicio son para desarrollo:

- `verification.policy`, `verification.max-attempts` y `verification.window-minutes`: política, límite y ventana de intentos.
- `name`, `support-phone` y `onboarding-url`: datos para los mensajes al cliente.
- `internal-roles.support` y `internal-roles.bank-policy`: valores autorizados del rol interno.
- `users-service.base-url` y `users-service.timeout-ms`: dirección y plazo de consulta del servicio de usuarios.

El historial requiere `X-Internal-Role` con el valor configurado para soporte. Para `block` y `unbind` con actor `BANCO`, el header debe coincidir con el rol de política del banco. La cabecera es una autorización interna y debe protegerse en el límite de la red.

## Resultado y estados

Tras aceptar términos, el vínculo queda `PENDIENTE_VERIFICACION`. Una verificación correcta lo lleva a `ACTIVO` y devuelve `verifiableResult`, con identificadores y fecha de verificación; el consumidor puede entonces continuar con la emisión de la credencial por el componente correspondiente. El agotamiento de intentos lleva a `BLOQUEADO` y devuelve 423 con indicación de soporte. Un vínculo activo puede pasar a `BLOQUEADO` o `DESVINCULADO`; ambas respuestas indican `credentialRevocationRequired=true`, que exige revocar la credencial. Desde `DESVINCULADO` se inicia nuevamente con términos y verificación.

Los errores tienen la forma `code`, `message`, `details` (lista) y `timestamp` UTC. Entre los códigos están `BAD_REQUEST`, `VALIDATION_ERROR`, `FORBIDDEN` (403), `NOT_FOUND` (404), `CONFLICT` (409), `VERIFICATION_LOCKED` (423) e `INTERNAL_ERROR` (500).

## Limitación conocida

La verificación recibe el `clientId` interno de `wa-users-service` desde el consumidor. El contrato de ese servicio no ofrece búsqueda por `uniquenessValue`.
