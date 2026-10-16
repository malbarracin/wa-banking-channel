# wa-users-service — Servicio de usuarios

Registro de personas clientes del banco para el canal WhatsApp (hito H1). Expone alta,
consulta, actualización de datos permitidos, cambio de estado operativo y aptitud de
vínculo para servicios consumidores (H3). No gestiona WhatsApp, ni sesión de canal, ni
productos bancarios.

## Funcionalidades

- Alta validada con unicidad documental — `POST /api/v1/users`
- Consulta por UUID con historial resumido — `GET /api/v1/users/{id}`
- Actualización parcial de campos permitidos — `PATCH /api/v1/users/{id}`
- Cambio de estado según matriz H1 — `PATCH /api/v1/users/{id}/status`
- Aptitud de vínculo de canal — `GET /api/v1/users/{id}/channel-eligibility`

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5.x |
| Base de datos | PostgreSQL 16 (H2 en tests) |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi (Swagger UI) |

## Requisitos

- JDK 21
- PostgreSQL accesible para ejecución local (o Docker Compose; ver runbook)
- Red en la primera descarga de dependencias Maven

## Cómo correrlo

### Local

```powershell
# Variables mínimas (sin secretos reales en el repo)
$env:DB_USERNAME = 'wa_users'
$env:DB_PASSWORD = 'local-dev-password'
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

En Linux/macOS: `./mvnw spring-boot:run` con las mismas variables.
Puerto por defecto: **8080** (`PORT` opcional). Salud: `/actuator/health`.

### Con Docker

Ver paso a paso en [`delivery/devops-local.md`](delivery/devops-local.md)
(`docker compose up --build -d --wait` con `.env` desde `.env.example`).

## Configuración

| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|
| `PORT` | Puerto HTTP | `8080` |
| `DB_URL` | JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/wa_users` |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales BD | obligatorias (sin default) |
| `DB_NAME` / `DB_PORT` | Perfil `dev` | `wa_users` / `5432` |
| `SPRING_PROFILES_ACTIVE` | Perfil | sin perfil: `validate`; `dev`/`docker`: `update` |
| `JPA_DDL_AUTO` | Estrategia Hibernate | `validate` |
| `springdoc.swagger-ui.enabled` | UI Swagger | `false` en perfil `prod` |

## API

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs · copia estática en `delivery/openapi/openapi.json` y `.yaml`
- Handoff para consumidores (H3): `shared/contracts/wa-users-service/` (monorepo) y copia en `delivery/contracts/`

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| POST | `/api/v1/users` | Alta ACTIVO | 201, 400, 409, 500 |
| GET | `/api/v1/users/{id}` | Consulta + historial | 200, 400, 404, 500 |
| PATCH | `/api/v1/users/{id}` | Actualización parcial | 200, 400, 404, 500 |
| PATCH | `/api/v1/users/{id}/status` | Cambio de estado | 200, 400, 404, 409, 500 |
| GET | `/api/v1/users/{id}/channel-eligibility` | Aptitud H3 | 200, 400, 404, 500 |

### Errores

Contrato uniforme (`code`, `message`, `details`, `timestamp`):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [
    {"field": "tipoDocumento", "message": "El campo tiene un valor inválido"}
  ],
  "timestamp": "2026-10-15T12:00:00Z"
}
```

| HTTP | `code` |
|------|--------|
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST` |
| 404 | `NOT_FOUND` |
| 409 | `CONFLICT` |
| 500 | `INTERNAL_ERROR` |

### Límites del piloto H1

- Sin autenticación JWT ni filtros por actor
- Sin Kafka / notificaciones a servicios dependientes
- Sin vinculación WhatsApp ni credenciales de sesión
- Sin reactivación desde `BAJA_LOGICA`
- Unicidad: `(tipoDocumento, numeroDocumento)`; consulta solo por UUID interno

## Probar la API

- Postman: importar `delivery/postman/wa-users-service.postman_collection.json` y
  `delivery/postman/wa-users-service.postman_environment.json`
- Bruno: abrir `delivery/bruno/wa-users-service/`
- Paso a paso: [`delivery/qa-manual.md`](delivery/qa-manual.md)
- Ejemplos curl: [`delivery/api.md`](delivery/api.md)

## Tests

```powershell
.\mvnw.cmd -q -B verify
```

Cubre unitarios e integración (MockMvc + H2), contrato de errores y exportación OpenAPI.
JaCoCo exige ≥ 80 % de líneas; reporte en `target/site/jacoco/index.html`.

## Estructura del proyecto

```
com.bank.users/
├── UsersServiceApplication.java
├── config/                 # OpenApiConfig
├── exception/              # ErrorResponse, GlobalExceptionHandler
├── user/{controller,service,repository,mapper,entity,dto,exception}
└── audit/{service,repository,entity}
```

## Documentación

| Documento | Para qué |
|---|---|
| `delivery/api.md` | Uso de la API con ejemplos curl |
| `delivery/architecture-overview.md` | Capas, decisiones y flujo |
| `delivery/qa-manual.md` | Cómo probar con Postman/Bruno |
| `delivery/devops-local.md` | Docker, CI y despliegue |
| `delivery/contracts/` | Copia del handoff OpenAPI / H3 |
| `specs/` | Requisitos de negocio (fuente) |
