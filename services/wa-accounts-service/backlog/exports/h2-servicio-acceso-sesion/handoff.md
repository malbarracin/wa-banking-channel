# Handoff — H2 Servicio de Acceso y Sesión

**Componente:** `wa-auth-service`  
**Session slug:** `h2-servicio-acceso-sesion`  
**Versión:** `0.0.1-SNAPSHOT`  
**Base package:** `com.wa.banking.auth`

---

## Resumen

Microservicio REST que gestiona credenciales opacas de sesión del canal WhatsApp (historia H2). Implementa emisión condicionada a verificación de identidad (A1), renovación de TTL (A2), revocación inmediata (A3) y validación para productos bancarios (H4–H6), con auditoría paginada sin exponer tokens.

**Stack:** Java 21 · Spring Boot 3.3.5 · MongoDB 7 · springdoc OpenAPI 2.3.0

**Consumidores previstos:**

| Componente | Rol |
|------------|-----|
| **H3** — Canal WhatsApp | Emite credencial al completar onboarding; revoca en block/unlink |
| **H4–H6** — Productos bancarios | Validan credencial + token antes de operaciones |
| **Banco / riesgo** | Revocación con motivo, revocación masiva por usuario |
| **Ops / soporte** | Consulta de estado y auditoría paginada |

H2 **no** implementa flujo conversacional, alta de usuario (H1) ni operaciones de productos.

---

## Endpoints (`/api/v1/sessions/credentials`)

| Método | Ruta | Flujo | HTTP | Descripción | Consumidor |
|--------|------|-------|------|-------------|------------|
| `POST` | `/` | A1 | 201 | Emitir credencial tras verificación H3 | H3 |
| `POST` | `/validate` | — | 200 | Validar credencial + token | H4–H6 |
| `DELETE` | `/{id}` | A3 | 204 | Revocar inmediata (block/unlink) | H3 |
| `POST` | `/{id}/renew` | A2 | 200 | Renovar TTL si ACTIVE | Canal |
| `POST` | `/{id}/revoke` | A3 | 204 | Revocar con motivo explícito | Banco/riesgo |
| `POST` | `/revoke-by-user` | A3 | 204 | Revocar todas las ACTIVE de un usuario | Admin |
| `GET` | `/{id}` | — | 200 | Estado metadata (sin token) | Ops |
| `GET` | `/{id}/audit` | — | 200 | Historial paginado de auditoría | Ops |

**Total:** 8 operaciones REST.

### Flujos A1–A3

```
A1 — Emisión (H3 complete-onboarding)
  POST /api/v1/sessions/credentials
  Body: channelLinkId, bankUserId, phoneNumber, identityVerified=true (RN1)
  → 201 { credentialId, token, expiresAt }
  → Revoca credencial ACTIVE previa del mismo channelLinkId (RN2)

A2 — Renovación
  POST /api/v1/sessions/credentials/{id}/renew
  → 200 { credentialId, expiresAt, renewalCount }
  → Solo si status=ACTIVE y no expirada

A3 — Revocación
  DELETE /api/v1/sessions/credentials/{id}     → H3 block/unlink (idempotente)
  POST   /api/v1/sessions/credentials/{id}/revoke → motivo banco
  POST   /api/v1/sessions/credentials/revoke-by-user → masiva por bankUserId
  → 204; validate posterior retorna valid=false (RN4/RN6)
```

### Reglas de negocio (RN1–RN6)

| Regla | Descripción |
|-------|-------------|
| RN1 | Rechazar emisión si `identityVerified != true` → 400 `BAD_REQUEST` |
| RN2 | Al emitir, reemplazar credencial `ACTIVE` previa del mismo `channelLinkId` |
| RN3 | TTL configurable (`session.credential.ttl-hours`, default 24h); renovación sin re-onboarding |
| RN4 | Revocación marca `REVOKED` inmediato |
| RN5 | Token opaco solo en respuesta de emisión; no reenviar al cliente en chat (responsabilidad H3) |
| RN6 | Credencial `REVOKED`/`EXPIRED` → `valid=false` en validate (HTTP 200) |

---

## Configuración necesaria

### MongoDB

| Variable / propiedad | Valor local (perfil `local`) |
|----------------------|------------------------------|
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/wa-auth` |

Levantar Mongo:

```bash
docker compose up -d mongo
```

### Puerto y perfil

| Propiedad | Valor |
|-----------|-------|
| `server.port` | `8082` |
| `spring.profiles.active` | `local` (default en `application.yml`) |

Perfil `docker`: URI `mongodb://mongo:27017/wa-auth`.

### Credencial de sesión

| Propiedad | Default | Descripción |
|-----------|---------|-------------|
| `session.credential.ttl-hours` | `24` | TTL de credencial en horas |
| `session.credential.pepper` | `change-me-in-production` | Pepper para hash de token (`SESSION_PEPPER` env) |

### Actuator

- Health: `GET /actuator/health`
- Endpoints expuestos: `health`, `info`, `metrics`, `prometheus`

---

## Integración con H3 (Canal WhatsApp)

H3 consume H2 vía `SessionClient`. Para integración real (sin stub):

### Configuración en H3 (`wa-channel-service`)

```yaml
integration:
  session:
    base-url: http://localhost:8082
    stub-enabled: false
```

| Propiedad | Valor integración | Efecto |
|-----------|-------------------|--------|
| `integration.session.base-url` | `http://localhost:8082` | Apunta al servicio H2 real |
| `integration.session.stub-enabled` | `false` | Desactiva credenciales simuladas (`stub-cred-*`) |

