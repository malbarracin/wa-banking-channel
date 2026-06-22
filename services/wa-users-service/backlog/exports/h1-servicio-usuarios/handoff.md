# Handoff — H1 Servicio de Usuarios

**Componente:** `wa-users-service`  
**Spec Notion:** [36875bb08ca78197a853e85505bf49a5](https://www.notion.so/36875bb08ca78197a853e85505bf49a5)  
**Versión:** `0.0.1-SNAPSHOT`  
**Base package:** `com.wa.banking.users`

---

## Resumen

Microservicio REST que gestiona el ciclo de vida de usuarios bancarios del canal WA (historia H1). Implementa los flujos U1–U4: alta, consulta, actualización parcial de campos mutables y cambio de estado operativo, con historial de auditoría paginado para soporte.

**Stack:** Java 21 · Spring Boot 3.3.5 · MongoDB 7 · springdoc OpenAPI 2.3.0

**Consumidores previstos:** H3 (Canal WhatsApp) consultará `canLinkChannel` y datos de usuario; H1 no expone autenticación ni sincronización downstream.

---

## Endpoints (`/api/v1/users`)

| Método | Ruta | Flujo | HTTP | Descripción |
|--------|------|-------|------|-------------|
| `POST` | `/api/v1/users` | U1 | 201 | Alta de usuario (`ACTIVE`, documento único) |
| `GET` | `/api/v1/users/{id}` | U2 | 200 | Consulta por ID interno |
| `GET` | `/api/v1/users/by-document` | U2 | 200 | Consulta por `documentType` + `documentNumber` |
| `PATCH` | `/api/v1/users/{id}` | U3 | 200 | Actualización parcial (`displayName`, `email`, `phone`, `preferences`) |
| `PATCH` | `/api/v1/users/{id}/status` | U4 | 200 | Cambio de estado operativo |
| `GET` | `/api/v1/users/{id}/audit` | — | 200 | Historial de auditoría paginado |

### Transiciones de estado (U4)

| Estado actual | Transiciones permitidas |
|---------------|-------------------------|
| `ACTIVE` | `SUSPENDED`, `SOFT_DELETED` |
| `SUSPENDED` | `ACTIVE`, `SOFT_DELETED` |
| `SOFT_DELETED` | *(terminal — sin reactivación)* |

### Flag `canLinkChannel`

- `true` solo cuando `status = ACTIVE`
- `false` para `SUSPENDED` y `SOFT_DELETED` (consumo futuro H3)

---

## Configuración necesaria

### MongoDB

| Variable / propiedad | Valor local (perfil `local`) |
|----------------------|------------------------------|
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/wa-users` |

Levantar Mongo:

```bash
docker compose up -d mongo
```

### Puerto y perfil

| Propiedad | Valor |
|-----------|-------|
| `server.port` | `8080` |
| `spring.profiles.active` | `local` (default en `application.yml`) |

Perfil `docker` (compose full stack): URI `mongodb://mongo:27017/wa-users`.

### Actuator

- Health: `GET /actuator/health`
- Endpoints expuestos: `health`, `info`, `metrics`, `prometheus`

---

## Cómo levantar y probar

> **Windows / Git Bash:** usar **Plan A** (JAR). En Git Bash `./mvnw spring-boot:run` puede fallar por classpath/encoding.

### Plan A — JAR (recomendado en Windows)

```powershell
./mvnw -DskipTests package
java -jar target/wa-users-service-0.0.1-SNAPSHOT.jar
```

### Plan B — spring-boot:run (PowerShell)

```powershell
./mvnw spring-boot:run
```

### Verificación rápida

```bash
curl -s http://localhost:8080/actuator/health
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"documentType":"DNI","documentNumber":"12345678","displayName":"John Doe","email":"john@example.com","phone":"+541112345678"}'
```

### Documentación interactiva

| Recurso | URL |
|---------|-----|
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| OpenAPI YAML | http://localhost:8080/v3/api-docs.yaml |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |

### Postman

Importar `postman/wa-users-service.postman_collection.json` (variable `baseUrl` = `http://localhost:8080`). Encadena U1 → U2 → U3 → U4.

### Contrato de error

Todas las respuestas de error:

```json
{
  "code": "ERROR_CODE",
  "message": "Descripción",
  "details": [],
  "timestamp": "2026-06-22T10:30:00Z"
}
```

| HTTP | code |
|------|------|
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST` |
| 404 | `NOT_FOUND` |
| 500 | `INTERNAL_ERROR` |

---

## Integración con otros componentes

### H3 — Canal WhatsApp (downstream)

- Consultar usuario por documento o ID antes de vincular canal.
- Validar `canLinkChannel == true` antes de permitir nuevo vínculo.
- H1 **no** notifica cambios de estado a otros servicios (fuera de alcance).

### Dependencias upstream

Ninguna en H1. Servicio autónomo con MongoDB.

### Artefactos de contrato en este bundle

- `openapi.yaml` — snapshot exportado de `/v3/api-docs.yaml` (OpenAPI 3.0.1)

---

## Limitaciones conocidas (H1)

- **Sin autenticación JWT** — endpoints públicos en red de confianza del piloto.
- **Sin sincronización downstream** — cambios de estado no propagan eventos a H3 u otros servicios.
- **Campos Compliance asumidos** — `documentType` / `documentNumber` modelados como enum + string; validación de formato legal no incluida.
- **SOFT_DELETED terminal** — no hay reactivación vía API.
- **Sin integración WhatsApp, productos bancarios ni H2/H4.**

---

## Comandos de verificación (CI / local)

```bash
./mvnw clean verify
```

Reporte JaCoCo: `target/site/jacoco/index.html`

---

## Próximos pasos sugeridos

1. Integrar H3 consumiendo `GET /api/v1/users/by-document` y flag `canLinkChannel`.
2. Agregar autenticación/autorización (JWT o mTLS) en hito posterior.
3. Publicar eventos de dominio (`UserStatusChanged`) para sync downstream.
4. Endurecer validación de documentos según normativa Compliance.
