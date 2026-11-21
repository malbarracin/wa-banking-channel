# Servicio de Usuarios (`wa-users-service`)

Servicio de dominio REST para la administración de clientes del banco: alta, consulta, actualización de los datos permitidos y gestión del estado operativo (activo, suspendido, baja lógica). Es el registro base sobre el que otros componentes identifican al cliente antes de vincular un canal.

Es un backend HTTP clásico (Java 21 + Spring Boot 3, paquete base `com.bcra.users.ws`, puerto `8080` dentro del contenedor). No contiene agente, MCP, RAG, pgvector ni Redis.

## Propósito y límites

**Hace**

- Alta de usuarios con validación de los datos mínimos obligatorios y de unicidad por documento.
- Consulta por identificador interno (UUID) y por documento.
- Actualización de los campos no sensibles permitidos (nombre de tratamiento, email, teléfono y preferencias).
- Cambio de estado operativo con validación de la política de transiciones.
- Registro de auditoría de altas, actualizaciones y cambios de estado, con `correlationId`.
- Publicación de un evento de cambio de estado para los servicios dependientes.

**No hace** (límites con otros componentes)

- Vincular un número de WhatsApp ni verificar la identidad del cliente por el canal (Canal WhatsApp).
- Emitir o revocar la credencial de sesión del canal (Acceso y Sesión).
- Consultar u operar productos: cuentas, tarjetas, seguros.
- Modificar datos legales sensibles (domicilio legal, estado civil, etc.) salvo política expresa del banco.
- Hacer onboarding comercial de productos.
- Emitir el JWT del actor: valida el token firmado por el gateway/IAM (`Authorization: Bearer`) según `shared/contracts/wa-users-service/gateway-trust.md`.

## Stack

| Pieza | Uso |
| --- | --- |
| Java 21 + Spring Boot 3.5 | Base del servicio |
| Spring Web / Bean Validation | API REST y validación de entrada |
| Spring Data JPA + PostgreSQL + Flyway | Persistencia y migraciones (`db/migration/V1__init.sql`) |
| Spring Security + OAuth2 Resource Server | Validación JWT (JWKS) y autorización por rol del claim `role` |
| springdoc-openapi | Documentación de la API (`/v3/api-docs`, denegada en producción) |
| Actuator + Micrometer | `health`, `info` y contadores de operaciones |
| MapStruct / Lombok | Mapeo entidad ↔ DTO y reducción de boilerplate |
| H2 | Base en memoria para los perfiles `dev` y `test` |
| JaCoCo | Cobertura mínima del 80 % en `verify` |

## Compilar y probar (build / test)

Desde `services/wa-users-service`:

```bash
./mvnw verify          # Linux / macOS
mvnw.cmd -B verify     # Windows
```

`verify` ejecuta la suite de tests, el reporte JaCoCo y la comprobación del umbral del 80 % de cobertura. La misma secuencia corre en CI (`.github/workflows/wa-users-service.yml`). Los tests incluyen un control de que los paths y métodos publicados en `openapi.yaml` coinciden con los que expone la aplicación (`OpenApiContractTest`).

No aplican evals (`adk eval`) ni set dorado: este componente no tiene modelo de lenguaje. La verificación es la suite de tests más la cobertura.

Si la caché Maven del usuario es de solo lectura, se puede apuntar a una caché escribible dentro del proyecto:

```bash
MAVEN_OPTS=-Dmaven.repo.local=./target/test-maven-cache mvnw.cmd -B verify
```

## Ejecución local y con Docker

### Local con H2 (perfil `dev`)

El perfil `dev` usa H2 en memoria y desactiva Flyway; no hace falta PostgreSQL:

```bash
./mvnw -Pdev spring-boot:run -Dspring-boot.run.profiles=dev
```

### Local con PostgreSQL (perfil `prod`)

Definir `DB_URL`, `DB_USER` y `DB_PASSWORD` en el entorno y arrancar con el perfil `prod`. Flyway aplica la migración `V1__init.sql` al iniciar y Hibernate valida el esquema:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Con Docker Compose (desde la raíz del repositorio)

