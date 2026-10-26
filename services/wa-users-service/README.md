# wa-users-service — Servicio de usuarios bancarios

Administra personas clientes del banco (hito H1): alta, consulta por documento, actualización
de datos permitidos y cambio de estado operativo. Es la base de identidad del canal
(WhatsApp H3, sesiones H2); **no** gestiona el canal, la verificación de identidad ni las
credenciales de sesión.

## Funcionalidades

- Alta de usuario — `POST /api/v1/users`
- Consulta por documento — `GET /api/v1/users/{tipo}/{numero}`
- Consulta de estado (H3) — `GET /api/v1/users/{tipo}/{numero}/status`
- Actualización de datos permitidos — `PATCH /api/v1/users/{tipo}/{numero}`
- Cambio de estado — `PATCH /api/v1/users/{tipo}/{numero}/status`
- Auditoría interna de alta / actualización / cambio de estado (sin endpoint HTTP)

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Base de datos | PostgreSQL 16 (H2 en perfil `test`) |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi (Swagger UI) |
| Mapeo | MapStruct · Lombok |

## Requisitos

- JDK 21
- Docker + Compose v2 (para levantar app + Postgres)
- Variables de entorno según perfil (ver abajo)

## Cómo correrlo

Desde `services/wa-users-service`. En PowerShell usar `.\mvnw.cmd` en lugar de `./mvnw`.

### Local (perfil `dev`)

```sh
# Postgres accesible (local o solo el servicio del compose)
# export DB_URL=jdbc:postgresql://localhost:5432/wa_users
# export DB_USERNAME=app
# export DB_PASSWORD=cambiar
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Solo Postgres del compose y app en el host:

```sh
docker compose up -d postgres
# Si DB_HOST_PORT≠5432, ajustar DB_URL (ej. jdbc:postgresql://localhost:5433/wa_users)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Con Docker

```sh
cp .env.example .env   # PowerShell: copy .env.example .env
# Editar .env (no commitear). Si 5432 está ocupado: DB_HOST_PORT=5433
docker compose up -d --build
curl http://localhost:8080/actuator/health
```

Detalle de CI, Helm y runbook: [`../../delivery/devops-local.md`](../../delivery/devops-local.md).

## Configuración

| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|
| `PORT` / `APP_PORT` | Puerto HTTP | `8080` |
| `DB_URL` | JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/wa_users` |
| `DB_USERNAME` | Usuario BD | `postgres` (compose: ver `.env`) |
| `DB_PASSWORD` | Contraseña BD | vacío (local); obligatorio en `prod` |
| `DB_NAME` | Nombre BD (compose / docker) | `wa_users` |
| `DB_HOST_PORT` | Puerto host → Postgres | `5432` |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring | — (`dev`, `docker`, `prod`, `test`) |

Perfiles: `dev` (ddl `update`), `docker` (JDBC a host `postgres`), `prod` (vars obligatorias, ddl `validate`, Swagger UI off), `test` (H2).

## API

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs · copia estática en `delivery/openapi/openapi.json` y `.yaml`

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| POST | `/api/v1/users` | Alta ACTIVE | 201, 400, 409 |
| GET | `/api/v1/users/{tipo}/{numero}` | Consulta completa | 200, 404 |
| GET | `/api/v1/users/{tipo}/{numero}/status` | Documento + estado | 200, 404 |
| PATCH | `/api/v1/users/{tipo}/{numero}` | Datos permitidos | 200, 400, 404 |
| PATCH | `/api/v1/users/{tipo}/{numero}/status` | Cambio de estado | 200, 400, 404 |

Guía con curl y flujo típico: [`delivery/api.md`](delivery/api.md).

### Errores

Contrato único (`code`, `message`, `details`, `timestamp` UTC); sin stacktraces.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [{ "field": "nombre", "message": "El nombre es obligatorio" }],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

| HTTP | `code` |
|------|--------|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` |
| 404 | `NOT_FOUND` |
| 409 | `CONFLICT` (documento duplicado) |
| 500 | `INTERNAL_ERROR` |

Transición de estado inválida → **400** `BAD_REQUEST` (no 409).

## Guía de uso (H1)

Flujo típico: **alta → consulta → actualización → consulta de estado → cambio de estado**.

**Fuera de alcance de H1** (equipos H2/H3 no deben asumirlos aquí):

- Vincular número de WhatsApp (H3)
- Verificación de identidad por el canal (H3)
- Emisión/revocación de credencial de sesión (H2)
- Productos (cuentas, tarjetas, etc.)
- Cambio de datos legales sensibles

Fuente: [`specs/usuarios/spec.md`](../../specs/usuarios/spec.md).

### Limitaciones conocidas

1. Filtrado de campos de consulta por perfil: pendiente de Compliance (hoy se expone el response completo).
2. Catálogo de tipos de documento: no cerrado (string libre).
3. Formato de email/teléfono: no cerrado (solo se exige al menos un contacto en el alta).
4. Reactivación desde `LOGICAL_DELETE`: no soportada en este hito.

## Probar la API

- Postman: `delivery/postman/wa-users-service.postman_collection.json` + entorno `.postman_environment.json`
- Bruno: abrir `delivery/bruno/wa-users-service/`
- Paso a paso QA: [`delivery/qa-manual.md`](delivery/qa-manual.md)

## Tests

```sh
./mvnw -q -B verify
```

Unitarios + integración (perfil `test` / H2). JaCoCo ≥ 80 % en `verify`. El test `OpenApiExportTest` regenera `delivery/openapi/`.

## Estructura del proyecto

```
com.bcra.users.ws/
├── UserApplication.java
├── user/{controller,service,repository,mapper,entity,dto,exception,validation}
├── config/          # OpenApiConfig
└── exception/       # contrato de error global
```

## Documentación

| Documento | Para qué |
|---|---|
| [`delivery/api.md`](delivery/api.md) | Uso de la API con curl |
| [`delivery/architecture-overview.md`](delivery/architecture-overview.md) | Capas, decisiones y flujo |
| [`delivery/qa-manual.md`](delivery/qa-manual.md) | Postman / Bruno |
| [`../../delivery/devops-local.md`](../../delivery/devops-local.md) | Docker, CI, Helm |
| [`specs/usuarios/spec.md`](../../specs/usuarios/spec.md) | Requisitos de negocio |
