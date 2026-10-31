# API — wa-credentials-service

Uso de los endpoints HTTP de H2 (Acceso y Sesión) con `curl`. Base: `http://localhost:8082`.
Los mismos ejemplos están en Swagger (`/swagger-ui.html`), Postman y Bruno.

La API es **interna**: la consumen H3 (emisión, renovación y revocación), los servicios de productos
H4–H6 (validación) y el banco (revocación por política o riesgo). El JWT nunca se muestra al cliente final.

Las operaciones privilegiadas exigen autenticación servicio a servicio con
`Authorization: Bearer <secreto>`; no son JWT de clientes:

| Operación | Actor exigido | Secreto |
|---|---|---|
| `POST /emitir` | H3 | `CREDENTIAL_H3_SERVICE_TOKEN` |
| `POST /{numero}/revocacion` | H3 | `CREDENTIAL_H3_SERVICE_TOKEN` |
| `POST /revocacion-masiva` | banco | `CREDENTIAL_BANK_SERVICE_TOKEN` |

Sin `Authorization` o con un valor desconocido la respuesta es **401** `UNAUTHORIZED`; con un secreto
válido pero del actor equivocado es **403** `FORBIDDEN`. Los dos secretos deben existir en H2 y ser
distintos; si un secreto no está configurado, ese actor queda denegado. `validar`, historial y
`renovacion/*` se autentican con la credencial presentada en el body.

Prerrequisito para emisión y validación: H1 (`wa-users-service`) disponible en
`WA_USERS_SERVICE_BASE_URL` (default `http://localhost:8080`) y con el cliente en estado `ACTIVE`.
La revocación, la revocación masiva y el historial no consultan H1.

## Contrato de error único

Todas las respuestas de error usan el mismo cuerpo (`details` nunca es `null`; lista vacía si no aplica):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [
    { "field": "documentoTipo", "message": "no debe estar vacío" }
  ],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `VALIDATION_ERROR` | Bean Validation (`@Valid`) o número fuera de E.164 |
| 400 | `BAD_REQUEST` | JSON ilegible, body ausente o parámetro con tipo inválido |
| 401 | `UNAUTHORIZED` | Credencial inválida, expirada, revocada, con riesgo o cliente no `ACTIVE` en H1; servicio sin autenticar |
| 403 | `FORBIDDEN` | Emisión sin confirmaciones verdaderas o cliente no `ACTIVE`; actor sin permiso (p. ej. H3 en revocación masiva) |
| 404 | `NOT_FOUND` | Cliente inexistente en H1, sin credencial activa, sin historial o sin renovación preparada |
| 409 | `CONFLICT` | Conflicto de persistencia al emitir (`jti` o credencial activa duplicada) |
| 503 | `SERVICE_UNAVAILABLE` | H1 no disponible o respuesta inválida |
| 500 | `INTERNAL_ERROR` | Error inesperado (sin stacktrace ni datos internos) |

Errores de ejemplo:

```json
{
  "code": "FORBIDDEN",
  "message": "La emisión requiere identidad verificada, vínculo activo y cliente activo",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

```json
{
  "code": "UNAUTHORIZED",
  "message": "La credencial no es válida; se requiere verificar la identidad nuevamente",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

```json
{
  "code": "NOT_FOUND",
  "message": "No existe una credencial activa",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "No se pudo validar el cliente",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

## Flujo típico

H3 emite tras verificar identidad y activar el vínculo → H4–H6 validan la credencial antes de operar →
el job interno prepara una entrega de renovación y H3 la obtiene y confirma antes de que venza →
H3 o el banco revocan (individual o masivamente) → la credencial deja de validar de inmediato.

El JWT de los ejemplos es ilustrativo: reemplazarlo por el valor devuelto por `emitir` y usarlo solo
entre servicios.

---

## 1. Emisión — `POST /api/v1/credenciales/emitir`

Solo H3 autenticado (`Authorization: Bearer <CREDENTIAL_H3_SERVICE_TOKEN>`).
Requisitos: `vinculoActivo` e `identidadVerificada` en `true` y cliente `ACTIVE` en H1.
Una emisión nueva invalida la credencial `ACTIVA` anterior del número (motivo `REEMPLAZO`).
Con `CREDENTIAL_VIGENCIA_MINUTOS` (default 1440) se calcula `fechaExpiracion`.

```bash
curl -s -X POST http://localhost:8082/api/v1/credenciales/emitir \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CREDENTIAL_H3_SERVICE_TOKEN" \
  -d '{
    "numeroWhatsapp": "+5491111111111",
    "documentoTipo": "DNI",
    "documentoNumero": "12345678",
    "vinculoActivo": true,
    "identidadVerificada": true
  }'
