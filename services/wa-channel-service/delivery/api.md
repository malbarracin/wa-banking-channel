# API — wa-channel-service

Uso de los 8 endpoints HTTP de H3 con `curl`. Base: `http://localhost:8081`.
Los mismos ejemplos están en Swagger (`/swagger-ui.html`), Postman y Bruno.

## Contrato de error único

Todas las respuestas de error usan:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [
    { "field": "numeroWhatsapp", "message": "El número de WhatsApp es obligatorio" }
  ],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `VALIDATION_ERROR` | Bean Validation (`@Valid`) o propiedad fuera del contrato |
| 400 | `BAD_REQUEST` | JSON ilegible o número fuera de E.164 |
| 401 | `UNAUTHORIZED` | Falta el contexto de credencial |
| 404 | `NOT_FOUND` | Sin términos, sin vínculo o cliente inexistente en H1 |
| 409 | `CONFLICT` | Cliente no activo en H1, número ya vinculado o estado incompatible |
| 423 | `LOCKED` | Intentos agotados en la ventana vigente |
| 500 | `INTERNAL_ERROR` | Error inesperado o fallo de H1 (sin stacktrace) |

`details` nunca es `null` (lista vacía si no aplica).

## Contexto de credencial

H3 valida con H2 la vigencia del token y su pertenencia al número solicitado antes de autorizar perfil,
desvinculación y bloqueo a pedido del cliente. Si H2 no puede validarlo, rechaza la operación con 401.
El antiguo header `X-Canal-Credencial-Valida` no autoriza operaciones.

| Header | Endpoint | Valor |
|---|---|---|
| `X-Canal-Credencial-Token` | Perfil, desvinculación, bloqueo con `SOLICITUD_CLIENTE` | Token H2 vigente |
| `X-Credencial-Documento-Tipo` | Preferencias | Tipo de documento del titular |
| `X-Credencial-Documento-Numero` | Preferencias | Número de documento del titular |

Sin ese contexto la respuesta es **401** `UNAUTHORIZED`.

## Preparación local

La verificación y el perfil consultan H1 en `http://localhost:8080`. Antes de probar, H1 debe estar
arriba y existir un cliente `ACTIVE` con el documento usado:

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

Si el documento ya existe, H1 responde **409**; usar ese mismo cliente o cambiar el número de documento.

> Con la estrategia de verificación por defecto (placeholder de Compliance) ningún intento activa el
> vínculo: `POST /verificaciones` devuelve **200** con `exitosa=false` y la cuenta de intentos baja.
> Para ver un **200** de perfil, preferencias, bloqueo o desvinculación hace falta un vínculo `ACTIVO`;
> ver la sección correspondiente en [`qa-manual.md`](qa-manual.md).

## Flujo típico

`términos → verificación → estado → perfil/preferencias → bloqueo o desvinculación → historial`.

---

## 1. Aceptar términos — `POST /api/v1/vinculos/terminos`

Primer paso obligatorio. No pide datos de identificación y deja el vínculo en
`PENDIENTE_VERIFICACION`. Cada llamada crea un registro pendiente nuevo (no es idempotente).

```bash
curl -s -X POST http://localhost:8081/api/v1/vinculos/terminos \
  -H "Content-Type: application/json" \
  -d '{
    "numeroWhatsapp": "+5491112345678"
  }'
```

**201 Created**

```json
{
  "id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
  "numeroWhatsapp": "+5491112345678",
  "estado": "PENDIENTE_VERIFICACION",
  "fechaActivacion": null,
  "preferencias": {
    "idioma": null,
    "notificacionesHabilitadas": false,
    "horarioPreferido": null
  }
}
```

Errores: **400** número ausente o fuera de E.164 (`VALIDATION_ERROR`); **500** error interno.

---

## 2. Verificar identidad — `POST /api/v1/vinculos/{numero}/verificaciones`

Requiere un vínculo pendiente creado antes y que el cliente exista y esté `ACTIVE` en H1. Cuenta los
fallos de la ventana vigente: al agotarlos responde **423** y deriva a soporte. Una re-vinculación
(vínculo previo `DESVINCULADO` o `BLOQUEADO`) exige el proceso completo.

```bash
curl -s -X POST http://localhost:8081/api/v1/vinculos/+5491112345678/verificaciones \
  -H "Content-Type: application/json" \
  -d '{
    "documentoTipo": "DNI",
    "documentoNumero": "30111222",
    "datosVerificacion": { "codigo": "123456" }
  }'
```

