# API — wa-cards-service

Uso de los endpoints HTTP de H5 con `curl`. Base: `http://localhost:8086`.
Todos los pedidos exigen `X-Credencial-Sesion: <JWT de H2>`; los ejemplos están también en Swagger,
Postman y Bruno.

## Contrato de error único

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [
    { "field": "tarjetaId", "message": "no debe ser nulo" }
  ],
  "timestamp": "2026-10-30T12:00:00Z"
}
```

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `VALIDATION_ERROR` | Bean Validation (`@Valid`) |
| 400 | `BAD_REQUEST` | JSON ilegible, confirmación ausente o inválida, valores fuera de rango, cursor inválido |
| 401 | `UNAUTHORIZED` | Credencial ausente, expirada o revocada |
| 403 | `FORBIDDEN` | Vínculo inactivo en el canal (H3) |
| 404 | `NOT_FOUND` | Tarjeta inexistente o de otro titular |
| 409 | `CONFLICT` | Estado que no admite la operación, bloqueo irreversible, doble confirmación pendiente |
| 500 | `INTERNAL_ERROR` | Error inesperado (sin stacktrace) |

`details` nunca es `null`.

## Flujo típico

Listado → consumos → bloqueo temporal → desbloqueo temporal → aviso de viaje → bloqueo definitivo.

---

## 1. Listado — `GET /api/v1/tarjetas`

```bash
curl -s http://localhost:8086/api/v1/tarjetas \
  -H "X-Credencial-Sesion: jwt-de-sesion"
```

**200**

```json
[
  {
    "id": "11111111-1111-4111-8111-111111111111",
    "ultimosCuatro": "1234",
    "marca": "VISA",
    "tipo": "CREDITO",
    "estado": "ACTIVA"
  }
]
```

Una lista vacía es válida (el titular no tiene tarjetas). **401** sin credencial válida; **403** con
vínculo inactivo.

---

## 2. Consumos — `GET /api/v1/tarjetas/{tarjetaId}/consumos`

Parámetros opcionales: `dias` (ventana, máximo 90), `limit` (tamaño de página, máximo 10) y `cursor`
(opaco, el de la página anterior). Ambos números deben ser positivos.

```bash
curl -s "http://localhost:8086/api/v1/tarjetas/11111111-1111-4111-8111-111111111111/consumos?dias=30&limit=5" \
  -H "X-Credencial-Sesion: jwt-de-sesion"
```

**200**

```json
{
  "consumos": [
    {
      "fecha": "2026-10-30T12:00:00Z",
      "comercio": "Comercio de prueba",
      "monto": 1250.5,
      "moneda": "ARS",
      "descripcion": "Compra",
      "referencia": "COMPRA-001"
    }
  ],
  "tieneMas": false,
  "cursor": null
}
```

Para la página siguiente, reenviar `cursor` con la misma tarjeta y la misma ventana (`dias`). El orden es
fecha e identificador descendentes y el cursor final es `null`.

**404** si la tarjeta no existe o es de otro titular. **400** con `dias`/`limit` ≤ 0 o cursor inválido.

---

## 3. Bloqueo temporal — `POST /api/v1/tarjetas/{tarjetaId}/bloquear/temporal`

Requiere `tarjetaId` igual al del path y `confirmar` exactamente `CONFIRMAR`. Solo aplica a tarjeta activa.

```bash
curl -s -X POST http://localhost:8086/api/v1/tarjetas/11111111-1111-4111-8111-111111111111/bloquear/temporal \
  -H "Content-Type: application/json" \
  -H "X-Credencial-Sesion: jwt-de-sesion" \
  -d '{
    "tarjetaId": "11111111-1111-4111-8111-111111111111",
    "confirmar": "CONFIRMAR"
  }'
```

**200**

```json
{
  "tarjetaId": "11111111-1111-4111-8111-111111111111",
  "estado": "BLOQUEO_TEMPORAL",
  "mensaje": "Tarjeta bloqueada temporalmente. Para desbloquearla, indicá DESBLOQUEAR."
}
```

**400** `BAD_REQUEST` si falta la confirmación o no coincide con el path (la tarjeta no cambia).
**409** `CONFLICT` si la tarjeta no está activa. **404** si no existe o es de otro titular.

---

## 4. Desbloqueo temporal — `POST /api/v1/tarjetas/{tarjetaId}/desbloquear/temporal`

Solo desde `BLOQUEO_TEMPORAL`. Con `cards.bloqueo.validacion-adicional-requerida=true` (valor por
defecto) exige `validacionAdicional: true`.

```bash
curl -s -X POST http://localhost:8086/api/v1/tarjetas/11111111-1111-4111-8111-111111111111/desbloquear/temporal \
  -H "Content-Type: application/json" \
  -H "X-Credencial-Sesion: jwt-de-sesion" \
  -d '{
    "tarjetaId": "11111111-1111-4111-8111-111111111111",
    "validacionAdicional": true
  }'
