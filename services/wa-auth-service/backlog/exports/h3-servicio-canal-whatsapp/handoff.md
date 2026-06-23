# Handoff — H3 Servicio Canal WhatsApp

**Componente:** `wa-channel-service`  
**Spec slug:** `h3-servicio-canal-whatsapp`  
**Versión:** `0.0.1-SNAPSHOT`  
**Base package:** `com.wa.banking.channel`

---

## Resumen

Microservicio REST que gestiona el vínculo entre un número WhatsApp y un cliente bancario existente (historia H3). Implementa los flujos F1–F4: onboarding con verificación de identidad, perfil/preferencias de canal, bloqueo/desvinculación, re-vinculación e historial resumido para soporte.

**Stack:** Java 21 · Spring Boot 3.3.5 · MongoDB 7 · springdoc OpenAPI 2.3.0

**Consumidores previstos:**

| Componente | Rol |
|------------|-----|
| **H9** — Canal conversacional | Consulta estado de vínculo, perfil y preferencias antes de enrutar mensajes |
| **H4–H6** — Productos bancarios | Requieren vínculo `ACTIVE` y credencial H2 válida (fuera de alcance REST H3) |
| **Soporte / backoffice** | Historial paginado e interacciones resumidas |

H3 **no** implementa mensajería WhatsApp ni MCP conversacional (alcance H9 Fase 2).

---

## Endpoints (`/api/v1/channel-links`)

| Método | Ruta | Flujo | HTTP | Descripción |
|--------|------|-------|------|-------------|
| `GET` | `/api/v1/channel-links/by-phone/{phone}` | F1 | 200/404 | Consulta anti-duplicado por número E.164 |
| `POST` | `/api/v1/channel-links` | F1 | 201/409 | Iniciar vínculo (`NO_LINK`) |
| `POST` | `/api/v1/channel-links/{id}/accept-terms` | F1 | 200 | Aceptar términos → `PENDING_VERIFICATION` |
| `POST` | `/api/v1/channel-links/{id}/verify` | F1 | 200 | Verificar identidad (H1 + OTP MVP) |
| `POST` | `/api/v1/channel-links/{id}/complete-onboarding` | F1 | 200 | Activar vínculo + emitir credencial H2 |
| `GET` | `/api/v1/channel-links/{id}` | — | 200/404 | Consulta por ID |
| `GET` | `/api/v1/channel-links/{id}/profile` | F2 | 200 | Perfil enmascarado (sin datos legales) |
| `GET` | `/api/v1/channel-links/{id}/preferences` | F2 | 200 | Consultar preferencias de canal |
| `PATCH` | `/api/v1/channel-links/{id}/preferences` | F2 | 200 | Actualizar preferencias |
| `POST` | `/api/v1/channel-links/{id}/block` | F3 | 200 | Bloquear + revocar credencial H2 |
| `POST` | `/api/v1/channel-links/{id}/unlink` | F3 | 200 | Desvincular + revocar credencial H2 |
| `POST` | `/api/v1/channel-links/{id}/relink` | F4 | 200 | Re-vinculación con verificación completa |
| `GET` | `/api/v1/channel-links/{id}/history` | — | 200 | Historial paginado para soporte |

**Total:** 13 operaciones REST.

---

## Estados del vínculo

| Estado | Descripción |
|--------|-------------|
| `NO_LINK` | Registro creado; términos no aceptados |
| `PENDING_VERIFICATION` | Términos aceptados o identidad verificada; pendiente activación |
| `VERIFICATION_FAILED` | Intentos OTP agotados (bloqueo temporal) |
| `ACTIVE` | Vínculo operativo con credencial H2 emitida |
| `BLOCKED` | Bloqueado por cliente o fraude |
| `UNLINKED` | Desvinculado; requiere `relink` |

### Transiciones principales (F1–F4)

```
NO_LINK ──accept-terms──► PENDING_VERIFICATION
PENDING_VERIFICATION ──verify (OK)──► PENDING_VERIFICATION (identityVerified=true)
PENDING_VERIFICATION ──complete-onboarding──► ACTIVE (+ H2 credential)
PENDING_VERIFICATION ──verify (OTP fail x3)──► VERIFICATION_FAILED
ACTIVE ──block──► BLOCKED (+ H2 revoke)
ACTIVE ──unlink──► UNLINKED (+ H2 revoke)
UNLINKED/BLOCKED ──relink──► PENDING_VERIFICATION (nuevo ciclo)
```

### Reglas de negocio (RN1–RN8)

| Regla | Descripción |
|-------|-------------|
| RN1 | Un número solo puede estar `ACTIVE` una vez (409 si duplicado) |
| RN2 | Verificación consulta H1; rechaza si `canLinkChannel=false` |
| RN3 | `complete-onboarding` emite credencial H2 |
| RN4 | `block` / `unlink` revocan credencial H2 |
| RN5 | Perfil enmascara datos sensibles |
| RN6 | Preferencias solo en vínculo `ACTIVE` |
| RN7 | OTP MVP: código fijo `123456`; 3 intentos → bloqueo temporal |
| RN8 | Auditoría e historial registran acciones sin PII completa |

---

## Configuración necesaria

### MongoDB

| Variable / propiedad | Valor local (perfil `local`) |
|----------------------|------------------------------|
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/wa-channel` |

Levantar Mongo:

```bash
docker compose up -d mongo
```

### Puerto y perfil

| Propiedad | Valor |
|-----------|-------|
| `server.port` | `8081` |
| `spring.profiles.active` | `local` (default en `application.yml`) |

### Integraciones upstream

| Servicio | Propiedad | URL default | Rol |
|----------|-----------|-------------|-----|
| **H1** — wa-users-service | `integration.users.base-url` | `http://localhost:8080` | Consulta cliente por documento/ID; valida `canLinkChannel` |
| **H2** — session | `integration.session.base-url` | `http://localhost:8082` | Emisión/revocación credencial |
| **H2 stub** | `integration.session.stub-enabled` | `true` | Genera credenciales locales sin H2 real |

