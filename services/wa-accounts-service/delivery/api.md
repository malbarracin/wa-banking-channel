# API — wa-accounts-service

Uso de los endpoints HTTP de H4 con `curl`. Base: `http://localhost:8084` (configurable con `PORT`).

Los mismos ejemplos están en Swagger (`/swagger-ui.html`), Postman y Bruno.

## Contrato de error único

Todas las respuestas de error usan:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [
    { "field": "cuentaOrigen", "message": "La cuenta origen es obligatoria" }
  ],
  "timestamp": "2026-10-27T12:00:00Z"
}
```

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `VALIDATION_ERROR` | Bean Validation (`@Valid`) |
| 400 | `BAD_REQUEST` | JSON ilegible, horario fuera de vigencia, solicitud inválida, confirmación distinta a `CONFIRMAR`, sesión sin credencial válida en contexto |
| 401 | `UNAUTHORIZED` | Sesión inválida o credencial ausente/no válida (validación con H2) |
| 404 | `NOT_FOUND` | Cuenta o transferencia inexistente o ajena al titular |
| 409 | `CONFLICT` | Reglas de negocio (saldo insuficiente, límite diario/mensual, producto no habilitado, moneda distinta, destinatario no disponible/no local, doble confirmación en curso con distinto flujo, ejecución repetida) |
| 500 | `INTERNAL_ERROR` | Error inesperado (sin stacktrace) |

`details` nunca es `null` (lista vacía si no aplica).

## Flujo típico

1. Consultar cuentas del titular → obtener `numeroCuenta` (y saldos)
2. Consultar terceros agendados → obtener `terceroId`
3. Consultar movimientos de una cuenta (opcional, con cursor)
4. Preparar transferencia (reserva) → obtener `transferenciaId`, `estado`, `expiraEn`, `requiereDobleConfirmacion`
5. Confirmar y ejecutar (primera o segunda confirmación) → obtener `estado` y, si ejecutada, comprobante con `referencia`

## Autenticación

Todas las operaciones de cuentas y transferencias validan la sesión mediante el header `X-Credencial-Sesion` con el servicio H2 (wa-credentials-service). En Swagger UI y en los ejemplos se debe usar una credencial H2 vigente.

## 1. Listado de cuentas y saldos — `GET /api/v1/cuentas`

```bash
curl -s -X GET "http://localhost:8084/api/v1/cuentas" \
  -H "X-Credencial-Sesion: <CREDENCIAL_H2_VIGENTE>"
```

**200 OK**

```json
[
  {
    "numeroCuenta": "123456789012345678",
    "tipo": "CA",
    "moneda": "ARS",
    "saldoContable": 150000.00,
    "saldoDisponible": 145000.00,
    "sucursal": "001"
  }
]
```

**401** sesión inválida; **500** error inesperado.

---

## 2. Saldo de una cuenta — `GET /api/v1/cuentas/{numero}/saldo`

```bash
curl -s -X GET "http://localhost:8084/api/v1/cuentas/123456789012345678/saldo" \
  -H "X-Credencial-Sesion: <CREDENCIAL_H2_VIGENTE>"
```

**200 OK**

```json
{
  "numeroCuenta": "123456789012345678",
  "saldoContable": 150000.00,
  "saldoDisponible": 145000.00
}
```

**401** sesión inválida; **404** cuenta inexistente o no pertenece al titular.

---

## 3. Movimientos de una cuenta — `GET /api/v1/cuentas/{numero}/movimientos`

Parámetros opcionales: `from`, `to`, `tipo` (`DEBITO|CREDITO`), `cursor`, `limit`.

```bash
curl -s -X GET "http://localhost:8084/api/v1/cuentas/123456789012345678/movimientos?limit=10" \
  -H "X-Credencial-Sesion: <CREDENCIAL_H2_VIGENTE>"
```

**200 OK**

```json
{
  "items": [
    {
      "id": "b7f3a9d2-8c42-4e61-b902-7ab81234cd56",
      "fecha": "2026-10-27T10:15:30Z",
      "tipo": "DEBITO",
      "monto": 5000.00,
      "moneda": "ARS",
      "concepto": "Transferencia",
      "saldoPosterior": 145000.00
    }
  ],
  "nextCursor": "eyJjIjoiMTIzNDU2Nzg5MDEyMzQ1Njc4Ii...",
  "hasMore": true
}
```

**401** sesión inválida; **404** cuenta inexistente o ajena al titular. Para paginar, copiar `nextCursor` de la respuesta anterior y conservar los filtros.

---

## 4. Listado de terceros agendados — `GET /api/v1/terceros`

```bash
curl -s -X GET "http://localhost:8084/api/v1/terceros" \
  -H "X-Credencial-Sesion: <CREDENCIAL_H2_VIGENTE>"