```bash
cp .env.example .env   # completar USERS_DB_*, USERS_GATEWAY_JWK_SET_URI y USERS_GATEWAY_JWT_ISSUER
docker compose up -d --build --wait
docker compose down    # bajar el entorno
```

Levanta `wa-users-postgres` y `wa-users-service` (perfil `prod`). El servicio escucha en `8080` dentro del contenedor y se publica en el host en `USERS_HTTP_PORT` (por defecto `8080`). La imagen compila en una etapa con el JDK y ejecuta con un JRE y un usuario sin privilegios (`services/wa-users-service/Dockerfile`).

## Configuración

Raíz del repositorio — `.env` para `docker-compose.yml` (copiar `.env.example`; `.env` no va a git):

| Variable | Para qué | Ejemplo |
| --- | --- | --- |
| `USERS_DB_NAME` | Nombre de la base PostgreSQL del contenedor | `nombre_bd_ejemplo` |
| `USERS_DB_USER` | Usuario de la base | `usuario_ejemplo` |
| `USERS_DB_PASSWORD` | Contraseña de la base (solo en `.env`, nunca en el repo) | — |
| `USERS_HTTP_PORT` | Puerto del host donde se publica el servicio; dentro del contenedor es `8080` | `8080` |
| `USERS_GATEWAY_JWK_SET_URI` | JWKS del gateway/IAM | `https://gateway.ejemplo/jwks` |
| `USERS_GATEWAY_JWT_ISSUER` | Emisor esperado del JWT | `https://gateway.ejemplo/iam` |

Servicio — `services/wa-users-service/.env.example` (consumido por `application.yml` / perfil `prod`):

| Variable | Para qué | Ejemplo |
| --- | --- | --- |
| `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://wa-users-postgres:5432/nombre_bd_ejemplo` |
| `DB_USER` | Usuario de la base | `usuario_ejemplo` |
| `DB_PASSWORD` | Contraseña de la base | — |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | Endpoint JWKS del gateway/IAM | `https://gateway.ejemplo/jwks` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Emisor esperado del JWT (`iss`) | `https://gateway.ejemplo/iam` |

La identidad y el rol salen de los claims verificados del JWT (`sub`, `role`). `X-Correlation-Id` sigue siendo opcional. Ver `shared/contracts/wa-users-service/gateway-trust.md`.

Perfiles: `dev` (H2, sin Flyway), `test` (H2 en modo PostgreSQL, usado por los tests) y `prod` (PostgreSQL, Flyway + `ddl-auto: validate`). Sin perfil, `application.yml` exige `DB_URL`, `DB_USER` y `DB_PASSWORD`.

## API

Base path: `/api/v1/users`. Todas las respuestas son JSON. El alta incluye el header `Location` con la ruta del usuario creado.

| Método | Path | Rol(es) | Éxito | Errores principales |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/users` | `OPERACIONES` | `201` + `UserResponse` | `400`, `401`, `403`, `409` (duplicado), `415`, `500` |
| `GET` | `/api/v1/users/{id}` | `OPERACIONES`, `SERVICIO_INTERNO` | `200` + `UserResponse` | `400`, `401`, `403`, `404`, `500` |
| `GET` | `/api/v1/users/by-document?tipo=&numero=` | `OPERACIONES`, `SERVICIO_INTERNO` | `200` + `UserResponse` | `400`, `401`, `403`, `404`, `500` |
| `PATCH` | `/api/v1/users/{id}` | `OPERACIONES` | `200` + `UserResponse` | `400`, `401`, `403`, `404`, `415`, `500` |
| `PATCH` | `/api/v1/users/{id}/status` | `OPERACIONES` | `200` + `UserResponse` | `400`, `401`, `403`, `404`, `409` (transición inválida), `415`, `500` |
| `GET` | `/api/v1/users/{id}/audit` | `OPERACIONES`, `SOPORTE` | `200` + lista de `AuditEventResponse` | `400`, `401`, `403`, `404`, `500` |

Los ejemplos usan datos ficticios. Tipos de documento: `DNI`, `PASAPORTE`, `CUIL`. Estados: `ACTIVO`, `SUSPENDIDO`, `BAJA_LOGICA`.

### Alta

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <jwt-del-gateway>' \
  -d '{
    "tipoDocumento": "DNI",
    "numeroDocumento": "00000000",
    "nombreCompleto": "Persona Ejemplo",
    "fechaNacimiento": "1990-01-01",
    "email": "ejemplo@example.invalid",
    "telefono": "00000000"
  }'
```

