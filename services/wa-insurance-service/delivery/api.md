# API — wa-insurance-service

Uso de los endpoints con curl. Los ejemplos coinciden con Swagger y las colecciones.

Base URL local: `http://localhost:8085`  
Header obligatorio: `X-Credencial-Sesion: <JWT_H2>`

## 1) Listar pólizas vigentes

```bash
curl -X GET "http://localhost:8085/api/v1/seguros" \
  -H "X-Credencial-Sesion: eyJhbGciOi..."
```

**200 OK**
```json
[
  {
    "id": "e7a62f43-9b82-4c01-b2ab-7f902eab1234",
    "numeroPoliza": "POL-000123",
    "ramo": "AUTO",
    "vigente": true,
    "fechaVigenciaDesde": "2026-01-01",
    "fechaVigenciaHasta": "2027-01-01",
    "identificadorCorto": "0123"
  }
]
```

**401 UNAUTHORIZED**
```json
{
  "code": "UNAUTHORIZED",
  "message": "Verificá tu identidad para consultar tus seguros.",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

## 2) Consultar coberturas

```bash
curl -X GET "http://localhost:8085/api/v1/seguros/e7a62f43-9b82-4c01-b2ab-7f902eab1234/coberturas" \
  -H "X-Credencial-Sesion: eyJhbGciOi..."
```

**200 OK**
```json
{
  "coberturas": ["Responsabilidad civil", "Daños propios"],
  "exclusiones": ["Uso comercial no declarado"],
  "resumen": true,
  "notaPrevalencia": "Este es un resumen informativo. En caso de disputa prevalecen las condiciones generales oficiales de la póliza."
}
```

**403 FORBIDDEN** (tercero)
```json
{
  "code": "FORBIDDEN",
  "message": "Consultá al titular o acudí a un canal formal para operar sobre esta póliza.",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

**404 NOT_FOUND**
```json
{
  "code": "NOT_FOUND",
  "message": "La póliza no existe.",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

## 3) Denunciar siniestro

```bash
curl -X POST "http://localhost:8085/api/v1/siniestros" \
  -H "X-Credencial-Sesion: eyJhbGciOi..." \
  -H "Content-Type: application/json" \
  -d '{
        "polizaId": "e7a62f43-9b82-4c01-b2ab-7f902eab1234",
        "tipoSiniestro": "AUTO",
        "fechaSiniestro": "2026-10-20",
        "descripcion": "Choque en intersección"
      }'
```

**201 CREATED**
```json
{
  "numeroCaso": "SIN-2026-000123",
  "proximosPasos": "Enviá la documentación requerida por los canales oficiales del banco.",
  "plazos": "Los plazos dependen del proceso formal correspondiente a tu ramo; no prometemos liquidación por este canal."
}
```

**400 VALIDATION_ERROR**
```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [{ "field": "descripcion", "message": "no debe estar vacío" }],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

**409 CONFLICT** (duplicado)
```json
{
  "code": "CONFLICT",
  "message": "Ya existe una denuncia abierta para esta póliza y tipo de evento.",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

## 4) Consultar siniestro

```bash
curl -X GET "http://localhost:8085/api/v1/siniestros/SIN-2026-000123" \
  -H "X-Credencial-Sesion: eyJhbGciOi..."
```

**200 OK**
```json
{
  "numeroCaso": "SIN-2026-000123",
  "estado": "DOCUMENTACION_PENDIENTE",
  "pendiente": "Presentar informe pericial"
}
```

**403 FORBIDDEN** (caso ajeno)
```json
{
  "code": "FORBIDDEN",
  "message": "Consultá al titular o acudí a un canal formal para operar sobre este caso.",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

**404 NOT_FOUND**
```json
{
  "code": "NOT_FOUND",
  "message": "El caso de siniestro no existe.",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

## 5) Documentación pendiente

```bash
curl -X GET "http://localhost:8085/api/v1/siniestros/SIN-2026-000123/documentacion-pendiente" \
  -H "X-Credencial-Sesion: eyJhbGciOi..."
```

**200 OK**
```json
{
  "documentos": ["Informe pericial", "Fotografías del siniestro"],
  "orientacionEnvio": "Enviá estos documentos por los canales oficiales del banco; este chat no reemplaza el proceso formal."
}
```

**Copia estática regenerada en cada `./mvnw verify`:** `delivery/openapi/openapi.json` y `openapi.yaml`. Disponible en Swagger UI `/swagger-ui.html` y JSON `/v3/api-docs`.