```

**200 OK**

```json
[
  {
    "terceroId": "9e4f62ab-81d7-4c53-b902-12ad873f6542",
    "alias": "JUAN PEREZ",
    "cuentaDestino": "987654321098765432",
    "banco": "BANCO NACION",
    "cbu": "0110000000000000000000",
    "moneda": "ARS",
    "activo": true
  }
]
```

Solo se listan terceros activos del titular según agenda validada con H2. **401** sesión inválida; **404** sin agenda válida/contexto.

---

## 5. Preparar transferencia — `POST /api/v1/transferencias`

Requiere header `X-Credencial-Sesion`. Cuerpo con `cuentaOrigen`, exactamente uno de `cuentaDestino` (propia) o `terceroId` (agenda activa del titular), `monto` positivo con hasta dos decimales, `moneda` y `concepto` opcional.

```bash
curl -s -X POST "http://localhost:8084/api/v1/transferencias" \
  -H "Content-Type: application/json" \
  -H "X-Credencial-Sesion: <CREDENCIAL_H2_VIGENTE>" \
  -d '{
    "cuentaOrigen": "123456789012345678",
    "terceroId": "9e4f62ab-81d7-4c53-b902-12ad873f6542",
    "monto": 5000.00,
    "moneda": "ARS",
    "concepto": "Alquiler"
  }'
```

**201 Created**

```json
{
  "transferenciaId": "a902f7b6-4d81-4e52-9c13-8ab7654321ed",
  "estado": "PREPARADA",
  "cuentaOrigen": "123456789012345678",
  "cuentaDestino": "987654321098765432",
  "monto": 5000.00,
  "moneda": "ARS",
  "requiereDobleConfirmacion": false,
  "expiraEn": "2026-10-27T12:05:00Z",
  "resumen": "Transferencia a JUAN PEREZ"
}
```

Notas:
- No debita ni genera movimientos al preparar. Las cuentas del titular se bloquean con `PESSIMISTIC_WRITE` en orden estable.
- Se validan horario (`TRANSFERENCIAS_HORARIO_*`), límites por titular/producto/moneda (incluidas reservas vigentes), saldo disponible, misma moneda y destinatario.
- Los terceros deben tener cuenta destino local (sin adaptador interbancario). Destinos no locales/ajenos → **409** sin débito.
- Si no hay política compatible en `limites_canal` → **409/400** según regla aplicable.
- **401** sesión inválida; **400** validación; **404** cuenta/origen inexistente o no pertenece al titular; **409** por reglas de negocio.

---

## 6. Confirmar y ejecutar transferencia — `POST /api/v1/transferencias/{id}/confirmar`

Requiere header `X-Credencial-Sesion`. Cuerpo con `"confirmacion":"CONFIRMAR"` (literal exacto).

```bash
curl -s -X POST "http://localhost:8084/api/v1/transferencias/a902f7b6-4d81-4e52-9c13-8ab7654321ed/confirmar" \
  -H "Content-Type: application/json" \
  -H "X-Credencial-Sesion: <CREDENCIAL_H2_VIGENTE>" \
  -d '{"confirmacion":"CONFIRMAR"}'
```

**200 OK — Ejecutada (sin doble confirmación o segunda confirmación)**

```json
{
  "transferenciaId": "a902f7b6-4d81-4e52-9c13-8ab7654321ed",
  "estado": "EJECUTADA",
  "fechaEjecucion": "2026-10-27T12:01:30Z",
  "referencia": "f62ab81d-7c53-4b90-812a-d873f6542901",
  "comprobante": {
    "fecha": "2026-10-27T12:01:30Z",
    "origen": "123456789012345678",
    "destino": "987654321098765432",
    "monto": 5000.00,
    "moneda": "ARS",
    "referencia": "f62ab81d-7c53-4b90-812a-d873f6542901"
  }
}
```

**200 OK — Requiere doble confirmación (primera confirmación)**

```json
{
  "transferenciaId": "a902f7b6-4d81-4e52-9c13-8ab7654321ed",
  "estado": "CONFIRMADA"
}
```

Comportamiento:
- Si requiere doble confirmación: primer `CONFIRMAR` → `200` con `estado: CONFIRMADA` (sin comprobante). Segundo `CONFIRMAR` → `200` con `estado: EJECUTADA` y comprobante.
- Una confirmación distinta a `CONFIRMAR` interrumpe la secuencia: **409** y se vuelve a `PREPARADA` (requiere repetir ambas confirmaciones).
- Se bloquean cuentas del titular y destino local del tercero en orden UUID, luego la transferencia. Ejecución repetida → **409** sin generar otro débito.
- Se revalidan horario, límites (excluyendo la propia reserva), saldo, moneda, vigencia/destinatario. Vencimiento original no se extiende.
- **401** sesión inválida; **404** transferencia ajena/inexistente; **409** por reglas (expirada, distinta confirmación, ya ejecutada/revertida); **400** validación.

## Salud

```bash
curl -s http://localhost:8084/actuator/health
```

## OpenAPI exportado

Copia estática regenerada en cada `./mvnw verify`: `delivery/openapi/openapi.json` y `openapi.yaml`. Disponible en Swagger UI `/swagger-ui.html` y JSON `/v3/api-docs`.

Los ejemplos son ficticios; usar cuentas del titular, un tercero de su agenda, el `transferenciaId` devuelto al preparar y una credencial H2 vigente para ejecutar las solicitudes.