```

**200**

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "ultimosCuatro": "1234",
  "marca": "VISA",
  "tipo": "CREDITO",
  "estado": "ACTIVA"
}
```

**409** si la tarjeta está en `BLOQUEO_DEFINITIVO` (no se revierte por chat) o si no está bloqueada
temporalmente. **400** si falta la validación adicional o el `tarjetaId` no coincide.

---

## 5. Bloqueo definitivo — `POST /api/v1/tarjetas/{tarjetaId}/bloquear/definitivo`

Motivo `ROBO` o `EXTRAVIO` y dos confirmaciones exactas `CONFIRMAR`. Path y cuerpo deben coincidir.
El canal debe advertir la irreversibilidad antes de pedir las confirmaciones.

```bash
curl -s -X POST http://localhost:8086/api/v1/tarjetas/11111111-1111-4111-8111-111111111111/bloquear/definitivo \
  -H "Content-Type: application/json" \
  -H "X-Credencial-Sesion: jwt-de-sesion" \
  -d '{
    "tarjetaId": "11111111-1111-4111-8111-111111111111",
    "motivo": "ROBO",
    "confirmarPrimero": "CONFIRMAR",
    "confirmarSegundo": "CONFIRMAR"
  }'
```

**200**

```json
{
  "tarjeta": {
    "id": "11111111-1111-4111-8111-111111111111",
    "ultimosCuatro": "1234",
    "marca": "VISA",
    "tipo": "CREDITO",
    "estado": "BLOQUEO_DEFINITIVO"
  },
  "mensaje": "Bloqueo definitivo aplicado, irreversible por WhatsApp. Solicite la reposición por los canales oficiales del banco."
}
```

Sin las dos confirmaciones responde **409** y no modifica la tarjeta:

```json
{
  "code": "CONFLICT",
  "message": "El bloqueo definitivo es irreversible por WhatsApp. Para continuar, confirme dos veces con CONFIRMAR e indique ROBO o EXTRAVIO.",
  "details": [],
  "timestamp": "2026-10-30T12:00:00Z"
}
```

Repetir el bloqueo definitivo devuelve **409** (`El bloqueo definitivo no se puede revertir por chat…`).
**400** si el motivo no es `ROBO`/`EXTRAVIO` o el `tarjetaId` del cuerpo no coincide con el path.

---

## 6. Aviso de viaje — `POST /api/v1/tarjetas/{tarjetaId}/aviso-viaje`

País obligatorio, región opcional y fechas ISO-8601 con `fechaHasta` igual o posterior a `fechaDesde`.

```bash
curl -s -X POST http://localhost:8086/api/v1/tarjetas/11111111-1111-4111-8111-111111111111/aviso-viaje \
  -H "Content-Type: application/json" \
  -H "X-Credencial-Sesion: jwt-de-sesion" \
  -d '{
    "tarjetaId": "11111111-1111-4111-8111-111111111111",
    "pais": "España",
    "region": "Madrid",
    "fechaDesde": "2026-12-01",
    "fechaHasta": "2026-12-15"
  }'
```

**201 Created**

```json
{
  "tarjetaId": "11111111-1111-4111-8111-111111111111",
  "pais": "España",
  "region": "Madrid",
  "fechaDesde": "2026-12-01",
  "fechaHasta": "2026-12-15",
  "mensajeConfirmacion": "Aviso de viaje registrado y confirmado para España / Madrid, desde 2026-12-01 hasta 2026-12-15. Usá canales oficiales y no compartas tus claves. Siguen aplicando las políticas internacionales del emisor y los límites del banco."
}
```

**400** `VALIDATION_ERROR` si falta `pais` o alguna fecha; `BAD_REQUEST` si las fechas son incoherentes.
**404** si la tarjeta no es del titular. El aviso no cambia estado, límites ni políticas de la tarjeta.

---

## Salud

```bash
curl -s http://localhost:8086/actuator/health
```

## OpenAPI exportado

Copia estática regenerada en cada `./mvnw verify`: `delivery/openapi/openapi.json` y `openapi.yaml`.
