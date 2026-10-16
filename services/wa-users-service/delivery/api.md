# API — wa-users-service

Base: `http://localhost:8080`. Todos los endpoints bajo `/api/v1`. Sin autenticación JWT en H1.
Errores uniformes: `code`, `message`, `details[]`, `timestamp` (UTC).

Los ejemplos coinciden con Swagger, Postman y Bruno.

## POST `/api/v1/users` — Alta

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "tipoDocumento": "DNI",
    "numeroDocumento": "30111222",
    "nombreTratamiento": "Ana Pérez"
  }'
```

**201 Created**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "tipoDocumento": "DNI",
  "numeroDocumento": "30111222",
  "nombreTratamiento": "Ana Pérez",
  "email": null,
  "telefonoContacto": null,
  "preferencias": {},
  "status": "ACTIVO",
  "aptoParaVinculoCanal": true,
  "createdAt": "2026-10-15T12:00:00Z",
  "updatedAt": "2026-10-15T12:00:00Z",
  "historialResumido": []
}
```

| Código | `code` | Cuándo |
|--------|--------|--------|
| 400 | `VALIDATION_ERROR` | Campos obligatorios inválidos o ausentes |
| 400 | `BAD_REQUEST` | JSON ilegible |
| 409 | `CONFLICT` | Par documental duplicado |
| 500 | `INTERNAL_ERROR` | Fallo inesperado |

## GET `/api/v1/users/{id}` — Consulta

```bash
curl -s http://localhost:8080/api/v1/users/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**200 OK** — datos autorizados + `historialResumido` (hasta 20 eventos).

| Código | `code` |
|--------|--------|
| 400 | `BAD_REQUEST` (UUID inválido) |
| 404 | `NOT_FOUND` |
| 500 | `INTERNAL_ERROR` |

## PATCH `/api/v1/users/{id}` — Actualización

Campos permitidos: `nombreTratamiento`, `email`, `telefonoContacto`, `preferencias`.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/users/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Content-Type: application/json" \
  -d '{
    "nombreTratamiento": "Ana Pérez",
    "email": "ana.perez@example.com",
    "telefonoContacto": "+5491112345678",
    "preferencias": {"idioma": "es"}
  }'
```

**200 OK** con el usuario actualizado.

| Código | `code` |
|--------|--------|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` (campos ajenos, null explícito) |
| 404 | `NOT_FOUND` |
| 500 | `INTERNAL_ERROR` |

## PATCH `/api/v1/users/{id}/status` — Cambio de estado

Matriz H1: `ACTIVO ↔ SUSPENDIDO`; `ACTIVO|SUSPENDIDO → BAJA_LOGICA`; desde `BAJA_LOGICA` no se reactiva.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/users/a1b2c3d4-e5f6-7890-abcd-ef1234567890/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SUSPENDIDO"}'
```

**200 OK** — `aptoParaVinculoCanal` pasa a `false` si no está `ACTIVO`.

| Código | `code` |
|--------|--------|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` |
| 404 | `NOT_FOUND` |
| 409 | `CONFLICT` (transición no permitida) |
| 500 | `INTERNAL_ERROR` |

## GET `/api/v1/users/{id}/channel-eligibility` — Aptitud (H3)

```bash
curl -s http://localhost:8080/api/v1/users/a1b2c3d4-e5f6-7890-abcd-ef1234567890/channel-eligibility
```

**200 OK**

```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "ACTIVO",
  "aptoParaVinculoCanal": true
}
```

Solo `ACTIVO` → `aptoParaVinculoCanal=true`. Sin datos personales ni historial.

| Código | `code` |
|--------|--------|
| 400 | `BAD_REQUEST` |
| 404 | `NOT_FOUND` |
| 500 | `INTERNAL_ERROR` |

## Contrato de error (ejemplo)

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [
    {"field": "tipoDocumento", "message": "El campo tiene un valor inválido"}
  ],
  "timestamp": "2026-10-15T12:00:00Z"
}
```

OpenAPI estático: `delivery/openapi/openapi.json` y `openapi.yaml` (regenerados por `./mvnw verify`).
