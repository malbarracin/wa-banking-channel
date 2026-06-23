# WA Channel Service

Servicio REST de canal WhatsApp del ecosistema bancario WA (historia H3). Gestiona el vínculo entre un número WhatsApp y un cliente bancario: onboarding con verificación, perfil/preferencias, bloqueo/desvinculación, re-vinculación e historial para soporte.

**Stack:** Java 21 · Spring Boot 3.3 · MongoDB 7 · springdoc OpenAPI

**Contacto:** licius-it · marceloalejandro.albarracin@gmail.com

---

## Prerrequisitos

| Requisito | Versión |
|-----------|---------|
| Java JDK  | 21      |
| Docker    | Para MongoDB local |
| Maven     | Wrapper incluido (`./mvnw`) |

**Integraciones (H3):**

| Servicio | URL default | Rol |
|----------|-------------|-----|
| H1 — wa-users-service | `http://localhost:8080` | Consulta cliente por documento |
| H2 — session (stub)   | `http://localhost:8082` | Emisión/revocación credencial (`stub-enabled: true`) |

---

## Inicio rápido

### 1. Levantar MongoDB

```bash
docker compose up -d mongo
```

Mongo queda disponible en `mongodb://localhost:27017/wa-channel`.

### 2. Ejecutar la aplicación

> **Windows / Git Bash:** preferir **Plan A** (JAR). En Git Bash `./mvnw spring-boot:run` puede fallar por classpath/encoding.

#### Plan A — JAR (recomendado en Windows)

```powershell
./mvnw -DskipTests package
java -jar target/wa-channel-service-0.0.1-SNAPSHOT.jar
```

#### Plan B — spring-boot:run (PowerShell)

```powershell
./mvnw spring-boot:run
```

La API queda en `http://localhost:8081`.

### 3. Verificar salud

```bash
curl -s http://localhost:8081/actuator/health
```

---

## Documentación interactiva

| Recurso | URL |
|---------|-----|
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Swagger UI   | http://localhost:8081/swagger-ui/index.html |

---

## Endpoints (`/api/v1/channel-links`)

| Método | Ruta | Flujo | Descripción |
|--------|------|-------|-------------|
| `GET`    | `/api/v1/channel-links/by-phone/{phone}` | F1 | Consulta anti-duplicado por número |
| `POST`   | `/api/v1/channel-links` | F1 | Iniciar vínculo |
| `POST`   | `/api/v1/channel-links/{id}/accept-terms` | F1 | Aceptar términos |
| `POST`   | `/api/v1/channel-links/{id}/verify` | F1 | Verificar identidad (H1 + OTP MVP) |
| `POST`   | `/api/v1/channel-links/{id}/complete-onboarding` | F1 | Completar onboarding → ACTIVE + H2 |
| `GET`    | `/api/v1/channel-links/{id}` | — | Consulta por ID |
| `GET`    | `/api/v1/channel-links/{id}/profile` | F2 | Perfil enmascarado |
| `GET`    | `/api/v1/channel-links/{id}/preferences` | F2 | Consultar preferencias |
| `PATCH`  | `/api/v1/channel-links/{id}/preferences` | F2 | Actualizar preferencias |
| `POST`   | `/api/v1/channel-links/{id}/block` | F3 | Bloquear y revocar H2 |
| `POST`   | `/api/v1/channel-links/{id}/unlink` | F3 | Desvincular y revocar H2 |
| `POST`   | `/api/v1/channel-links/{id}/relink` | F4 | Re-vinculación con nueva verificación |
| `GET`    | `/api/v1/channel-links/{id}/history` | — | Historial paginado para soporte |

### Estados del vínculo

| Estado | Descripción |
|--------|-------------|
| `NO_LINK` | Registro creado, sin términos aceptados |
| `PENDING_VERIFICATION` | Términos aceptados o identidad verificada, pendiente de activación |
| `ACTIVE` | Vínculo operativo con credencial H2 |
| `BLOCKED` | Bloqueado por el cliente o fraude |
| `UNLINKED` | Desvinculado; requiere relink |
| `VERIFICATION_FAILED` | Intentos OTP agotados (bloqueo temporal) |

**OTP MVP:** código fijo `123456` para pruebas locales.

---

## Ejemplos cURL

Variables de conveniencia:

```bash
BASE=http://localhost:8081
```

### F1 — Onboarding completo

#### 1. Iniciar vínculo

```bash
curl -s -X POST "$BASE/api/v1/channel-links" \
  -H "Content-Type: application/json" \
  -d '{ "phoneNumber": "+541112345678" }'
```

Respuesta `201 Created`:

