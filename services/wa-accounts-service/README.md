# WA Accounts Service (H4 — Piloto F1)

Servicio de consulta de cuentas y saldos del ecosistema bancario WA. Expone la API REST v1 consumida por H3 (canal WhatsApp) tras validar la credencial de sesión emitida por H2.

**Alcance F1:**

- `GET /api/v1/accounts` — listar cuentas del titular autenticado
- `GET /api/v1/accounts/{accountId}/balance` — consultar saldo disponible y contable

## Prerrequisitos

| Requisito | Versión / notas |
|-----------|-----------------|
| Java | 21 |
| Maven Wrapper | `./mvnw` (incluido en el repo) |
| Docker | Para MongoDB local vía `docker-compose` |
| MongoDB | 7.x en `localhost:27017` (perfil `local`) |

## Variables de configuración

| Variable / propiedad | Default (local) | Descripción |
|----------------------|-----------------|-------------|
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/wa-accounts` | Conexión MongoDB |
| `integration.session.base-url` | `http://localhost:8082` | URL base del servicio H2 (auth) |
| `integration.session.stub-enabled` | `true` | Si `true`, acepta cualquier credencial sin llamar a H2 |
| `server.port` | `8083` | Puerto HTTP del servicio |

En Docker Compose se pueden sobreescribir con `INTEGRATION_SESSION_BASE_URL` e `INTEGRATION_SESSION_STUB_ENABLED`.

## Runbook de ejecución (runtime-safe)

> **Advertencia Git Bash / MINGW:** en Windows, evitá `./mvnw spring-boot:run` desde Git Bash si el path del proyecto contiene caracteres no ASCII o si el classpath falla. Preferí **PowerShell** o el **Plan A (JAR)**.

### 1. Levantar MongoDB

```powershell
docker compose up -d mongo
```

### 2. Plan A — JAR (recomendado en Windows)

```powershell
./mvnw -DskipTests package
java -jar target/wa-accounts-service-0.0.1-SNAPSHOT.jar
```

### 3. Plan B — Spring Boot run (PowerShell)

```powershell
./mvnw spring-boot:run
```

### 4. Verificar salud y documentación

| Recurso | URL |
|---------|-----|
| Health | http://localhost:8083/actuator/health |
| OpenAPI JSON | http://localhost:8083/v3/api-docs |
| Swagger UI | http://localhost:8083/swagger-ui/index.html |

Con perfil `local` y stub H2 habilitado, se cargan cuentas demo para `user-demo-001` al primer arranque.

## Integración H2 — validación de credencial

H4 valida cada request contra H2 antes de procesar la operación.

| Aspecto | Detalle |
|---------|---------|
| Endpoint H2 | `POST {integration.session.base-url}/api/v1/sessions/credentials/validate` |
| Body enviado | `{ "credentialId": "<id>", "token": "<token>" }` |
| Respuesta esperada | `{ "valid": true, "bankUserId": "...", "channelLinkId": "...", "expiresAt": "..." }` |
| Modo stub (`stub-enabled: true`) | Omite la llamada HTTP; responde `valid=true` con `bankUserId=user-demo-001` |

### Headers requeridos en cada request a H4

| Header | Obligatorio | Ejemplo | Descripción |
|--------|-------------|---------|-------------|
| `Authorization` | Sí | `Bearer demo-token` | Token opaco emitido por H2 al crear la credencial |
| `X-Credential-Id` | Sí | `cred-demo-001` | Identificador de la credencial de sesión |
| `Accept` | Recomendado | `application/json` | Formato de respuesta |

Si falta algún header o H2 responde `valid=false`, H4 responde **401** con el mensaje orientado al cliente (spec §8).

## Contrato de error

Todas las respuestas de error siguen el mismo envelope:

```json
{
  "code": "ERROR_CODE",
  "message": "Descripción legible",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

### Mapping HTTP → código → mensaje (H3)

| HTTP | code | Cuándo ocurre | message típico (cliente) |
|------|------|---------------|--------------------------|
| 401 | `UNAUTHORIZED` | Sin `Authorization`, sin `X-Credential-Id`, token inválido o credencial expirada | Por seguridad, verificá tu identidad para ver tus cuentas. |
| 404 | `NOT_FOUND` | Cuenta inexistente o de otro titular | Account not found: {accountId} |
| 400 | `VALIDATION_ERROR` | Bean Validation (futuros POST/PUT) | Validation failed |
| 400 | `BAD_REQUEST` | Argumento ilegal | Mensaje específico de la excepción |
| 500 | `INTERNAL_ERROR` | Error no controlado | An unexpected error occurred |

## Ejemplos curl

Reemplazá `{accountId}` por el `id` obtenido del listado de cuentas.

### Listar cuentas (con autenticación)

```bash
curl -s -X GET "http://localhost:8083/api/v1/accounts" \
  -H "Authorization: Bearer demo-token" \
  -H "X-Credential-Id: cred-demo-001" \
  -H "Accept: application/json"
```

### Consultar saldo

```bash
curl -s -X GET "http://localhost:8083/api/v1/accounts/{accountId}/balance" \
  -H "Authorization: Bearer demo-token" \
  -H "X-Credential-Id: cred-demo-001" \
  -H "Accept: application/json"
```

### 401 — sin token (ejemplo error)

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

## Postman

Importá la colección desde `postman/wa-accounts-service.json`.

Variables de colección:

| Variable | Valor demo |
|----------|------------|
| `baseUrl` | `http://localhost:8083` |
| `credentialId` | `cred-demo-001` |
| `token` | `demo-token` |
| `accountId` | (completar tras listar cuentas) |

## Tests y coverage

```powershell
./mvnw clean verify
```

Reporte JaCoCo: `target/site/jacoco/index.html`

## Contacto

- Equipo: licius-it
- Email: marceloalejandro.albarracin@gmail.com
