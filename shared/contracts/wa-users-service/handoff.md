# wa-users-service — handoff

Registro único de clientes del banco: alta, consulta, actualización de datos permitidos, cambio de estado operativo y auditoría. Otros componentes trabajan contra este contrato, no contra el código del servicio.

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/users` | Alta de cliente (queda `ACTIVE`) |
| `GET` | `/api/v1/users/{id}` | Consulta por identificador interno (solo campos públicos) |
| `PATCH` | `/api/v1/users/{id}` | Actualización de campos permitidos |
| `PATCH` | `/api/v1/users/{id}/status` | Cambio de estado operativo |
| `GET` | `/api/v1/users/{id}/audit` | Historial de auditoría del cliente |

Errores en formato único `{code, message, details, timestamp}` (`VALIDATION_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`). Detalle en `openapi.yaml`.

Swagger UI local: `/swagger-ui.html`. Documento OpenAPI en runtime: `/v3/api-docs` y `/v3/api-docs.yaml`.

## Cómo levantarlo

- Puerto: `8080` (configurable con `SERVER_PORT`).
- Desde `services/wa-users-service`: `./mvnw spring-boot:run` (en Windows: `.\mvnw.cmd spring-boot:run`).
- Requiere PostgreSQL alcanzable con las variables de datasource.
- Health: `GET /actuator/health`.

## Variables de entorno (sin valores)

| Variable | Uso |
|----------|-----|
| `SERVER_PORT` | Puerto HTTP (default 8080) |
| `DATABASE_URL` | JDBC URL de PostgreSQL |
| `DATABASE_USERNAME` | Usuario de la base |
| `DATABASE_PASSWORD` | Contraseña de la base |

No versionar secretos ni credenciales en este contrato ni en la configuración commiteada.

## Parámetros configurables `bank.users.*`

Definidos en `application.yml` del servicio (valores por defecto):

| Clave | Default | Significado |
|-------|---------|-------------|
| `bank.users.required-fields` | `uniquenessValue,displayName` | Campos mínimos del alta |
| `bank.users.uniqueness-field` | `uniquenessValue` | Campo de unicidad del banco |
| `bank.users.updatable-fields` | `displayName,contactEmail,contactPhone` | Campos admitidos en `PATCH /users/{id}` |
| `bank.users.public-fields` | `id,externalId,displayName,contactEmail,contactPhone,status,createdAt` | Campos expuestos en la consulta |
| `bank.users.allowed-transitions.ACTIVE` | `SUSPENDED, INACTIVE` | Transiciones desde ACTIVE |
| `bank.users.allowed-transitions.SUSPENDED` | `ACTIVE, INACTIVE` | Transiciones desde SUSPENDED |
| `bank.users.allowed-transitions.INACTIVE` | _(vacío)_ | Baja lógica: sin salida por defecto |

Un `PATCH` con un campo fuera de `updatable-fields` se rechaza con `400 BAD_REQUEST` (no se ignora).

## Autorización

Este hito **no autentica**. El actor lo informa el llamador:

- Header opcional `X-Actor` en alta y actualización (si se omite, el servicio usa `system`).
- Campo `actor` obligatorio en el body de `PATCH /api/v1/users/{id}/status`.

## Elegibilidad por estado (para consumidores)

Estados: `ACTIVE`, `SUSPENDED`, `INACTIVE`.

**Solo `ACTIVE` es elegible para un vínculo de canal nuevo.** Un cliente `SUSPENDED` o `INACTIVE` no debe usarse para vincular un canal.
