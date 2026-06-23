# WA Auth Service

Servicio REST de acceso y sesión del ecosistema bancario WA (historia **H2**). Emite, valida, renueva y revoca la credencial de sesión del canal tras verificación de identidad (H3).

**Stack:** Java 21 · Spring Boot 3.3 · MongoDB 7 · springdoc OpenAPI

**Puerto:** `8082`

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

Mongo queda disponible en `mongodb://localhost:27017/wa-auth`.

### 2. Ejecutar la aplicación

> **Windows / Git Bash:** preferir **Plan A** (JAR). En Git Bash `./mvnw spring-boot:run` puede fallar por classpath/encoding.

#### Plan A — JAR (recomendado en Windows)

```powershell
./mvnw -DskipTests package
java -jar target/wa-auth-service-0.0.1-SNAPSHOT.jar
```

#### Plan B — spring-boot:run (PowerShell)

```powershell
./mvnw spring-boot:run
```

La API queda en `http://localhost:8082`.

### 3. Verificar salud

```bash
curl -s http://localhost:8082/actuator/health
```

---

## Documentación interactiva

| Recurso | URL |
|---------|-----|
| OpenAPI JSON | http://localhost:8082/v3/api-docs |
| OpenAPI YAML  | http://localhost:8082/v3/api-docs.yaml |
| Swagger UI   | http://localhost:8082/swagger-ui/index.html |
| Especificación estática | [`openapi.yaml`](openapi.yaml) |

---

## Endpoints (`/api/v1/sessions/credentials`)

| Método | Ruta | Descripción | Consumidor |
|--------|------|-------------|------------|
| POST | `/` | A1 — Emitir credencial | H3 |
| POST | `/validate` | Validar credencial + token | H4–H6 |
| DELETE | `/{id}` | Revocar (block/unlink) | H3 |
| POST | `/{id}/revoke` | Revocar con motivo (banco) | Riesgo |
| POST | `/revoke-by-user` | Revocar todas por usuario | Admin |
| POST | `/{id}/renew` | A2 — Renovar TTL | Canal |
| GET | `/{id}` | Consultar estado | Ops |
| GET | `/{id}/audit` | Historial de auditoría | Ops |

---

## Flujo principal: issue → validate → revoke

### A1 — Emitir credencial (POST)

```bash
curl -s -X POST http://localhost:8082/api/v1/sessions/credentials \
  -H "Content-Type: application/json" \
  -d '{
    "channelLinkId": "link-abc123",
    "bankUserId": "user-xyz789",
    "phoneNumber": "+541112345678",
    "identityVerified": true
  }'
```

Respuesta `201 Created`:

```json
{
  "credentialId": "665f1a2b3c4d5e6f7a8b9c0d",
  "token": "sess_xxx_placeholder",
  "expiresAt": "2026-06-24T10:00:00Z"
}
```

> Guardá `credentialId` y `token` — el token **solo se devuelve una vez**.

### Validar credencial (POST)

```bash
curl -s -X POST http://localhost:8082/api/v1/sessions/credentials/validate \
  -H "Content-Type: application/json" \
  -d '{
    "credentialId": "665f1a2b3c4d5e6f7a8b9c0d",
    "token": "sess_xxx_placeholder"
  }'
```

Respuesta `200 OK` (válida):

```json
{
  "valid": true,
  "bankUserId": "user-xyz789",
  "channelLinkId": "link-abc123",
  "expiresAt": "2026-06-24T10:00:00Z"
}
```

### A3 — Revocar credencial (DELETE)

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE \
  http://localhost:8082/api/v1/sessions/credentials/665f1a2b3c4d5e6f7a8b9c0d
```

Respuesta: `204 No Content`.

Tras revocar, una nueva validación retorna `"valid": false` con HTTP 200.

---

## Otros endpoints (curl)

### Renovar credencial

```bash
curl -s -X POST http://localhost:8082/api/v1/sessions/credentials/{credentialId}/renew
```

### Consultar estado

```bash
curl -s http://localhost:8082/api/v1/sessions/credentials/{credentialId}
```

### Revocar con motivo (banco)

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST \
  http://localhost:8082/api/v1/sessions/credentials/{credentialId}/revoke \
  -H "Content-Type: application/json" \
  -d '{"reason": "FRAUD"}'
```

### Revocar por usuario

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST \
  http://localhost:8082/api/v1/sessions/credentials/revoke-by-user \
  -H "Content-Type: application/json" \
  -d '{"bankUserId": "user-xyz789", "reason": "POLICY"}'
```

### Auditoría paginada

```bash
curl -s "http://localhost:8082/api/v1/sessions/credentials/{credentialId}/audit?page=0&size=20"
```

---

## Contrato de error

Todas las respuestas de error siguen el mismo formato:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": ["channelLinkId: channelLinkId is required"],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

| HTTP | code | Cuándo |
|------|------|--------|
| 400  | `VALIDATION_ERROR` | Bean Validation (campos requeridos, formato) |
| 400  | `BAD_REQUEST` | Reglas de negocio (identidad no verificada, credencial revocada, token inválido) |
| 404  | `NOT_FOUND` | Credencial inexistente |
| 500  | `INTERNAL_ERROR` | Error inesperado (sin stacktrace al cliente) |

### Ejemplos de error

**Validación (400):**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": ["bankUserId: bankUserId is required"],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

**Identidad no verificada (400):**

```json
{
  "code": "BAD_REQUEST",
  "message": "Identity verification is required before issuing a session credential",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

**Credencial no encontrada (404):**

```json
{
  "code": "NOT_FOUND",
  "message": "Session credential not found: cred_xxx",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

---

## Postman

Importar [`postman/wa-auth-service.postman_collection.json`](postman/wa-auth-service.postman_collection.json).

Variables de colección:

| Variable | Valor por defecto |
|----------|-------------------|
| `baseUrl` | `http://localhost:8082` |
| `credentialId` | (se setea tras Issue) |
| `token` | (se setea tras Issue) |

Ejecutar la carpeta **H2 Flow — issue → validate → revoke** en orden.

---

## Configuración relevante

| Propiedad | Default | Descripción |
|-----------|---------|-------------|
| `server.port` | `8082` | Puerto HTTP |
| `session.credential.ttl-hours` | `24` | TTL de credencial |
| `integration.users.enabled` | `false` | Validación contra H1 (deshabilitada en local) |

---

## Build y tests

```powershell
./mvnw clean test
./mvnw clean verify
```

Reporte JaCoCo: `target/site/jacoco/index.html`

---

## Docker nativo

```bash
docker build -f Dockerfile.native -t wa-auth-service:native .
```
