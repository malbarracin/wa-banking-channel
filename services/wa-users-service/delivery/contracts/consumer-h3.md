# Integración H3 — elegibilidad de vínculo

Antes de vincular un canal WhatsApp a un cliente:

1. El cliente **debe existir** en `wa-users-service` (UUID interno).
2. Consultar `GET /api/v1/users/{id}/channel-eligibility`.
3. Continuar el vínculo **solo si** `aptoParaVinculoCanal === true`.

Si el usuario no existe → `404 NOT_FOUND` (prerrequisito no cumplido).  
Si está `SUSPENDIDO` o `BAJA_LOGICA` → `aptoParaVinculoCanal=false` (no vincular).

Base URL local: `http://localhost:8080`. En entornos desplegados usar el gateway/red acordados;
el servicio no exige JWT en H1.
