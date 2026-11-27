# wa-users-service — Registro de clientes del banco

Registro único y confiable de clientes para el canal y el resto del programa: alta con datos mínimos, consulta por identificador, actualización de datos permitidos, estados operativos y auditoría. Sin cliente en el banco no hay vínculo ni operación por ningún canal.

**Fuera de alcance:** vincular WhatsApp, verificar identidad por canal, credenciales de sesión, productos, datos legales sensibles u onboarding comercial.

## Funcionalidades

- Alta de cliente (`POST /api/v1/users`) — queda `ACTIVE`, con unicidad configurable
- Consulta por id (`GET /api/v1/users/{id}`) — solo campos de la política de privacidad
- Actualización de datos permitidos (`PATCH /api/v1/users/{id}`)
- Cambio de estado (`PATCH /api/v1/users/{id}/status`) — `ACTIVE` / `SUSPENDED` / `INACTIVE`
- Historial de auditoría (`GET /api/v1/users/{id}/audit`)

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5.x |
| Base de datos | PostgreSQL 16 (H2 en tests) |
| Migraciones | Flyway |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi (Swagger UI) |

## Requisitos

- JDK 21
- PostgreSQL alcanzable (o Docker Compose)
- Variables `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (ver `.env.example` para Compose)

## Cómo correrlo

### Local

```bash
# desde services/wa-users-service
.\mvnw.cmd spring-boot:run
# Linux/macOS: ./mvnw spring-boot:run
```

Por defecto escucha en `http://localhost:8080` (`SERVER_PORT`). Datasource por defecto: `jdbc:postgresql://localhost:5432/wa_users`.

### Con Docker

```bash
copy .env.example .env   # ajustar APP_PORT / DB_PORT si 8080 o 5432 están ocupados
docker compose up --build
```

La app queda en `http://localhost:${APP_PORT}` (default 8080). Health: `/actuator/health`.

## Configuración

| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP | `8080` |
| `DATABASE_URL` | JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/wa_users` |
| `DATABASE_USERNAME` | Usuario BD | `postgres` |
| `DATABASE_PASSWORD` | Contraseña BD | _(vacío)_ |
| `bank.users.required-fields` | Campos mínimos del alta | `uniquenessValue,displayName` |
| `bank.users.uniqueness-field` | Campo de unicidad | `uniquenessValue` |
| `bank.users.updatable-fields` | Campos admitidos en PATCH | `displayName,contactEmail,contactPhone` |
| `bank.users.public-fields` | Campos expuestos en GET | `id,externalId,displayName,contactEmail,contactPhone,status,createdAt` |
| `bank.users.allowed-transitions.*` | Transiciones de estado | ver `application.yml` |

El banco cambia políticas editando `bank.users.*` en `application.yml` (o overlay por perfil/entorno), sin redeploy de lógica.

## API

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs · copia estática en `delivery/openapi/openapi.json` y `.yaml`
- Contrato publicado: `../../shared/contracts/wa-users-service/`

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| POST | `/api/v1/users` | Alta (ACTIVE) | 201, 400, 409, 500 |
| GET | `/api/v1/users/{id}` | Consulta pública | 200, 400, 404, 500 |
| PATCH | `/api/v1/users/{id}` | Actualizar campos permitidos | 200, 400, 404, 500 |
| PATCH | `/api/v1/users/{id}/status` | Cambiar estado | 200, 400, 404, 409, 500 |
| GET | `/api/v1/users/{id}/audit` | Historial de auditoría | 200, 400, 500 |

### Errores

Formato único:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": [{ "field": "contactEmail", "message": "must be a well-formed email address" }],
  "timestamp": "2026-11-27T12:00:00Z"
}
```

Códigos: `VALIDATION_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`.

## Probar la API

- Postman: importar `delivery/postman/wa-users-service.postman_collection.json` y `delivery/postman/wa-users-service.postman_environment.json`
- Bruno: abrir `delivery/bruno/wa-users-service/`
- Paso a paso: `delivery/qa-manual.md`

## Tests

```bash
.\mvnw.cmd -q -B clean verify
```

Incluye unitarios, integración (H2), JaCoCo ≥ 80% y exportación OpenAPI. El log no debe ir a `target/` si se usa `clean` (borra el directorio).

## Estructura del proyecto

```
src/main/java/com/bcra/users/ws/
├── config/       # políticas bank.users, OpenAPI
├── exception/    # contrato de error global
├── user/         # controller → service → repository (+ dto, entity, mapper)
└── audit/        # historial por cliente
```

## Documentación

| Documento | Para qué |
|---|---|
| `delivery/api.md` | Uso de la API con ejemplos curl |
| `delivery/architecture-overview.md` | Capas, decisiones y flujo |
| `delivery/qa-manual.md` | Cómo probar con Postman/Bruno |
| `AGENTS.md` | Guía para quien toque el código |
| `../../shared/contracts/wa-users-service/handoff.md` | Contrato para integradores |
| `specs/` | Requisitos de negocio (fuente) |
