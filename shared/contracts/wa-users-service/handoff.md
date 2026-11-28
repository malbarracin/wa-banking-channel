# wa-users-service — handoff

Registro único de clientes del banco: alta, consulta, actualización de datos permitidos, cambio de estado operativo y auditoría. Otros componentes trabajan contra este contrato, no contra el código del servicio.

## 1. Qué expone y qué no hace

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/users` | Alta de cliente (queda `ACTIVE`) |
| `GET` | `/api/v1/users/{id}` | Consulta por identificador interno (solo campos públicos) |
| `PATCH` | `/api/v1/users/{id}` | Actualización de campos permitidos |
| `PATCH` | `/api/v1/users/{id}/status` | Cambio de estado operativo |
| `GET` | `/api/v1/users/{id}/audit` | Historial de auditoría del cliente |

**No hace:** vincular número de WhatsApp, verificar identidad por canal, emitir/revocar credencial de sesión, consultar u operar productos, cambiar datos legales sensibles, onboarding comercial.

Swagger UI local: `/swagger-ui.html`. OpenAPI en runtime: `/v3/api-docs` y `/v3/api-docs.yaml`. Spec estática: `openapi.yaml` (junto a este handoff).

## 2. Cómo levantarlo

- Puerto: `8080` (configurable con `SERVER_PORT`).
- Desde `services/wa-users-service`: `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`).
- Docker: copiar `.env.example` → `.env` y `docker compose up --build`.
- Requiere PostgreSQL alcanzable con las variables de datasource.
- Health: `GET /actuator/health`.

### Variables de entorno (sin valores)

| Variable | Uso |
|----------|-----|
| `SERVER_PORT` | Puerto HTTP (default 8080) |
| `DATABASE_URL` | JDBC URL de PostgreSQL |
| `DATABASE_USERNAME` | Usuario de la base |
| `DATABASE_PASSWORD` | Contraseña de la base |

No versionar secretos ni credenciales.

### Parámetros configurables `bank.users.*`

| Clave | Default | Significado |
|-------|---------|-------------|
| `bank.users.required-fields` | `uniquenessValue,displayName` | Campos mínimos del alta |
| `bank.users.uniqueness-field` | `uniquenessValue` | Campo de unicidad del banco |
| `bank.users.updatable-fields` | `displayName,contactEmail,contactPhone` | Campos admitidos en `PATCH /users/{id}` |
| `bank.users.public-fields` | `id,externalId,displayName,contactEmail,contactPhone,status,createdAt` | Campos expuestos en la consulta |
| `bank.users.allowed-transitions.ACTIVE` | `SUSPENDED, INACTIVE` | Transiciones desde ACTIVE |
| `bank.users.allowed-transitions.SUSPENDED` | `ACTIVE, INACTIVE` | Transiciones desde SUSPENDED |
| `bank.users.allowed-transitions.INACTIVE` | _(vacío)_ | Baja lógica: sin salida por defecto |

## 3. Autorización y formato de error

Este hito **no autentica**. El actor lo informa el llamador:

- Header opcional `X-Actor` en alta y actualización (si se omite → `system`).
- Campo `actor` obligatorio en el body de `PATCH /api/v1/users/{id}/status`.

Errores en formato único `{code, message, details, timestamp}`:

```json
{
  "code": "NOT_FOUND",
  "message": "No existe el usuario solicitado",
  "details": [],
  "timestamp": "2026-11-27T12:00:00Z"
}
```

Códigos: `VALIDATION_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`.

## 4. Ejemplos de pedido y respuesta

### Alta

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -H "X-Actor: operador-123" \
  -d '{"uniquenessValue":"CLI-1001","displayName":"Ana Pérez","contactEmail":"ana.perez@example.com","contactPhone":"+5491112345678"}'
```

```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "externalId": "USR-00042",
  "displayName": "Ana Pérez",
  "contactEmail": "ana.perez@example.com",
  "contactPhone": "+5491112345678",
  "status": "ACTIVE",
  "createdAt": "2026-11-27T12:00:00Z"
}
```

### Consulta

```bash
curl -s http://localhost:8080/api/v1/users/123e4567-e89b-12d3-a456-426614174000
```

Misma forma de respuesta que el alta (campos según `public-fields`).

### Actualización

```bash
curl -s -X PATCH http://localhost:8080/api/v1/users/123e4567-e89b-12d3-a456-426614174000 \
  -H "Content-Type: application/json" \
  -H "X-Actor: operador-123" \
  -d '{"displayName":"Juan Pérez","contactEmail":"juan.perez@example.com","contactPhone":"+5491198765432"}'
```

### Cambio de estado

```bash
curl -s -X PATCH http://localhost:8080/api/v1/users/123e4567-e89b-12d3-a456-426614174000/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SUSPENDED","actor":"operador-123"}'
```

### Auditoría

```bash
curl -s http://localhost:8080/api/v1/users/123e4567-e89b-12d3-a456-426614174000/audit
```

```json
[
  {
    "id": "a1a2a3a4-1111-4444-8888-123456789abc",
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "action": "ALTA",
    "result": "OK",
    "previousStatus": null,
    "newStatus": "ACTIVE",
    "changedFields": null,
    "actor": "operador-123",
    "occurredAt": "2026-11-27T12:00:00Z"
  }
]
```

Más ejemplos (incluidos errores) en `openapi.yaml` y en `services/wa-users-service/delivery/api.md`.

## 5. Eventos

Este servicio **no publica ni consume** eventos de mensajería. La integración es HTTP síncrona.

## 6. Límites y comportamientos para quien consume

- Unicidad: un alta con el mismo valor del campo de unicidad → `409 CONFLICT` (`DUPLICADO`).
- `PATCH` de datos: cualquier campo fuera de `updatable-fields` → `400 BAD_REQUEST` (no se ignora).
- Estados: `ACTIVE`, `SUSPENDED`, `INACTIVE`. Transiciones según `allowed-transitions`.
- **Solo `ACTIVE` es elegible para un vínculo de canal nuevo.** Un cliente `SUSPENDED` o `INACTIVE` no debe usarse para vincular un canal.
- Auditoría: sin valores personales; solo nombres de campos / estados / resultado.
- Sin paginación en el historial de auditoría (lista completa del cliente).
- Sin autenticación en este hito; no hay idempotency-key HTTP.
- Entrega at-least-once de otros componentes: el consumidor debe tolerar reintentos consultando por id.

## 7. Dónde está el resto

| Recurso | Ubicación |
|---------|-----------|
| README del servicio | `services/wa-users-service/README.md` |
| Guía del componente | `services/wa-users-service/AGENTS.md` |
| API con curl | `services/wa-users-service/delivery/api.md` |
| Arquitectura | `services/wa-users-service/delivery/architecture-overview.md` |
| Manual QA | `services/wa-users-service/delivery/qa-manual.md` |
| Postman | `services/wa-users-service/delivery/postman/` |
| Bruno | `services/wa-users-service/delivery/bruno/wa-users-service/` |
| OpenAPI exportado | `openapi.yaml` (este directorio) y `delivery/openapi/` |
