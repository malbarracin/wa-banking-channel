# wa-users-service

Servicio de Usuarios (H1) del canal bancario WhatsApp. Stack: Java 21, Spring Boot 3.3, MongoDB 7, MVC.

## Requisitos

- Java 21+
- Docker / Docker Compose (Mongo local y Testcontainers)
- Maven Wrapper (`./mvnw`) — no requiere Maven global

## Arranque rápido

### 1. Base de datos

```bash
docker compose up -d mongo
```

### 2. Compilar y ejecutar — Plan A (recomendado en Windows / Git Bash)

```bash
./mvnw -DskipTests package
java -jar target/wa-users-service-0.0.1-SNAPSHOT.jar
```

### 3. Alternativa — Plan B (PowerShell recomendado)

```powershell
./mvnw spring-boot:run
```

**Advertencia (Windows / Git Bash / MINGW):** preferir **Plan A** (`package` + `java -jar`). No usar `spring-boot:run` como path principal en Git Bash si el usuario o los paths tienen caracteres no ASCII (fallas de classpath).

## Tests y coverage

```bash
# 1) Mongo real para IT (estrategia equivalente a Testcontainers)
docker compose up -d mongo

# 2) Verify + JaCoCo
./mvnw clean verify
```

- Suites: unitarios (service/audit), WebMvc (controller), integración U1–U4 contra Mongo 7.
- Persistencia IT: `docker compose up -d mongo` → `mongodb://localhost:27017/wa_users_it`
  (equivalente documentado a Testcontainers; el cliente Testcontainers falla con el npipe
  de Docker Desktop en este entorno Windows).
- JaCoCo: umbral **LINE ≥ 80%** en fase `verify` (`jacocoArgLine` + surefire; sin `argLine` global).
- Reporte HTML: `target/site/jacoco/index.html`.
- Si Mongo no está arriba, la IT se omite (`Assumptions`) y el resto de suites sigue corriendo.

## Documentación de API

| Recurso | Path / URL |
|---------|------------|
| OpenAPI (fuente) | `docs/openapi.yaml` |
| OpenAPI (contrato monorepo) | `shared/contracts/wa-users-service/openapi.yaml` |
| Postman | `docs/postman/wa-users-service.postman_collection.json` |
| Runtime OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |

Variables Postman: `baseUrl`, `userId`, `documentType`, `documentNumber`, `actorId`.
La request de alta OK guarda `userId` automáticamente si responde 201.

## Endpoints

| Método | Path | Descripción |
|--------|------|-------------|
| POST | `/api/v1/users` | Alta (U1) → 201 ACTIVE |
| GET | `/api/v1/users/{id}` | Consulta por id (U2) |
| GET | `/api/v1/users?documentType=&documentNumber=` | Consulta por documento (U2) |
| PATCH | `/api/v1/users/{id}` | Update campos permitidos (U3) |
| PATCH | `/api/v1/users/{id}/status` | Cambio de estado (U4) |
| GET | `/api/v1/users/{id}/audit` | Historial de auditoría |
| GET | `/actuator/health` | Health |
| GET | `/v3/api-docs` | OpenAPI |
| GET | `/swagger-ui.html` | Swagger UI |

Header opcional de auditoría: `X-Actor-Id`.

### Curls de ejemplo (U1–U4)

```bash
# U1 — Alta OK
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: ops" \
  -d '{
    "documentType": "DNI",
    "documentNumber": "30111222",
    "firstName": "Maria",
    "lastName": "Lopez",
    "email": "maria.lopez@example.com",
    "phone": "+5491112345678",
    "preferredName": "Mari",
    "preferredLanguage": "es"
  }'

# U2 — Consulta por id
curl -s http://localhost:8080/api/v1/users/{id}

# U2 — Consulta por documento
curl -s "http://localhost:8080/api/v1/users?documentType=DNI&documentNumber=30111222"

# U3 — Update campos permitidos
curl -s -X PATCH http://localhost:8080/api/v1/users/{id} \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: ops" \
  -d '{"preferredName":"Mari","email":"mari@example.com"}'

# U4 — Suspender
curl -s -X PATCH http://localhost:8080/api/v1/users/{id}/status \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: ops" \
  -d '{"status":"SUSPENDED"}'

# Auditoría
curl -s http://localhost:8080/api/v1/users/{id}/audit
```

## Error contract

```json
{
  "code": "NOT_FOUND | VALIDATION_ERROR | BAD_REQUEST | INTERNAL_ERROR",
  "message": "string",
  "details": [],
  "timestamp": "ISO-8601"
}
```

| HTTP | code | Caso |
|------|------|------|
| 400 | `VALIDATION_ERROR` | Bean Validation (email inválido, campos requeridos) |
| 400 | `BAD_REQUEST` | Transición de estado inválida / update sin campos |
| 404 | `NOT_FOUND` | Usuario inexistente |
| 409 | `BAD_REQUEST` | Documento duplicado (`details[].field=documentNumber`) |
| 500 | `INTERNAL_ERROR` | Error no controlado (sin stacktrace) |

### Ejemplos JSON

**400 VALIDATION_ERROR**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    { "field": "email", "message": "email must be valid" }
  ],
  "timestamp": "2026-10-19T12:00:00Z"
}
```

**404 NOT_FOUND**

```json
{
  "code": "NOT_FOUND",
  "message": "User not found: 661f0a1b2c3d4e5f67890123",
  "details": [],
  "timestamp": "2026-10-19T12:00:00Z"
}
```

**409 documento duplicado** (`code` = `BAD_REQUEST`)

```json
{
  "code": "BAD_REQUEST",
  "message": "A user with the same document already exists",
  "details": [
    {
      "field": "documentNumber",
      "message": "Document already registered: DNI/30111222"
    }
  ],
  "timestamp": "2026-10-19T12:00:00Z"
}
```

**500 INTERNAL_ERROR**

```json
{
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "details": [],
  "timestamp": "2026-10-19T12:00:00Z"
}
```

## Perfiles

- `local` (default): Mongo en `mongodb://localhost:27017/wa_users`
- `docker`: URI vía `SPRING_DATA_MONGODB_URI`

## Docker (app + Mongo)

```bash
docker compose up -d --build
docker compose logs -f app
docker compose down
```