```json
{
  "id": "665f1a2b3c4d5e6f7a8b9c0d",
  "phoneNumber": "+541112345678",
  "status": "NO_LINK",
  "identityVerified": false,
  "verificationAttempts": 0
}
```

```bash
LINK_ID="<id del response>"
```

#### 2. Aceptar términos

```bash
curl -s -X POST "$BASE/api/v1/channel-links/$LINK_ID/accept-terms" \
  -H "Content-Type: application/json" \
  -d '{ "termsAccepted": true }'
```

#### 3. Verificar identidad (requiere usuario activo en H1)

```bash
curl -s -X POST "$BASE/api/v1/channel-links/$LINK_ID/verify" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "DNI",
    "documentNumber": "12345678",
    "otpCode": "123456"
  }'
```

#### 4. Completar onboarding

```bash
curl -s -X POST "$BASE/api/v1/channel-links/$LINK_ID/complete-onboarding"
```

Respuesta `200 OK` con `"status": "ACTIVE"`.

#### Consulta por teléfono

```bash
curl -s "$BASE/api/v1/channel-links/by-phone/+541112345678"
```

---

### F2 — Perfil y preferencias

```bash
curl -s "$BASE/api/v1/channel-links/$LINK_ID/profile"
```

```bash
curl -s "$BASE/api/v1/channel-links/$LINK_ID/preferences"
```

```bash
curl -s -X PATCH "$BASE/api/v1/channel-links/$LINK_ID/preferences" \
  -H "Content-Type: application/json" \
  -d '{
    "language": "es",
    "notificationsEnabled": true,
    "quietHoursStart": "22:00",
    "quietHoursEnd": "08:00"
  }'
```

---

### F3 — Bloqueo y desvinculación

#### Bloquear vínculo activo

```bash
curl -s -X POST "$BASE/api/v1/channel-links/$LINK_ID/block" \
  -H "Content-Type: application/json" \
  -d '{ "confirmed": true }'
```

#### Desvincular número

```bash
curl -s -X POST "$BASE/api/v1/channel-links/$LINK_ID/unlink" \
  -H "Content-Type: application/json" \
  -d '{ "confirmed": true }'
```

#### Historial de soporte

```bash
curl -s "$BASE/api/v1/channel-links/$LINK_ID/history?page=0&size=20"
```

---

## Contrato de error

Todas las respuestas de error siguen este formato:

```json
{
  "code": "ERROR_CODE",
  "message": "Descripción del error",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

| HTTP | code | Cuándo |
|------|------|--------|
| 400 | `VALIDATION_ERROR` | Bean Validation (campos requeridos, formato E.164) |
| 400 | `BAD_REQUEST` | Estado inválido, usuario no elegible, OTP bloqueado |
| 404 | `NOT_FOUND` | Vínculo inexistente |
| 409 | `BAD_REQUEST` | Número ya vinculado a cliente activo (RN1) |
| 500 | `INTERNAL_ERROR` | Error inesperado del servidor |

### 400 — Validación (`VALIDATION_ERROR`)

```bash
curl -s -X POST "$BASE/api/v1/channel-links" \
  -H "Content-Type: application/json" \
  -d '{}'
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": [
    "phoneNumber: phoneNumber is required"
  ],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

### 409 — Número duplicado activo

Repetir POST F1 con un número ya ACTIVE:

```json
{
  "code": "BAD_REQUEST",
  "message": "Phone number +541112345678 is already linked to an active client",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

### 404 — Vínculo no encontrado (`NOT_FOUND`)

```bash
curl -s "$BASE/api/v1/channel-links/by-phone/+5499999999999"
```

```json
{
  "code": "NOT_FOUND",
  "message": "Channel link not found with id +5499999999999",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

### 400 — Estado inválido (`BAD_REQUEST`)

Intentar aceptar términos en un vínculo ya ACTIVE:

```json
{
  "code": "BAD_REQUEST",
  "message": "Cannot perform accept-terms when link status is ACTIVE",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

### 500 — Error interno (`INTERNAL_ERROR`)

```json
{
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

---

## Postman

Importar la colección:

```
postman/wa-channel-service.postman_collection.json
```

Variables de colección: `baseUrl` = `http://localhost:8081`, `linkId`, `phoneNumber`.

La colección encadena F1 → F2 → F3 y guarda `linkId` automáticamente.

---

## Tests y build

```bash
./mvnw clean verify
```

Reporte JaCoCo: `target/site/jacoco/index.html`

---

## Limitaciones H3

- Sin autenticación JWT
- OTP fijo `123456` (MVP)
- H2 (session) operativo como stub configurable
- Integración H1 requiere wa-users-service en `localhost:8080`