### Contrato H3 → H2

| Momento H3 | Operación H2 | Payload clave |
|------------|--------------|-----------------|
| `complete-onboarding` (RN3) | `POST /api/v1/sessions/credentials` | **`identityVerified: true`** (RN1 obligatorio), `channelLinkId`, `bankUserId`, `phoneNumber` |
| `block` / `unlink` (RN4) | `DELETE /api/v1/sessions/credentials/{credentialId}` | Path param `credentialId` almacenado en vínculo |

> **RN1:** H3 **debe** enviar `"identityVerified": true` en el POST de emisión. Si es `false` o la identidad no fue verificada, H2 responde `400 BAD_REQUEST` con mensaje *Identity verification is required before issuing a session credential*.

Ejemplo de emisión desde H3:

```json
{
  "channelLinkId": "link-abc123",
  "bankUserId": "user-xyz789",
  "phoneNumber": "+541112345678",
  "identityVerified": true
}
```

Alias aceptado: `linkId` como alternativa a `channelLinkId` (compatibilidad H3).

Bundle H3: `backlog/exports/h3-servicio-canal-whatsapp/`

---

## Integración con H4–H6 (Productos)

Los productos validan sesión antes de operaciones sensibles:

```bash
curl -s -X POST http://localhost:8082/api/v1/sessions/credentials/validate \
  -H "Content-Type: application/json" \
  -d '{
    "credentialId": "665f1a2b3c4d5e6f7a8b9c0d",
    "token": "sess_xxx_placeholder"
  }'
```

Respuesta válida (`200`):

```json
{
  "valid": true,
  "bankUserId": "user-xyz789",
  "channelLinkId": "link-abc123",
  "expiresAt": "2026-06-24T10:00:00Z"
}
```

Credencial inválida/revocada/expirada: HTTP `200` con `"valid": false` (sin error HTTP).

---

## Integración upstream H1 (opcional)

| Propiedad | Default | Descripción |
|-----------|---------|-------------|
| `integration.users.enabled` | `false` | Validación contra H1 deshabilitada en local |

Cuando `enabled=true`, H2 consulta `GET /api/v1/users/{id}` en H1 (`http://localhost:8080`) antes de emitir.

Bundle H1: `backlog/exports/h1-servicio-usuarios/`

---

## Cómo levantar y probar

> **Windows / Git Bash:** usar **Plan A** (JAR). En Git Bash `./mvnw spring-boot:run` puede fallar por classpath/encoding.

### Plan A — JAR (recomendado en Windows)

```powershell
docker compose up -d mongo
./mvnw -DskipTests package
java -jar target/wa-auth-service-0.0.1-SNAPSHOT.jar
```

### Plan B — spring-boot:run (PowerShell)

```powershell
docker compose up -d mongo
./mvnw spring-boot:run
```

### Verificación rápida

```bash
curl -s http://localhost:8082/actuator/health
```

### Flujo completo issue → validate → revoke

**A1 — Emitir:**

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

**Validar (H4–H6):**

```bash
curl -s -X POST http://localhost:8082/api/v1/sessions/credentials/validate \
  -H "Content-Type: application/json" \
  -d '{"credentialId":"<ID>","token":"<TOKEN>"}'
```

**A3 — Revocar:**

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE \
  http://localhost:8082/api/v1/sessions/credentials/<ID>
```

### Documentación interactiva

| Recurso | URL |
|---------|-----|
| OpenAPI JSON | http://localhost:8082/v3/api-docs |
| OpenAPI YAML | http://localhost:8082/v3/api-docs.yaml |
| Swagger UI | http://localhost:8082/swagger-ui/index.html |
| Spec estática | `openapi.yaml` (este bundle) |

### Postman

Importar `postman/wa-auth-service.postman_collection.json` (`baseUrl` = `http://localhost:8082`). Ejecutar carpeta **H2 Flow — issue → validate → revoke**.

### Contrato de error

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
| 400 | `VALIDATION_ERROR` | Bean Validation |
| 400 | `BAD_REQUEST` | RN1 identidad no verificada, credencial ya revocada, no renovable |
| 404 | `NOT_FOUND` | Credencial inexistente |
| 500 | `INTERNAL_ERROR` | Error inesperado (sin stacktrace) |

---

## Limitaciones conocidas

- **Sin JWT/OAuth2** — endpoints en red de confianza del piloto.
- **Token opaco** — no es JWT firmado; hash BCrypt en persistencia.
- **H1 opcional deshabilitado** — en local no valida existencia de usuario en H1.
- **Pepper default inseguro** — cambiar `SESSION_PEPPER` en producción.
- **Sin mensajería WhatsApp** — H2 solo gestiona credenciales, no conversación.

---

## Comandos de verificación (CI / local)

```powershell
./mvnw clean verify
```

Reporte JaCoCo: `target/site/jacoco/index.html`

**Estado pipeline:** `./mvnw clean verify` OK · 40 tests · coverage LINE ~85% (umbral 70%)

---

## Próximos pasos sugeridos

1. H3: configurar `integration.session.stub-enabled=false` y levantar H2 en `:8082`.
2. H4–H6: integrar `POST /validate` en middleware de autorización de productos.
3. Habilitar `integration.users.enabled=true` cuando H1 esté disponible en entorno integrado.
4. Rotar `SESSION_PEPPER` y configurar secretos en despliegue productivo.
5. Agregar autenticación mTLS/JWT entre servicios en hito posterior.