Respuesta `201` (el documento se devuelve enmascarado):

```json
{
  "id": "00000000-0000-4000-8000-000000000001",
  "tipoDocumento": "DNI",
  "numeroDocumentoEnmascarado": "***0000",
  "nombreCompleto": "Persona Ejemplo",
  "estado": "ACTIVO",
  "email": "ejemplo@example.invalid",
  "telefono": "00000000",
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-01T00:00:00Z"
}
```

### Consulta por documento y actualización

```bash
curl 'http://localhost:8080/api/v1/users/by-document?tipo=DNI&numero=00000000' \
  -H 'Authorization: Bearer <jwt-del-gateway>'

curl -X PATCH http://localhost:8080/api/v1/users/00000000-0000-4000-8000-000000000001 \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <jwt-del-gateway>' \
  -d '{"email": "nuevo@example.invalid", "preferencias": "Sin preferencias"}'
```

En la actualización, los campos omitidos o nulos no se modifican y se rechazan propiedades desconocidas (`400 VALIDATION_ERROR`).

### Cambio de estado

```bash
curl -X PATCH http://localhost:8080/api/v1/users/00000000-0000-4000-8000-000000000001/status \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <jwt-del-gateway>' \
  -d '{"nuevoEstado": "SUSPENDIDO", "motivo": "Solicitud de ejemplo"}'
```

### Formato de error

