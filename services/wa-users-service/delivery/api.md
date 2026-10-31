# API — wa-users-service

Uso de los endpoints HTTP de H1 con `curl`. Base: `http://localhost:8080`.
Los mismos ejemplos están en Swagger (`/swagger-ui.html`), Postman y Bruno.

## Contrato de error único

Todas las respuestas de error usan:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [
    { "field": "nombre", "message": "El nombre es obligatorio" }
  ],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `VALIDATION_ERROR` | Bean Validation (`@Valid`) |
| 400 | `BAD_REQUEST` | JSON ilegible, transición de estado inválida, campo no editable |
| 403 | `FORBIDDEN` | Consulta completa sin principal autenticado o sin rol `USERS_FULL_READ` |
| 404 | `NOT_FOUND` | Usuario inexistente (mensaje genérico, sin PII) |
| 409 | `CONFLICT` | Documento duplicado en el alta |
| 500 | `INTERNAL_ERROR` | Error inesperado (sin stacktrace) |

`details` nunca es `null` (lista vacía si no aplica).

## Flujo típico

Alta → consulta → actualización → consulta de estado → cambio de estado.

---

## 1. Alta — `POST /api/v1/users`

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "documento": { "tipo": "DNI", "numero": "30111222" },
    "nombre": "Ana",
    "apellido": "Pérez",
    "fechaNacimiento": "1990-05-15",
    "email": "ana@example.test",
    "telefono": "5551234"
  }'
```

**201 Created** — incluye el header `Location` con la ruta canónica del recurso:

```text
Location: /api/v1/users/DNI/30111222
```

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "documento": { "tipo": "DNI", "numero": "30111222" },
  "nombre": "Ana",
  "apellido": "Pérez",
  "fechaNacimiento": "1990-05-15",
  "email": "ana@example.test",
  "telefono": "5551234",
  "nombreTratamiento": null,
  "preferencias": null,
  "estado": "ACTIVE"
}
```

Errores: **400** datos incompletos; **409** documento ya registrado. Un alta rechazada por
Bean Validation queda auditada como `ALTA` / `RECHAZADO` cuando el body trae un documento
identificable (tipo y número no vacíos y de hasta 255 caracteres).

---

## 2. Consulta — `GET /api/v1/users/{tipo}/{numero}`

Requiere un principal autenticado con el rol de servidor `USERS_FULL_READ`. La aplicación no
configura el mecanismo de autenticación: por defecto la consulta completa queda denegada con **403**,
incluso para documentos inexistentes. Headers como `X-Profile`, `X-User` o `X-Role` no otorgan acceso.

```bash
curl -s http://localhost:8080/api/v1/users/DNI/30111222
```

**403** — respuesta por defecto sin autenticación configurada:

```json
{
  "code": "FORBIDDEN",
  "message": "Acceso no autorizado a la consulta completa",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

**200** — con principal y rol `USERS_FULL_READ`: mismo cuerpo que el alta (UserResponse completo
de este hito, provisional hasta la proyección por perfil de Compliance).

**404** — con principal y rol, si el usuario no existe:

```json
{
  "code": "NOT_FOUND",
  "message": "Usuario no encontrado",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

---

## 3. Estado — `GET /api/v1/users/{tipo}/{numero}/status`

```bash
curl -s http://localhost:8080/api/v1/users/DNI/30111222/status
```

**200**

```json
{
  "documento": { "tipo": "DNI", "numero": "30111222" },
  "estado": "ACTIVE"
}
```

Pensado para H3: expone estados no habilitantes (`SUSPENDED`, `LOGICAL_DELETE`) sin aplicar la restricción de vínculo aquí.

---

## 4. Actualización — `PATCH /api/v1/users/{tipo}/{numero}`

Solo `email`, `telefono`, `nombreTratamiento`, `preferencias`. Ausentes o `null` conservan el valor.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/users/DNI/30111222 \
  -H "Content-Type: application/json" \
  -d '{
    "email": "nuevo@example.test",
    "telefono": "555999",
    "nombreTratamiento": "Anita",
    "preferencias": "canal:whatsapp"
  }'
```

**200** — UserResponse con los campos actualizados.

**400** si se envía una propiedad fuera del contrato (ej. `fechaNacimiento`, `documento`, `estado`):

```json
{
  "code": "BAD_REQUEST",
  "message": "El campo no es editable por este servicio",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

---

## 5. Cambio de estado — `PATCH /api/v1/users/{tipo}/{numero}/status`

Transiciones H1: `ACTIVE`→`SUSPENDED`|`LOGICAL_DELETE`; `SUSPENDED`→`ACTIVE`|`LOGICAL_DELETE`.
Desde `LOGICAL_DELETE` no hay reactivación.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/users/DNI/30111222/status \
  -H "Content-Type: application/json" \
  -d '{"estado":"SUSPENDED"}'
```

**200**

```json
{
  "documento": { "tipo": "DNI", "numero": "30111222" },
  "estado": "SUSPENDED"
}
```

**400** transición inválida (`code`: `BAD_REQUEST`). **404** si no existe el usuario.

## Salud

```bash
curl -s http://localhost:8080/actuator/health
```

## OpenAPI exportado

Copia estática regenerada en cada `./mvnw verify`: `delivery/openapi/openapi.json` y `openapi.yaml`.
