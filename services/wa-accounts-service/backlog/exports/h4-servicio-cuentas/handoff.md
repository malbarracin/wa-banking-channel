# Handoff — H4 Servicio de Cuentas

**Componente:** `wa-accounts-service`  
**Session slug:** `h4-servicio-cuentas`  
**Versión:** `0.0.1-SNAPSHOT`  
**Base package:** `com.wa.banking.accounts`

---

## Resumen

Microservicio REST que expone consulta de cuentas y saldos del titular autenticado (historia H4, piloto **F1**). Cada request valida la credencial de sesión emitida por H2 antes de consultar MongoDB. Consumidor principal: **H3** (canal WhatsApp), que reenvía `Authorization` + `X-Credential-Id` obtenidos en `complete-onboarding`.

**Stack:** Java 21 · Spring Boot 3.3.5 · MongoDB 7 · springdoc OpenAPI 2.3.0

**Alcance F1 (este release):**

| Operación | Descripción |
|-----------|-------------|
| Listado de cuentas | Cuentas activas del `bankUserId` retornado por H2 |
| Consulta de saldo | Saldo disponible y contable de una cuenta del titular |

**Fuera de alcance F1:** movimientos (F2), transferencias propias/terceros (F3/F4).

---

## Endpoints (`/api/v1/accounts`)

| Método | Ruta | Flujo | HTTP | Descripción | Consumidor |
|--------|------|-------|------|-------------|------------|
| `GET` | `/api/v1/accounts` | F1 | 200/401 | Listar cuentas del titular autenticado | H3 |
| `GET` | `/api/v1/accounts/{accountId}/balance` | F1 | 200/401/404 | Saldo disponible y contable | H3 |

**Total:** 2 operaciones REST.

### Flujo de autenticación

```
Request H3 → H4
  Headers: Authorization: Bearer {token}
           X-Credential-Id: {credentialId}

H4 → H2 validate
  POST {integration.session.base-url}/api/v1/sessions/credentials/validate
  Body: { "credentialId": "...", "token": "..." }
  → { "valid": true, "bankUserId": "...", "channelLinkId": "...", "expiresAt": "..." }

Si valid=false o headers ausentes → 401 UNAUTHORIZED (sin consultar cuentas)
Si valid=true → filtro inyecta bankUserId → service consulta solo cuentas del titular
```

### Aislamiento de datos

- El `bankUserId` proviene **exclusivamente** de la respuesta H2 validada.
- Cuentas de otro titular responden **404** (mismo mensaje que cuenta inexistente).
- Ningún endpoint F1 es accesible sin pasar el filtro de sesión.

---

## Headers requeridos (H3 → H4)

| Header | Obligatorio | Ejemplo | Descripción |
|--------|-------------|---------|-------------|
| `Authorization` | Sí | `Bearer sess_xxx` | Token opaco emitido por H2 al crear credencial |
| `X-Credential-Id` | Sí | `665f1a2b3c4d5e6f7a8b9c0d` | ID de credencial de sesión |
| `Accept` | Recomendado | `application/json` | Formato de respuesta |

H3 debe persistir `credentialId` y `token` tras `complete-onboarding` (RN3 H3) y reenviarlos en cada llamada a productos bancarios.

---

## Integración con H2 (dependencia runtime)

| Aspecto | Valor |
|---------|-------|
| Servicio | `wa-auth-service` — puerto **8082** |
| Operación | `POST /api/v1/sessions/credentials/validate` |
| Bundle H2 | `backlog/exports/h2-servicio-acceso-sesion/` |

### Configuración en H4

```yaml
integration:
  session:
    base-url: http://localhost:8082
    stub-enabled: true   # local: omite HTTP a H2
```

| Propiedad | Local (default) | Integración real |
|-----------|-----------------|------------------|
| `integration.session.base-url` | `http://localhost:8082` | URL del servicio H2 |
| `integration.session.stub-enabled` | `true` | `false` — llama validate real |

Con **stub habilitado**, cualquier par `credentialId`/`token` es aceptado y responde `bankUserId=user-demo-001` (útil para desarrollo sin H2).

Variables de entorno Docker: `INTEGRATION_SESSION_BASE_URL`, `INTEGRATION_SESSION_STUB_ENABLED`.

---

## Integración con H3 (consumidor)

H3 invoca H4 cuando el usuario consulta cuentas o saldo en el canal.

### Configuración sugerida en H3 (`wa-channel-service`)

```yaml
integration:
  accounts:
    base-url: http://localhost:8083
    stub-enabled: false
```

| Momento H3 | Operación H4 | Headers |
|------------|--------------|---------|
| Usuario pide "mis cuentas" | `GET /api/v1/accounts` | `Authorization`, `X-Credential-Id` del vínculo ACTIVE |
| Usuario pide saldo de cuenta | `GET /api/v1/accounts/{id}/balance` | Idem + `accountId` del listado previo |

Bundle H3: `backlog/exports/h3-servicio-canal-whatsapp/`

> **Mensaje 401 al cliente:** H4 responde `"Por seguridad, verificá tu identidad para ver tus cuentas."` — H3 debe mapearlo a copy conversacional sin exponer detalles técnicos.

---

## Configuración necesaria

### MongoDB

| Variable / propiedad | Valor local (perfil `local`) |
|----------------------|------------------------------|
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/wa-accounts` |

Levantar Mongo:

```bash
docker compose up -d mongo
```

### Puerto y perfil

| Propiedad | Valor |
|-----------|-------|
| `server.port` | **8083** |
| `spring.profiles.active` | `local` (default en `application.yml`) |

Perfil `docker`: URI `mongodb://mongo:27017/wa-accounts`.