Todos los errores comparten la misma forma. El `code` es `VALIDATION_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`, `INVALID_TOKEN`, `TOKEN_EXPIRED` o `INSUFFICIENT_CLAIMS`; los stack traces no se devuelven al cliente.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [],
  "timestamp": "2026-01-01T00:00:00Z"
}
```

Cuando los errores vienen de la validación de campos, `details` contiene entradas `{ "field": "...", "message": "..." }`. La respuesta incluye el header `X-Correlation-Id` con el identificador efectivo de la solicitud.

## Seguridad

El gateway/IAM autentica al actor y emite un JWT. El consumidor envía `Authorization: Bearer <token>`; el servicio valida firma (JWKS), `iss`, `aud=wa-users-service`, `exp`, `iat` y `jti`, y toma `sub`/`role` de los claims. Los headers `X-Actor-Id` / `X-Actor-Role` no tienen autoridad. Detalle en [`gateway-trust.md`](../../shared/contracts/wa-users-service/gateway-trust.md).

| Header | Obligatorio | Descripción |
| --- | --- | --- |
| `Authorization` | Sí | `Bearer` + JWT del gateway. Ausente/inválido → `401 INVALID_TOKEN`; vencido → `401 TOKEN_EXPIRED`; claims insuficientes → `403 INSUFFICIENT_CLAIMS`. |
| `X-Correlation-Id` | No | 1–64 caracteres alfanuméricos ASCII, punto, guion o guion bajo. Si falta o es inválido se genera un UUID. Se devuelve en la respuesta. |

| Rol | Permisos |
| --- | --- |
| `OPERACIONES` | Alta, consulta, actualización, cambio de estado e historial de auditoría |
| `SOPORTE` | Solo historial de auditoría |
| `SERVICIO_INTERNO` | Solo consulta por id o por documento |

Los endpoints `/actuator/health` y `/actuator/info` son públicos; el resto de las rutas no listadas se deniegan. La credencial de sesión del canal nunca llega a este servicio.

## Estados y transiciones

El alta crea usuarios `ACTIVO`. Las transiciones admitidas son:

- `ACTIVO` → `SUSPENDIDO` o `BAJA_LOGICA`
- `SUSPENDIDO` → `ACTIVO` (reactivación) o `BAJA_LOGICA`

`BAJA_LOGICA` es terminal y una transición al mismo estado se rechaza con `409 CONFLICT`. Un usuario `SUSPENDIDO` o `BAJA_LOGICA` no puede iniciar nuevos vínculos de canal: esa regla la aplica el consumidor.

## Auditoría

Toda alta, actualización y cambio de estado inserta un evento en `audit_events` dentro de la misma transacción del cambio. La tabla es solo-append: la API no expone update ni delete. `GET /api/v1/users/{id}/audit` devuelve el historial resumido con `action` (`ALTA`, `ACTUALIZACION`, `CAMBIO_ESTADO`), `resultado` (`EXITOSO`, `RECHAZADO`), `fecha`, `correlationId`, `actor` (`rol:id`) y `resumen` (nombres de los campos cambiados, sin valores).

```json
[
  {
    "action": "CAMBIO_ESTADO",
    "resultado": "EXITOSO",
    "fecha": "2026-01-01T00:00:00Z",
    "correlationId": "ejemplo-1",
    "actor": "OPERACIONES:actor-ejemplo",
    "resumen": ["estado"]
  }
]
```

**PII enmascarada**: `numeroDocumento` se expone como `numeroDocumentoEnmascarado` (`***1234`, o `***` si tiene 4 caracteres o menos). Los logs no imprimen documento, email ni teléfono completos. Ninguna clave ni dato real de personas se guarda en el repositorio: las credenciales se leen de variables de entorno y solo existe `.env.example` con nombres vacíos o de ejemplo.

## Eventos

Tras confirmar la transacción de un cambio de estado exitoso se publica `wa.users.status-changed` con:

```json
{
  "userId": "00000000-0000-4000-8000-000000000001",
  "estadoAnterior": "ACTIVO",
  "estadoNuevo": "SUSPENDIDO",
  "correlationId": "ejemplo-1"
}
```

La publicación ocurre después del commit (`@TransactionalEventListener`, fase `AFTER_COMMIT`), de modo que la operación no depende del broker. **Implementación actual**: el publicador solo escribe el evento en el log (`LoggingStatusChangedPublisher`); todavía no hay entrega a un broker ni garantía de entrega durable. El detalle del transporte lo define la plataforma de eventos.

## Operación

- **Health**: `GET /actuator/health` (lo usa el healthcheck de Docker y de Compose); `GET /actuator/info`. Ambos son públicos.
- **Logs con `correlationId`**: el filtro `CorrelationIdFilter` lee o genera el identificador, lo devuelve en la cabecera `X-Correlation-Id` y lo pone en el MDC con la clave `correlationId`. Con él se puede seguir una solicitud de punta a punta, incluida su fila de auditoría y el evento de cambio de estado.
- **Métricas** (Actuator/Micrometer): `http.server.requests` (latencia por endpoint) y los contadores `users.created`, `users.duplicate.rejected` y `users.status.changed`.
- **Errores frecuentes**: `VALIDATION_ERROR` (datos inválidos o campos desconocidos), `BAD_REQUEST` (JSON o query malformados, tipo de documento inválido), `NOT_FOUND` (usuario inexistente), `CONFLICT` (documento duplicado o transición no permitida), `INVALID_TOKEN`/`TOKEN_EXPIRED`/`INSUFFICIENT_CLAIMS` (JWT ausente, inválido, vencido o sin permiso), `INTERNAL_ERROR` (error no esperado; el detalle queda solo en el log).

## Contrato

El contrato publicado está en [`shared/contracts/wa-users-service/`](../../shared/contracts/wa-users-service/):

- [`openapi.yaml`](../../shared/contracts/wa-users-service/openapi.yaml): especificación OpenAPI 3.0.3 de los seis endpoints, los DTOs, la seguridad Bearer JWT, los errores y sus ejemplos.
- [`handoff.md`](../../shared/contracts/wa-users-service/handoff.md): guía de consumo (base path, JWT, roles, estados, correlación y evento) para quienes integren con el servicio.
- [`gateway-trust.md`](../../shared/contracts/wa-users-service/gateway-trust.md): confianza del gateway/IAM (claims JWT, JWKS e issuer).

Las decisiones de diseño están en `docs/adr/0001` a `0004` (stack, identificador y unicidad, auditoría y evento de cambio de estado).
