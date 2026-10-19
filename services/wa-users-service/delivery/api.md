# API — wa-users-service

Base: `http://localhost:8080` · Prefijo: `/api/v1/users` · Content-Type: `application/json`

Los ejemplos usan el mismo usuario ficticio coherente con Swagger, Postman y Bruno
(`Ana Pérez`, DNI `30111222`, id `550e8400-e29b-41d4-a716-446655440000`).

## Contrato de errores

Toda respuesta de error usa:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [{ "field": "documentType", "message": "no debe estar vacío" }],
  "timestamp": "2026-10-05T14:30:00Z"
}
```

| HTTP | code | Cuándo |
|------|------|--------|
| 400 | `VALIDATION_ERROR` | Datos inválidos o contacto incompleto |
| 400 | `BAD_REQUEST` | Formato, campo no permitido, params ausentes |
| 404 | `NOT_FOUND` | Usuario inexistente |
| 409 | `CONFLICT` | Duplicado, transición inválida, update en baja lógica |
| 500 | `INTERNAL_ERROR` | Error inesperado |

---

## POST `/api/v1/users` — Alta

Crea un usuario en estado `ACTIVE`. Unicidad: tipo + número de documento. Al menos un contacto.

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "DNI",
    "documentNumber": "30111222",
    "displayName": "Ana Pérez",
    "email": "ana@example.com",
    "birthDate": "1990-01-02"
  }'
```

**201 Created**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "documentType": "DNI",
  "documentNumber": "30111222",
  "displayName": "Ana Pérez",
  "email": "ana@example.com",
  "phone": null,
  "birthDate": "1990-01-02",
  "status": "ACTIVE",
  "preferences": {},
  "channelLinkEligible": true
}
```

| Error | Ejemplo |
|-------|---------|
| 400 `VALIDATION_ERROR` | Falta `birthDate` o email/phone |
| 409 `CONFLICT` | Mismo DNI+número ya registrado |

---

## GET `/api/v1/users/{id}` — Consulta por ID

```bash
curl -s http://localhost:8080/api/v1/users/550e8400-e29b-41d4-a716-446655440000
```

**200 OK** — mismos campos autorizados que el alta, más `channelLinkEligible`.

| Error | Ejemplo |
|-------|---------|
| 400 `BAD_REQUEST` | UUID mal formado |
| 404 `NOT_FOUND` | Id inexistente |

---

## GET `/api/v1/users` — Consulta por documento

Ambos query params son obligatorios.

```bash
curl -s "http://localhost:8080/api/v1/users?documentType=DNI&documentNumber=30111222"
```

**200 OK** — mismo cuerpo que la consulta por ID.

| Error | Ejemplo |
|-------|---------|
| 400 | Params ausentes o `documentType` inválido |
| 404 `NOT_FOUND` | Documento no registrado |

---

## PATCH `/api/v1/users/{id}` — Actualización de datos permitidos

Solo `displayName`, `email`, `phone`, `preferences`. Clave ausente = omitir; `null` = eliminar.
Tras el patch debe quedar al menos un contacto. No admite update si el usuario está en baja lógica.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/users/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{"displayName":"Nuevo nombre","preferences":{"language":"es"}}'
```

**200 OK** — `UserResponse` actualizado.

| Error | Ejemplo |
|-------|---------|
| 400 `BAD_REQUEST` | Propiedad no permitida (p. ej. `documentNumber`) |
| 400 `VALIDATION_ERROR` | Queda sin email ni phone |
| 404 `NOT_FOUND` | Id inexistente |
| 409 `CONFLICT` | Usuario en `LOGICALLY_DELETED` |

---

## PATCH `/api/v1/users/{id}/status` — Cambio de estado

Transiciones H1:

| Desde | Hacia |
|-------|-------|
| ACTIVE | SUSPENDED, LOGICALLY_DELETED |
| SUSPENDED | ACTIVE, LOGICALLY_DELETED |
| LOGICALLY_DELETED | *(terminal — ninguna)* |

```bash
curl -s -X PATCH http://localhost:8080/api/v1/users/550e8400-e29b-41d4-a716-446655440000/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SUSPENDED"}'
```

**200 OK** — `status: SUSPENDED`, `channelLinkEligible: false`.

| Error | Ejemplo |
|-------|---------|
| 400 | `status` ausente o inválido |
| 404 `NOT_FOUND` | Id inexistente |
| 409 `CONFLICT` | Transición no permitida |

---

## Salud

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/info
```

## OpenAPI

- UI: http://localhost:8080/swagger-ui.html (desactivada en perfil `prod`)
- JSON vivo: http://localhost:8080/v3/api-docs
- Estático (regenerado en `./mvnw verify`): [`openapi/openapi.json`](openapi/openapi.json)