**Prerrequisito F1 verify:** usuario `ACTIVE` en H1 con `canLinkChannel=true`. Crear vía H1:

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"documentType":"DNI","documentNumber":"12345678","displayName":"John Doe","email":"john@example.com","phone":"+541112345678"}'
```

Contrato H1: ver bundle `backlog/exports/h1-servicio-usuarios/openapi.yaml`.

### Actuator

- Health: `GET /actuator/health`
- Endpoints expuestos: `health`, `info`, `metrics`, `prometheus`

---

## Cómo levantar y probar

> **Windows / Git Bash:** usar **Plan A** (JAR). En Git Bash `./mvnw spring-boot:run` puede fallar por classpath/encoding.

### Plan A — JAR (recomendado en Windows)

```powershell
./mvnw -DskipTests package
java -jar target/wa-channel-service-0.0.1-SNAPSHOT.jar
```

### Plan B — spring-boot:run (PowerShell)

```powershell
./mvnw spring-boot:run
```

### Verificación rápida

```bash
curl -s http://localhost:8081/actuator/health
curl -s -X POST http://localhost:8081/api/v1/channel-links \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+541112345678"}'
```

### Documentación interactiva

| Recurso | URL |
|---------|-----|
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| OpenAPI YAML | http://localhost:8081/v3/api-docs.yaml |
| Swagger UI | http://localhost:8081/swagger-ui/index.html |

### Postman

Importar `postman/wa-channel-service.postman_collection.json` (variable `baseUrl` = `http://localhost:8081`). Encadena F1 → F2 → F3.

### Contrato de error

Todas las respuestas de error:

```json
{
  "code": "ERROR_CODE",
  "message": "Descripción",
  "details": [],
  "timestamp": "2026-06-23T10:30:00Z"
}
```

| HTTP | code |
|------|------|
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST` |
| 404 | `NOT_FOUND` |
| 409 | `BAD_REQUEST` (RN1 duplicado activo) |
| 500 | `INTERNAL_ERROR` |

---

## Integración con otros componentes

### H1 — Servicio de Usuarios (upstream obligatorio)

H3 consume vía `UsersClient`:

| Operación H1 | Uso en H3 |
|--------------|-----------|
| `GET /api/v1/users/by-document` | Verificación identidad (F1 verify) |
| `GET /api/v1/users/{id}` | Resolución de perfil enmascarado |
| Flag `canLinkChannel` | Rechazo si `false` (usuario SUSPENDED/SOFT_DELETED) |

Bundle H1: `backlog/exports/h1-servicio-usuarios/`

### H2 — Session (upstream, stub en piloto)

H3 consume vía `SessionClient`:

| Operación H2 | Uso en H3 |
|--------------|-----------|
| `POST /api/v1/sessions/credentials` | Emisión al completar onboarding (RN3) |
| `DELETE /api/v1/sessions/credentials/{id}` | Revocación en block/unlink (RN4) |

Con `stub-enabled: true` (default local) no requiere H2 levantado; genera `stub-cred-{uuid}`.

### H9 — Canal conversacional (downstream)

Integración sugerida sin leer código H3:

1. `GET /by-phone/{phone}` — determinar si el número tiene vínculo y estado.
2. Si `ACTIVE`: usar `GET /{id}/profile` y `GET /{id}/preferences` para personalización.
3. Si `NO_LINK` / `UNLINKED`: guiar flujo onboarding vía F1.
4. Si `BLOCKED` / `VERIFICATION_FAILED`: mensaje de bloqueo; no enrutar operaciones bancarias.

H9 **no** debe llamar endpoints de mutación salvo orquestación explícita de onboarding.

### H4–H6 — Productos bancarios (downstream)

Requieren vínculo `ACTIVE` + credencial H2 válida. H3 expone estado del vínculo; productos deben validar credencial con H2 (fuera de alcance H3).

### Artefactos de contrato en este bundle

- `openapi.yaml` — snapshot exportado de `/v3/api-docs.yaml` (OpenAPI 3.0.1)
- `manifest.yaml` — índice de operaciones `provides`

---

## Limitaciones conocidas (H3)

- **Sin autenticación JWT** — endpoints públicos en red de confianza del piloto.
- **OTP MVP** — código fijo `123456`; no hay envío real por WhatsApp/SMS.
- **H2 stub por defecto** — credenciales simuladas; integración real requiere `stub-enabled: false` y H2 en `:8082`.
- **Sin mensajería WhatsApp** — H3 gestiona solo el vínculo lógico, no envía/recibe mensajes.
- **Sin alta de cliente** — el usuario debe existir previamente en H1.
- **Sin productos bancarios** — H4–H6 no implementados en este componente.

---

## Comandos de verificación (CI / local)

```bash
./mvnw clean verify
```

Reporte JaCoCo: `target/site/jacoco/index.html`

---

## Próximos pasos sugeridos

1. Integrar H9 consumiendo `GET /by-phone/{phone}` y perfil/preferencias.
2. Reemplazar OTP MVP por proveedor real (WhatsApp Business API / SMS).
3. Conectar H2 real (`stub-enabled: false`) cuando el servicio de sesión esté disponible.
4. Agregar autenticación/autorización (JWT o mTLS) en hito posterior.
5. Publicar eventos de dominio (`LinkActivated`, `LinkBlocked`) para sync downstream.