### Datos demo (perfil local)

Al primer arranque con Mongo vacío, se cargan 2 cuentas para `bankUserId=user-demo-001` desde `src/main/resources/data/accounts-seed.json`.

Credenciales demo (stub H2):

| Variable | Valor |
|----------|-------|
| `credentialId` | `cred-demo-001` |
| `token` | `demo-token` |

### Actuator

- Health: `GET /actuator/health`
- Endpoints expuestos: `health`, `info`, `metrics`, `prometheus`

---

## Cómo levantar y probar

> **Windows / Git Bash:** usar **Plan A** (JAR). En Git Bash `./mvnw spring-boot:run` puede fallar por classpath/encoding.

### Plan A — JAR (recomendado en Windows)

```powershell
docker compose up -d mongo
./mvnw -DskipTests package
java -jar target/wa-accounts-service-0.0.1-SNAPSHOT.jar
```

### Plan B — spring-boot:run (PowerShell)

```powershell
docker compose up -d mongo
./mvnw spring-boot:run
```

### Verificación rápida

```bash
curl -s http://localhost:8083/actuator/health
```

### 200 — Listar cuentas

```bash
curl -s -X GET "http://localhost:8083/api/v1/accounts" \
  -H "Authorization: Bearer demo-token" \
  -H "X-Credential-Id: cred-demo-001" \
  -H "Accept: application/json"
```

Respuesta esperada (`200`):

```json
{
  "accounts": [
    {
      "id": "674a1b2c3d4e5f6789012345",
      "alias": "Cuenta Sueldo",
      "type": "CHECKING",
      "currency": "ARS",
      "availableBalance": 125000.50,
      "ledgerBalance": 125000.50,
      "status": "ACTIVE"
    }
  ]
}
```

### 200 — Consultar saldo

Reemplazá `{accountId}` por un `id` del listado anterior.

```bash
curl -s -X GET "http://localhost:8083/api/v1/accounts/{accountId}/balance" \
  -H "Authorization: Bearer demo-token" \
  -H "X-Credential-Id: cred-demo-001" \
  -H "Accept: application/json"
```

### 401 — sin credencial

```bash
curl -s -X GET "http://localhost:8083/api/v1/accounts" \
  -H "Accept: application/json"
```

Respuesta esperada:

```json
{
  "code": "UNAUTHORIZED",
  "message": "Por seguridad, verificá tu identidad para ver tus cuentas.",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

### 404 — cuenta inexistente o ajena

```bash
curl -s -X GET "http://localhost:8083/api/v1/accounts/missing-account-id/balance" \
  -H "Authorization: Bearer demo-token" \
  -H "X-Credential-Id: cred-demo-001" \
  -H "Accept: application/json"
```

Respuesta esperada:

```json
{
  "code": "NOT_FOUND",
  "message": "Account not found: missing-account-id",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

### Documentación interactiva

| Recurso | URL |
|---------|-----|
| OpenAPI JSON | http://localhost:8083/v3/api-docs |
| OpenAPI YAML | http://localhost:8083/v3/api-docs.yaml |
| Swagger UI | http://localhost:8083/swagger-ui/index.html |
| Spec estática | `openapi.yaml` (este bundle) |

### Postman

Importar `postman/wa-accounts-service.json` (`baseUrl` = `http://localhost:8083`).

### Contrato de error

```json
{
  "code": "UNAUTHORIZED | NOT_FOUND | VALIDATION_ERROR | BAD_REQUEST | INTERNAL_ERROR",
  "message": "string",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

| HTTP | code | Cuándo |
|------|------|--------|
| 401 | `UNAUTHORIZED` | Sin headers, token inválido o H2 `valid=false` |
| 404 | `NOT_FOUND` | Cuenta inexistente o de otro titular |
| 400 | `VALIDATION_ERROR` | Bean Validation (futuros POST/PUT) |
| 400 | `BAD_REQUEST` | Argumento ilegal |
| 500 | `INTERNAL_ERROR` | Error inesperado (sin stacktrace) |

---

## Limitaciones conocidas

- **F1 acotado** — solo listado y saldo; movimientos y transferencias diferidos.
- **Stub H2 default en local** — desactivar para integración end-to-end con H2 real.
- **Sin rate limiting** — piloto en red de confianza.
- **Seed demo fijo** — `user-demo-001`; producción requiere core bancario real.
- **Sin caché de validate** — cada request llama a H2 (salvo stub).

---

## Comandos de verificación (CI / local)

```powershell
./mvnw clean verify
```

Reporte JaCoCo: `target/site/jacoco/index.html`

**Estado pipeline:** `./mvnw clean verify` OK · 26 tests · coverage LINE ~86% (umbral 70%)

---

## Próximos pasos sugeridos

1. **H3:** configurar cliente HTTP a `:8083` y reenviar headers de sesión en flujos de consulta de cuentas.
2. **Integración E2E:** levantar H2 (`:8082`) + H4 con `integration.session.stub-enabled=false`; emitir credencial real vía H3 onboarding.
3. **F2 — Movimientos:** `GET /api/v1/accounts/{id}/movements` paginado, ventana 90 días.
4. **F3/F4 — Transferencias:** endpoints de transferencia propia y a terceros agendados, con límites y doble confirmación.
