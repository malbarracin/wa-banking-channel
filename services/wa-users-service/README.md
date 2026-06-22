# WA Users Service

Servicio REST de usuarios bancarios del canal WA (historia H1). Expone operaciones de alta, consulta, actualización parcial y cambio de estado sobre usuarios cliente, con historial de auditoría para soporte.

**Stack:** Java 21 · Spring Boot 3.3 · MongoDB 7 · springdoc OpenAPI

**Contacto:** licius-it · marceloalejandro.albarracin@gmail.com

---

## Prerrequisitos

| Requisito | Versión |
|-----------|---------|
| Java JDK  | 21      |
| Docker    | Para MongoDB local |
| Maven     | Wrapper incluido (`./mvnw`) |

---

## Inicio rápido

### 1. Levantar MongoDB

```bash
docker compose up -d mongo
```

Mongo queda disponible en `mongodb://localhost:27017/wa-users`.

### 2. Ejecutar la aplicación

> **Windows / Git Bash:** preferir **Plan A** (jar). En Git Bash `./mvnw spring-boot:run` puede fallar por classpath/encoding.

#### Plan A — JAR (recomendado en Windows)

```powershell
./mvnw -DskipTests package
java -jar target/wa-users-service-0.0.1-SNAPSHOT.jar
```

#### Plan B — spring-boot:run (PowerShell)

```powershell
./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`.

### 3. Verificar salud

```bash
curl -s http://localhost:8080/actuator/health
```

---

## Documentación interactiva

| Recurso | URL |
|---------|-----|
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Swagger UI   | http://localhost:8080/swagger-ui/index.html |

---

## Endpoints (`/api/v1/users`)

| Método | Ruta | Flujo | Descripción |
|--------|------|-------|-------------|
| `POST`   | `/api/v1/users` | U1 | Alta de usuario (estado ACTIVE) |
| `GET`    | `/api/v1/users/{id}` | U2 | Consulta por ID interno |
| `GET`    | `/api/v1/users/by-document` | U2 | Consulta por tipo + número de documento |
| `PATCH`  | `/api/v1/users/{id}` | U3 | Actualización parcial (campos mutables) |
| `PATCH`  | `/api/v1/users/{id}/status` | U4 | Cambio de estado operativo |
| `GET`    | `/api/v1/users/{id}/audit` | — | Historial de auditoría paginado |

### Estados y transiciones (U4)

| Estado actual | Transiciones permitidas |
|---------------|-------------------------|
| `ACTIVE`      | `SUSPENDED`, `SOFT_DELETED` |
| `SUSPENDED`   | `ACTIVE`, `SOFT_DELETED` |
| `SOFT_DELETED`| *(terminal — sin reactivación)* |

---

## Ejemplos cURL

Variables de conveniencia:

```bash
BASE=http://localhost:8080
```

### U1 — Alta de usuario activo

```bash
curl -s -X POST "$BASE/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "DNI",
    "documentNumber": "12345678",
    "displayName": "John Doe",
    "email": "john@example.com",
    "phone": "+541112345678",
    "preferences": { "lang": "es" }
  }'
```

Respuesta `201 Created`:

```json
{
  "id": "665f1a2b3c4d5e6f7a8b9c0d",
  "documentType": "DNI",
  "documentNumber": "12345678",
  "displayName": "John Doe",
  "email": "john@example.com",
  "phone": "+541112345678",
  "preferences": { "lang": "es" },
  "status": "ACTIVE",
  "canLinkChannel": true,
  "createdAt": "2026-06-22T10:30:00Z",
  "updatedAt": "2026-06-22T10:30:00Z"
}
```

Guardar el `id` para los siguientes pasos:

```bash
USER_ID="<id del response>"
```

### U2 — Consulta por ID

```bash
curl -s "$BASE/api/v1/users/$USER_ID"
```

### U2 — Consulta por documento

```bash
curl -s "$BASE/api/v1/users/by-document?documentType=DNI&documentNumber=12345678"
```

### U3 — Actualización de campos permitidos

```bash
curl -s -X PATCH "$BASE/api/v1/users/$USER_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Jane Doe",
    "email": "jane@example.com",
    "phone": "+541198765432"
  }'
```

### U4 — Suspender usuario

```bash
curl -s -X PATCH "$BASE/api/v1/users/$USER_ID/status" \
  -H "Content-Type: application/json" \
  -d '{ "status": "SUSPENDED" }'
```

### U4 — Baja lógica

```bash
curl -s -X PATCH "$BASE/api/v1/users/$USER_ID/status" \
  -H "Content-Type: application/json" \
  -d '{ "status": "SOFT_DELETED" }'
```

### Auditoría

```bash
curl -s "$BASE/api/v1/users/$USER_ID/audit?page=0&size=20"
```

---

## Contrato de error

Todas las respuestas de error siguen este formato:

```json
{
  "code": "ERROR_CODE",
  "message": "Descripción del error",
  "details": [],
  "timestamp": "2026-06-22T10:30:00Z"
}
```

| HTTP | code | Cuándo |
|------|------|--------|
| 400 | `VALIDATION_ERROR` | Bean Validation (campos requeridos, email inválido) |
| 400 | `BAD_REQUEST` | Documento duplicado, transición de estado inválida |
| 404 | `NOT_FOUND` | Usuario inexistente |
| 500 | `INTERNAL_ERROR` | Error inesperado del servidor |

### 400 — Validación (`VALIDATION_ERROR`)

```bash
curl -s -X POST "$BASE/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{ "documentType": "DNI" }'
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": [
    "documentNumber: documentNumber is required",
    "displayName: displayName is required",
    "email: email is required",
    "phone: phone is required"
  ],
  "timestamp": "2026-06-22T10:30:00Z"
}
```

### 400 — Documento duplicado (`BAD_REQUEST`)

Repetir el POST U1 con el mismo `documentType` + `documentNumber`:

```json
{
  "code": "BAD_REQUEST",
  "message": "User already exists with document DNI 12345678",
  "details": [],
  "timestamp": "2026-06-22T10:30:00Z"
}
```

### 404 — Usuario no encontrado (`NOT_FOUND`)

```bash
curl -s "$BASE/api/v1/users/000000000000000000000000"
```

```json
{
  "code": "NOT_FOUND",
  "message": "User not found with id 000000000000000000000000",
  "details": [],
  "timestamp": "2026-06-22T10:30:00Z"
}
```

### 400 — Transición de estado inválida (`BAD_REQUEST`)

Intentar reactivar un usuario en `SOFT_DELETED`:

```json
{
  "code": "BAD_REQUEST",
  "message": "Transition from SOFT_DELETED to ACTIVE is not allowed",
  "details": [],
  "timestamp": "2026-06-22T10:30:00Z"
}
```

### 500 — Error interno (`INTERNAL_ERROR`)

```json
{
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "details": [],
  "timestamp": "2026-06-22T10:30:00Z"
}
```

---

## Postman

Importar la colección:

```
postman/wa-users-service.postman_collection.json
```

Variable de entorno incluida: `baseUrl` = `http://localhost:8080`.

La colección encadena el flujo completo U1 → U2 → U3 → U4 y guarda `userId` automáticamente.

---

## Tests y build

```bash
./mvnw clean verify
```

Reporte JaCoCo: `target/site/jacoco/index.html`

---

## Limitaciones H1

- Sin autenticación JWT
- Sin sincronización downstream
- Campos de compliance asumidos (documentType, documentNumber)