```

**201 Created**

```json
{
  "credencial": "JWT_DEVUELTO_POR_EMITIR",
  "fechaExpiracion": "2026-10-27T12:00:00Z"
}
```

Errores: **400** datos o JSON inválidos; **401** sin `Authorization` o secreto desconocido;
**403** confirmaciones falsas, cliente no `ACTIVE` o actor distinto de H3; **404** cliente inexistente
en H1; **409** conflicto de persistencia; **503** H1 no disponible.

---

## 2. Validación — `POST /api/v1/credenciales/validar`

Verifica firma HS256, `exp`, `iat`, `jti`, `sub` y `doc`, el estado persistido (activa, sin riesgo, no
revocada, no vencida) y el estado del cliente en H1. Cualquier rechazo de la credencial responde **401**
con el mismo mensaje y registra `VALIDACION_FALLIDA` en auditoría.

El body se valida antes de procesar: `credencial` es obligatoria y no vacía. Un body `{}` o con
`"credencial": ""` responde **400** `VALIDATION_ERROR` (no 401), con `details` indicando el campo.

```bash
curl -s -X POST http://localhost:8082/api/v1/credenciales/validar \
  -H "Content-Type: application/json" \
  -d '{
    "credencial": "JWT_DEVUELTO_POR_EMITIR"
  }'
```

**200 OK**

```json
{
  "vigente": true,
  "numeroWhatsapp": "+5491111111111",
  "documentoTipo": "DNI",
  "documentoNumero": "12345678",
  "fechaExpiracion": "2026-10-27T12:00:00Z"
}
```

Errores: **400** JSON ilegible, body ausente o `credencial` vacía (`VALIDATION_ERROR`);
**401** credencial inválida, expirada, revocada, con riesgo, o H1 caído/inexistente/cliente no `ACTIVE`.

---

## 3. Revocación individual — `POST /api/v1/credenciales/{numero}/revocacion`

Solo H3 autenticado (`Authorization: Bearer <CREDENTIAL_H3_SERVICE_TOKEN>`).
Revoca de inmediato la credencial `ACTIVA` del número. Omitir `solicitadoPor` equivale a `CANAL`.
`SOLICITUD_CLIENTE` e `INTENTOS_AGOTADOS` se interpretan como `BLOQUEO`.
Sin credencial activa responde **404** y deja auditoría de rechazo.

```bash
curl -i -s -X POST 'http://localhost:8082/api/v1/credenciales/+5491111111111/revocacion' \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CREDENTIAL_H3_SERVICE_TOKEN" \
  -d '{
    "motivo": "DESVINCULACION",
    "solicitadoPor": "CANAL"
  }'
```

**204 No Content** — sin cuerpo.

Errores: **400** número fuera de E.164, body inválido o `motivo` ausente; **401** sin autenticar o
secreto desconocido; **403** actor distinto de H3; **404** sin credencial activa; **500** fallo
inesperado al confirmar (sin stacktrace).

---

## 4. Revocación masiva — `POST /api/v1/credenciales/revocacion-masiva`

Solo el banco autenticado (`Authorization: Bearer <CREDENTIAL_BANK_SERVICE_TOKEN>`); el secreto de H3
no habilita esta operación (**403**). Decisión del banco. `TODAS` revoca todas las `ACTIVA` y no admite
`segmento`; `POR_CLIENTE` exige `segmento` (tipo + número de documento) y alcanza todos los números de
ese cliente. Se confirma junto con una auditoría por credencial. Repetir el alcance devuelve `revocadas = 0`.

```bash
curl -s -X POST http://localhost:8082/api/v1/credenciales/revocacion-masiva \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CREDENTIAL_BANK_SERVICE_TOKEN" \
  -d '{
    "criterio": "POR_CLIENTE",
    "segmento": {
      "documentoTipo": "DNI",
      "documentoNumero": "12345678"
    }
  }'
