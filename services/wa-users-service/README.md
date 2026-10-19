# wa-users-service — Servicio de usuarios

Administra personas clientes del banco para el canal WhatsApp: alta, consulta por ID o documento,
actualización de datos permitidos y estados operativos (activo, suspendido, baja lógica). Es la base
de identidad que otros servicios consultan; **no** gestiona WhatsApp, vínculos de canal ni sesiones.

## Funcionalidades

- Alta de usuario activo — `POST /api/v1/users`
- Consulta por ID interno — `GET /api/v1/users/{id}`
- Consulta por documento — `GET /api/v1/users?documentType=&documentNumber=`
- Actualización parcial de datos permitidos — `PATCH /api/v1/users/{id}`
- Cambio de estado operativo — `PATCH /api/v1/users/{id}/status`
- Aptitud para vínculo de canal — campo `channelLinkEligible` en la respuesta (solo `ACTIVE`)

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5 |
| Base de datos | PostgreSQL 16 (H2 en tests) |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi (Swagger UI) |

## Requisitos

- JDK 21
- Docker / Docker Compose (opcional, para Postgres y la app en contenedor)
- Variables `DB_USERNAME` y `DB_PASSWORD` (sin valores por defecto; no versionar secretos)

## Cómo correrlo

### Local

Con Postgres levantado (por ejemplo solo el servicio de Compose) y el perfil `dev`:

```powershell
docker compose up -d postgres
$env:SPRING_PROFILES_ACTIVE='dev'
$env:DB_NAME='wa_users'
$env:DB_USERNAME='app'
# Definir DB_PASSWORD en la sesión (mismo valor local que en .env)
.\mvnw.cmd spring-boot:run
```

O empaquetar y ejecutar el JAR:

```sh
./mvnw -DskipTests package
java -jar target/wa-users-service-0.1.0-SNAPSHOT.jar
```

Puerto por defecto: `8080` (`PORT`).

### Con Docker

```powershell
Copy-Item .env.example .env
# Completar DB_PASSWORD en .env
docker compose up --build -d --wait --wait-timeout 240
```

Detalle de Compose, Helm y CI: [`delivery/devops-local.md`](delivery/devops-local.md).

## Configuración

| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|
| `PORT` | Puerto HTTP | `8080` |
| `DB_URL` | JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/wa_users` |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales BD | sin default |
| `JPA_DDL_AUTO` | Hibernate DDL | `validate` (`update` en perfil `dev`/`docker`) |
| `SPRING_PROFILES_ACTIVE` | Perfil (`dev`, `docker`, `test`, `prod`) | — |
| `springdoc.swagger-ui.enabled` | UI Swagger | `true` (desactivada en `prod`) |

## API

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs · copia estática en [`delivery/openapi/openapi.json`](delivery/openapi/openapi.json) y [`.yaml`](delivery/openapi/openapi.yaml)

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| POST | `/api/v1/users` | Alta con datos mínimos | 201, 400, 409, 500 |
| GET | `/api/v1/users/{id}` | Consulta por ID | 200, 400, 404 |
| GET | `/api/v1/users` | Consulta por documento | 200, 400, 404 |
| PATCH | `/api/v1/users/{id}` | Actualiza datos permitidos | 200, 400, 404, 409, 500 |
| PATCH | `/api/v1/users/{id}/status` | Cambia estado operativo | 200, 400, 404, 409, 500 |

### Errores

Contrato uniforme `{code, message, details, timestamp}` (`details` nunca es `null`):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [{ "field": "documentType", "message": "no debe estar vacío" }],
  "timestamp": "2026-10-05T14:30:00Z"
}
```

| HTTP | code | Caso típico |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation / contacto ausente |
| 400 | `BAD_REQUEST` | UUID mal formado, campo no permitido, params inválidos |
| 404 | `NOT_FOUND` | Usuario inexistente |
| 409 | `CONFLICT` | Documento duplicado, transición inválida, update en baja lógica |
| 500 | `INTERNAL_ERROR` | Error inesperado (sin stacktrace) |

## Probar la API

- Postman: importar [`delivery/postman/wa-users-service.postman_collection.json`](delivery/postman/wa-users-service.postman_collection.json) y el entorno [`delivery/postman/wa-users-service.postman_environment.json`](delivery/postman/wa-users-service.postman_environment.json)
- Bruno: abrir la colección [`delivery/bruno/wa-users-service/`](delivery/bruno/wa-users-service/)
- Paso a paso para QA: [`delivery/qa-manual.md`](delivery/qa-manual.md)

## Tests

```sh
./mvnw -q -B verify
```

En Windows: `.\mvnw.cmd -q -B verify`. Cubre unitarios e integración (MockMvc + H2). JaCoCo exige ≥80% de líneas; reporte en `target/site/jacoco/index.html`. El test `OpenApiExportTest` regenera `delivery/openapi/` en cada verify.

## Estructura del proyecto

```
com.bank.users/
├── UsersServiceApplication.java
├── config/          # OpenApiConfig
├── exception/       # contrato de error global
├── user/            # controller, service, repository, mapper, entity, dto, exception
└── audit/           # AuditService / AuditLog (sin API pública en H1)
```

## Documentación

| Documento | Para qué |
|---|---|
| [`delivery/api.md`](delivery/api.md) | Uso de la API con ejemplos curl |
| [`delivery/architecture-overview.md`](delivery/architecture-overview.md) | Capas, auditoría y flujo de una petición |
| [`delivery/qa-manual.md`](delivery/qa-manual.md) | Cómo probar con Postman/Bruno |
| [`delivery/devops-local.md`](delivery/devops-local.md) | Docker, CI y despliegue |
| [`delivery/openapi/`](delivery/openapi/) | Contrato OpenAPI exportado |
| [`specs/`](specs/) | Requisitos de negocio (fuente) |
| `../../shared/contracts/wa-users-service/` | Handoff H1 para consumidores del monorepo |