**200** — verificación exitosa (requiere estrategia aprobada por Compliance):

```json
{
  "exitosa": true,
  "vinculoId": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
  "numeroWhatsapp": "+5491112345678",
  "documentoTipo": "DNI",
  "documentoNumero": "30111222",
  "estadoVinculo": "ACTIVO",
  "intentosRestantes": null,
  "verificadoEn": "2026-10-26T12:00:00Z"
}
```

**200** — verificación fallida con la estrategia por defecto:

```json
{
  "exitosa": false,
  "vinculoId": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
  "numeroWhatsapp": "+5491112345678",
  "documentoTipo": "DNI",
  "documentoNumero": "30111222",
  "estadoVinculo": "PENDIENTE_VERIFICACION",
  "intentosRestantes": 2,
  "verificadoEn": null
}
```

**423** al superar los fallos permitidos (default 3 en 15 minutos):

```json
{
  "code": "LOCKED",
  "message": "Verificación pausada temporalmente. Contacte a soporte",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

Otros errores: **400** número/body inválidos; **404** sin términos o cliente inexistente en H1;
**409** cliente no activo o número ya vinculado; **500** fallo de H1 o error interno.

---

## 3. Estado del número — `GET /api/v1/vinculos/{numero}/estado`

Consulta anti-duplicados y fraude: informa si el número tiene un vínculo en cualquier estado, sin
exponer datos del cliente. Un número libre devuelve **200** con `vinculado=false`; **nunca 404**.

```bash
curl -s http://localhost:8081/api/v1/vinculos/+5491112345678/estado
```

**200** — vinculado:

```json
{
  "numeroWhatsapp": "+5491112345678",
  "vinculado": true,
  "estado": "PENDIENTE_VERIFICACION"
}
```

**200** — libre:

```json
{
  "numeroWhatsapp": "+5491112345678",
  "vinculado": false
}
```

Error: **500** error interno.

---

## 4. Perfil del canal — `GET /api/v1/vinculos/{numero}/perfil`

Devuelve los datos de contacto de H1 y las preferencias locales del vínculo `ACTIVO`, sin datos legales
sensibles. Exige `X-Canal-Credencial-Token: <token-H2>` y un vínculo activo.

```bash
curl -s http://localhost:8081/api/v1/vinculos/+5491112345678/perfil \
  -H "X-Canal-Credencial-Token: <token-H2>"
```

**200**

```json
{
  "nombre": "Ana",
  "apellido": "Pérez",
  "nombreTratamiento": "Anita",
  "email": "ana@example.test",
  "telefono": "5551234",
  "preferencias": {
    "idioma": "es",
    "notificacionesHabilitadas": true,
    "horarioPreferido": "09:00-18:00"
  }
}
```

Errores: **401** sin contexto de credencial; **409** sin vínculo `ACTIVO`; **500** fallo de H1 o error
interno.

> El **200** requiere un vínculo `ACTIVO`, no alcanzable con el verificador por defecto. Ver
> [`qa-manual.md`](qa-manual.md#vínculo-activo-para-pruebas-manuales-solo-local).

---

## 5. Preferencias — `PATCH /api/v1/vinculos/{numero}/preferencias`

Solo `idioma`, `notificacionesHabilitadas` y `horarioPreferido`. Los campos ausentes o `null` conservan
el valor actual. Exige vínculo `ACTIVO` y los headers de documento. Una propiedad fuera del contrato
devuelve **400** `VALIDATION_ERROR` sin aplicar cambios.

```bash
curl -s -X PATCH http://localhost:8081/api/v1/vinculos/+5491112345678/preferencias \
  -H "Content-Type: application/json" \
  -H "X-Credencial-Documento-Tipo: DNI" \
  -H "X-Credencial-Documento-Numero: 30111222" \
  -d '{
    "idioma": "es",
    "notificacionesHabilitadas": true
  }'