```

**200 OK**

```json
{
  "revocadas": 1
}
```

Errores: **400** criterio o segmento inválidos; **401** sin autenticar o secreto desconocido;
**403** actor distinto del banco; **500** fallo inesperado al confirmar el lote (se revierte todo,
sin stacktrace).

---

## 5. Historial resumido — `GET /api/v1/credenciales/{numero}/historial`

Devuelve la identidad y el estado efectivo de la última emisión y los eventos del número, del más
reciente al más antiguo. Calcula `EXPIRADA` sin modificar la credencial persistida. No incluye JWT ni
`jti`. Si falta la credencial o no hay eventos, responde **404**.

```bash
curl -s http://localhost:8082/api/v1/credenciales/+5491111111111/historial
```

**200 OK**

```json
{
  "numeroWhatsapp": "+5491111111111",
  "documentoTipo": "DNI",
  "documentoNumero": "12345678",
  "estadoActual": "ACTIVA",
  "eventos": [
    {
      "accion": "EMISION",
      "resultado": "EXITOSO",
      "fecha": "2026-10-26T12:00:00Z",
      "motivo": "EMISION"
    }
  ]
}
```

Errores: **400** número fuera de E.164; **404** sin credencial persistida o sin eventos.

---

## 6. Renovación — obtener el JWT sustituto `POST /api/v1/credenciales/renovacion/obtener`

H3 presenta el JWT **vigente** del número y recibe el reemplazo ya preparado por el job interno.
La entrega no se consume: repetir la llamada devuelve el mismo `credencial` y `fechaExpiracion`.
**El JWT anterior sigue activo** hasta que H3 confirma la recepción (paso 7). La respuesta incluye
`Cache-Control: no-store`; no guardar ni registrar el JWT.

```bash
curl -s -X POST http://localhost:8082/api/v1/credenciales/renovacion/obtener \
  -H "Content-Type: application/json" \
  -d '{
    "credencial": "JWT_ACTUAL"
  }'
```

**200 OK**

```json
{
  "credencial": "JWT_NUEVO",
  "fechaExpiracion": "2026-11-01T12:00:00Z"
}
```

Errores: **400** body ausente, JSON ilegible o `credencial` vacía; **401** credencial inválida,
revocada, expirada, con riesgo o cliente no `ACTIVE` en H1; **404** `NOT_FOUND` («Todavía no hay una
renovación preparada»): conservar el JWT actual y reintentar más tarde; **409** conflicto de
persistencia; **500** error interno. Contrato completo: [`shared/contracts/wa-credentials-service/renovacion.md`](../../../shared/contracts/wa-credentials-service/renovacion.md).

---

## 7. Renovación — confirmar la recepción `POST /api/v1/credenciales/renovacion/confirmar`

H3 presenta el **JWT nuevo** después de guardarlo. H2 activa el reemplazo y revoca el anterior con
`REEMPLAZO` en una sola transacción; desde ese momento el JWT anterior deja de validar.
La confirmación es idempotente: repetirla con el mismo JWT vigente devuelve **204** sin volver a
emitir, revocar ni auditar.

```bash
curl -i -s -X POST http://localhost:8082/api/v1/credenciales/renovacion/confirmar \
  -H "Content-Type: application/json" \
  -d '{
    "credencial": "JWT_NUEVO"
  }'
```

**204 No Content** — sin cuerpo.

Errores: **400** body ausente, JSON ilegible o `credencial` vacía; **401** el JWT no corresponde a una
entrega preparada, la anterior ya no está activa o el cliente no está `ACTIVE`; **409** conflicto de
persistencia; **500** error interno.

---

## Salud

```bash
curl -s http://localhost:8082/actuator/health
```

```json
{ "status": "UP" }
```

## Renovación automática

El job interno corre cada `CREDENTIAL_RENOVACION_INTERVALO_MS` (default 60000 ms) sobre credenciales
`ACTIVA` que vencen dentro de `CREDENTIAL_RENOVACION_VENTANA_MINUTOS` (default 30). Sin riesgo prepara
una entrega durable (`entrega_renovacion`) con el `jti` y las fechas del reemplazo, **sin guardar el
JWT**. La credencial anterior sigue activa y validando hasta que H3 confirma el reemplazo (pasos 6 y 7);
el vencimiento natural no se extiende. Con `riesgo = true` no prepara entrega, audita `RENOVACION` /
`RECHAZADO` / `REVERIFICACION_REQUERIDA` y la validación responde **401**. Se deshabilita con
`CREDENTIAL_RENOVACION_HABILITADA=false`.

## OpenAPI exportado

Copia estática regenerada en cada `./mvnw verify`: `delivery/openapi/openapi.json` y `openapi.yaml`.
