# Ejemplos — elegibilidad y errores

## Activo (apto)

```http
GET /api/v1/users/a1b2c3d4-e5f6-7890-abcd-ef1234567890/channel-eligibility
```

```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "ACTIVO",
  "aptoParaVinculoCanal": true
}
```

## Suspendido (no apto)

```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "SUSPENDIDO",
  "aptoParaVinculoCanal": false
}
```

## Usuario inexistente

```json
{
  "code": "NOT_FOUND",
  "message": "El recurso solicitado no existe",
  "details": [],
  "timestamp": "2026-10-15T12:00:00Z"
}
```