```

**200**

```json
{
  "id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
  "numeroWhatsapp": "+5491112345678",
  "estado": "ACTIVO",
  "fechaActivacion": "2026-10-26T12:00:00Z",
  "preferencias": {
    "idioma": "es",
    "notificacionesHabilitadas": true,
    "horarioPreferido": "09:00-18:00"
  }
}
```

Error **400** por propiedad desconocida:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene propiedades no permitidas",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

Otros errores: **401** sin contexto de credencial; **404** sin vínculo; **409** vínculo no activo;
**500** error interno.

---

## 6. Bloqueo — `PATCH /api/v1/vinculos/{numero}/bloqueo`

Bloquea un vínculo `ACTIVO` por `SOLICITUD_CLIENTE` (exige contexto de credencial) o por
`POLITICA_BANCO` (llamada interna). Solicita la revocación a H2 de inmediato y de forma best-effort: si
H2 no confirma, el bloqueo local se mantiene y se audita el rechazo.

```bash
curl -s -X PATCH http://localhost:8081/api/v1/vinculos/+5491112345678/bloqueo \
  -H "Content-Type: application/json" \
  -H "X-Canal-Credencial-Token: <token-H2>" \
  -d '{
    "motivo": "SOLICITUD_CLIENTE",
    "confirmacion": true
  }'
```

**200**

```json
{
  "id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
  "numeroWhatsapp": "+5491112345678",
  "estado": "BLOQUEADO",
  "fechaActivacion": "2026-10-26T12:00:00Z",
  "preferencias": {
    "idioma": "es",
    "notificacionesHabilitadas": true,
    "horarioPreferido": "09:00-18:00"
  }
}
```

Bloqueo por política del banco (sin credencial de cliente):

```bash
curl -s -X PATCH http://localhost:8081/api/v1/vinculos/+5491112345678/bloqueo \
  -H "Content-Type: application/json" \
  -d '{"motivo": "POLITICA_BANCO", "confirmacion": true}'
```

Errores: **400** motivo/confirmación inválidos; **401** `SOLICITUD_CLIENTE` sin credencial; **404** sin
vínculo; **409** el vínculo no está `ACTIVO`; **500** error interno.

---

## 7. Desvinculación — `PATCH /api/v1/vinculos/{numero}/desvinculacion`

Desvincula un vínculo `ACTIVO` o `BLOQUEADO` con confirmación explícita y contexto de credencial.
Solicita la revocación a H2 de inmediato y de forma best-effort.

```bash
curl -s -X PATCH http://localhost:8081/api/v1/vinculos/+5491112345678/desvinculacion \
  -H "Content-Type: application/json" \
  -H "X-Canal-Credencial-Token: <token-H2>" \
  -d '{
    "confirmacion": true
  }'
```

**200**

```json
{
  "id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
  "numeroWhatsapp": "+5491112345678",
  "estado": "DESVINCULADO",
  "fechaActivacion": "2026-10-26T12:00:00Z",
  "preferencias": {
    "idioma": "es",
    "notificacionesHabilitadas": true,
    "horarioPreferido": "09:00-18:00"
  }
}
```

Errores: **400** confirmación ausente o `false`; **401** sin contexto de credencial; **404** sin vínculo;
**409** estado incompatible (por ejemplo `PENDIENTE_VERIFICACION`); **500** error interno.

---

## 8. Historial — `GET /api/v1/vinculos/{numero}/historial`

Uso interno de soporte, sin contexto de cliente. Devuelve el estado del vínculo más reciente y los
eventos de auditoría del número, del más reciente al más antiguo. No incluye motivos libres ni datos de
verificación.

```bash
curl -s http://localhost:8081/api/v1/vinculos/+5491112345678/historial
```

**200**

```json
{
  "numeroWhatsapp": "+5491112345678",
  "documentoTipo": "DNI",
  "documentoNumero": "30111222",
  "estadoActual": "ACTIVO",
  "eventos": [
    { "accion": "VINCULACION", "resultado": "EXITOSO", "fecha": "2026-10-26T12:01:00Z" },
    { "accion": "VERIFICACION_EXITOSA", "resultado": "EXITOSO", "fecha": "2026-10-26T12:00:00Z" },
    { "accion": "ACEPTACION_TERMINOS", "resultado": "EXITOSO", "fecha": "2026-10-26T11:58:00Z" }
  ]
}
```

**404** si el número no tiene ningún vínculo registrado:

```json
{
  "code": "NOT_FOUND",
  "message": "No existe un vínculo registrado para el número consultado",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

Error: **500** error interno.

## Salud

```bash
curl -s http://localhost:8081/actuator/health
```

## OpenAPI exportado

Copia estática regenerada en cada `./mvnw -q -B verify`: `delivery/openapi/openapi.json` y `openapi.yaml`.
