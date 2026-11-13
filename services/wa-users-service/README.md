# wa-users-service — Servicio de Usuarios

Administra a las personas clientes del banco: alta Compliance, consulta por vistas según actor, actualización de contacto y estado operativo (activo, suspendido, baja lógica). Es la base de identidad para otros servicios del canal. No gestiona WhatsApp, vínculos de teléfono ni credenciales de sesión.

## Funcionalidades

- `POST /api/v1/users` — Alta de cliente (ACTIVE); unicidad de documento y email
- `GET /api/v1/users/{id}` — Consulta por id (vista completa o básica según `X-Query-Actor`)
- `GET /api/v1/users?documentType=&documentNumber=` — Consulta por documento
- `PATCH /api/v1/users/{id}` — Actualización de campos de contacto/tratamiento
- `PATCH /api/v1/users/{id}/status` — Cambio de estado (baja lógica terminal)
- `GET /api/v1/users/{id}/audit` — Historial de auditoría
- `GET /actuator/health` — Salud

### Vistas de consulta (default)

Las consultas GET seleccionan la proyección con `X-Query-Actor`:

| Actor | Respuesta |
|-------|-----------|
| `BUSINESS_OPERATIONS`, `SUPPORT` | Dataset completo (`UserResponse`) |
| `BANK_SERVICE`, ausente o desconocido | Subset básico: `id`, `documentType`, `documentNumber`, `status`, `channelLinkEligible` |

`channelLinkEligible` es `true` solo en `ACTIVE`. `users.query.full-view-actors` (`USERS_QUERY_FULL_VIEW_ACTORS`) solo puede restringir actores internos; no amplía `BANK_SERVICE` ni desconocidos. La matriz exacta de campos visibles por actor sigue pendiente de definición por el banco. `X-Actor-Id` es solo auditoría y no modifica la vista.

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Base de datos | MongoDB 7 (`wa_users` local; H2 no aplica) |
| Build | Maven Wrapper (`./mvnw` / `mvnw.cmd`) |
| Documentación API | springdoc-openapi (Swagger UI) |

## Requisitos

- JDK 21+
- Docker / Docker Compose (Mongo local e integración)
- Maven Wrapper incluido (no requiere Maven global)

## Cómo correrlo

### Local

```powershell
cd services/wa-users-service
docker compose up -d --wait mongo
.\mvnw.cmd spring-boot:run
```

En POSIX: `./mvnw spring-boot:run`. Perfil por defecto: `local` → `mongodb://localhost:27017/wa_users`.

Alternativa Windows (package + jar):

```powershell
.\mvnw.cmd -DskipTests package
java -jar target/wa-users-service-0.0.1-SNAPSHOT.jar
```

### Con Docker

```powershell
cd services/wa-users-service
docker compose up -d --build --wait
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose down
```

Runbook completo (CI, imagen, Helm): [`../../delivery/devops-local.md`](../../delivery/devops-local.md).

## Configuración

| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|
| `SPRING_DATA_MONGODB_URI` | URI Mongo | `mongodb://localhost:27017/wa_users` (local) |
| `SERVER_PORT` / `APP_PORT` | Puerto HTTP | `8080` |
| `SPRING_PROFILES_ACTIVE` | Perfil | `local` |
| `USERS_QUERY_FULL_VIEW_ACTORS` | Actores internos con vista completa | `BUSINESS_OPERATIONS,SUPPORT` |
| `springdoc.swagger-ui.enabled` | UI Swagger | `false` en `prod` |

Ver `.env.example` (sin secretos). Mongo local de Compose no tiene autenticación y publica en loopback.

## API

- Swagger UI: http://localhost:8080/swagger-ui.html (grupo **v1**)
- OpenAPI: http://localhost:8080/v3/api-docs/v1 · copia estática en `delivery/openapi/openapi.json` y `.yaml`

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| POST | `/api/v1/users` | Alta Compliance | 201, 400, 409 |
| GET | `/api/v1/users/{id}` | Consulta por id | 200, 404 |
| GET | `/api/v1/users?documentType=&documentNumber=` | Consulta por documento | 200, 400, 404 |
| PATCH | `/api/v1/users/{id}` | Update contacto | 200, 400, 404, 409 |
| PATCH | `/api/v1/users/{id}/status` | Cambio de estado | 200, 400, 404, 409 |
| GET | `/api/v1/users/{id}/audit` | Auditoría | 200, 404 |

### Errores

```json
{
  "code": "CONFLICT",
  "message": "A user with the same document already exists",
  "details": [
    { "field": "documentNumber", "message": "Document already registered" }
  ],
  "timestamp": "2026-10-19T12:00:00Z"
}
```

| HTTP | code | Caso |
|------|------|------|
| 400 | `VALIDATION_ERROR` | Bean Validation / campo desconocido |
| 400 | `BAD_REQUEST` | Parámetros inválidos / campo no actualizable |
| 404 | `NOT_FOUND` | Usuario inexistente |
| 409 | `CONFLICT` | Documento/email duplicado o transición inválida |
| 500 | `INTERNAL_ERROR` | Error no controlado (sin stacktrace) |

## Probar la API

- Postman: importar `delivery/postman/wa-users-service.postman_collection.json` y el entorno `delivery/postman/wa-users-service.postman_environment.json`
- Bruno: abrir `delivery/bruno/wa-users-service/`
- Paso a paso: [`delivery/qa-manual.md`](delivery/qa-manual.md)
- Curl y ejemplos: [`delivery/api.md`](delivery/api.md)

## Tests

```powershell
cd services/wa-users-service
docker compose up -d --wait mongo
.\mvnw.cmd clean verify
```

- Unitarios (service/audit), WebMvc (controller), integración U1–U4 contra Mongo (`wa_users_it`).
- JaCoCo: umbral **LINE ≥ 80%** en `verify`. Reporte: `target/site/jacoco/index.html`.
- El verify regenera `delivery/openapi/openapi.json` y `openapi.yaml`.

Desde la raíz del repo: `.\mvnw.cmd -q -B verify` (delega al servicio).

## Estructura del proyecto

```
services/wa-users-service/
├── src/main/java/com/bcra/ws/users/
│   ├── api/v1/{controller,dto,mapper}/
│   ├── service/  repository/  entity/  domain/
│   ├── exception/  config/
├── src/main/resources/application*.yml
├── delivery/          # API, OpenAPI, Postman, Bruno, QA
├── Dockerfile
└── docker-compose.yml
```

## Documentación

| Documento | Para qué |
|---|---|
| `delivery/api.md` | Uso de la API con ejemplos curl |
| `delivery/architecture-overview.md` | Capas, decisiones y flujo de una petición |
| `delivery/qa-manual.md` | Cómo probar con Postman/Bruno |
| `../../delivery/devops-local.md` | Docker, CI y despliegue |
| `specs/usuarios/spec.md` | Requisitos de negocio |
