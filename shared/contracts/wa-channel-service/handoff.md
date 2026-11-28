# Servicio de vínculos del canal — handoff

El servicio administra el vínculo entre un número de WhatsApp y un cliente del banco, sus preferencias y su historial de auditoría. **No** emite credenciales, no da de alta clientes, no ejecuta operaciones bancarias ni transporta mensajes. El contrato HTTP completo está en [openapi.yaml](openapi.yaml).

## 1. Qué expone y qué no

Todas las rutas bajo `/api/v1/bindings/{whatsappNumber}` (número en E.164; en curl codificar `+` como `%2B`):

| Método | Ruta | Qué hace |
|--------|------|----------|
| GET | `/api/v1/bindings/{whatsappNumber}` | Estado del vínculo (`SIN_VINCULO` si no hay registro) |
| POST | `…/terms` | Aceptar o rechazar términos |
| POST | `…/verify` | Verificar identidad y activar |
| GET | `…/profile` | Perfil del canal (vínculo ACTIVO) |
| PATCH | `…/preferences` | Idioma y notificaciones |
| POST | `…/block` | Bloquear (CLIENTE o BANCO) |
| POST | `…/unbind` | Desvincular (CLIENTE o BANCO) |
| GET | `…/history` | Historial de auditoría (soporte) |

**No hace:** alta de clientes, emisión de credencial, envío/recepción de WhatsApp, Kafka.

## 2. Cómo levantarlo

Desde `services/wa-channel-service`:

```bash
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```

Puerto: `SERVER_PORT` (por defecto 8080). Variables de entorno **sin valores reales**:

| Variable | Uso |
|----------|-----|
| `SERVER_PORT` | Puerto HTTP |
| `DATABASE_URL` | JDBC PostgreSQL |
| `DATABASE_USERNAME` | Usuario de BD |
| `DATABASE_PASSWORD` | Contraseña de BD |
| `BANK_CHANNEL_USERS_SERVICE_BASE_URL` | Base URL de wa-users-service (perfil docker) |

Propiedades `bank.channel.*` (policy, max-attempts, window-minutes, name, support-phone, onboarding-url, internal-roles, users-service): se configuran por entorno. Swagger: `/swagger-ui.html`.

## 3. Autorización y errores

| Operación | Requisito |
|-----------|-----------|
| Historial | Header `X-Internal-Role` = `bank.channel.internal-roles.support` |
| block/unbind con `actor: BANCO` | Header `X-Internal-Role` = `bank.channel.internal-roles.bank-policy` |
| Resto | Sin header (tráfico interno del canal) |

El header es autorización interna del piloto: protegerlo en el límite de red.

Formato de error:

```json
{
  "code": "FORBIDDEN",
  "message": "Rol interno no autorizado",
  "details": [],
  "timestamp": "2026-11-27T15:30:00Z"
}
```

Códigos: `BAD_REQUEST`, `VALIDATION_ERROR`, `FORBIDDEN` (403), `NOT_FOUND` (404), `CONFLICT` (409), `VERIFICATION_LOCKED` (423), `INTERNAL_ERROR` (500).

## 4. Ejemplos de pedido y respuesta

### Estado

```bash
curl -s "http://localhost:8080/api/v1/bindings/%2B5491112345678"
```

```json
{
  "whatsappNumber": "+5491112345678",
  "status": "SIN_VINCULO",
  "clientId": null,
  "bindingId": null
}
```

### Términos

```bash
curl -s -X POST "http://localhost:8080/api/v1/bindings/%2B5491112345678/terms" \
  -H "Content-Type: application/json" \
  -d '{"accepted":true}'
```

```json
{
  "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "PENDIENTE_VERIFICACION",
  "message": "Términos aceptados. Podés continuar con la verificación de identidad."
}
```

### Verificar (el orquestador aporta `clientId` de wa-users-service)

```bash
curl -s -X POST "http://localhost:8080/api/v1/bindings/%2B5491112345678/verify" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"123e4567-e89b-12d3-a456-426614174000","verificationData":"+5491112345678"}'
```

```json
{
  "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "ACTIVO",
  "attemptsRemaining": 0,
  "verifiableResult": {
    "identityVerified": true,
    "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "clientId": "123e4567-e89b-12d3-a456-426614174000",
    "verifiedAt": "2026-11-27T15:30:00Z"
  },
  "message": "¡Listo, Ana Pérez! Tu WhatsApp quedó vinculado de forma segura."
}
```

### Perfil y preferencias

```bash
curl -s "http://localhost:8080/api/v1/bindings/%2B5491112345678/profile"
curl -s -X PATCH "http://localhost:8080/api/v1/bindings/%2B5491112345678/preferences" \
  -H "Content-Type: application/json" \
  -d '{"languagePreference":"en","notificationsEnabled":false}'
```

### Bloqueo / desvinculación

```bash
curl -s -X POST "http://localhost:8080/api/v1/bindings/%2B5491112345678/block" \
  -H "Content-Type: application/json" \
  -d '{"actor":"CLIENTE","reason":"Solicitud del cliente","confirmed":true}'
```

Respuesta: `status: BLOQUEADO`, `credentialRevocationRequired: true` (revocar credencial en el mismo intercambio).

### Historial (soporte)

```bash
curl -s "http://localhost:8080/api/v1/bindings/%2B5491112345678/history" \
  -H "X-Internal-Role: support"
```

## 5. Eventos

Este servicio **no** publica ni consume topics Kafka. El ciclo de mensajes lo manejan gateway y orquestador.

## 6. Límites y comportamientos

- Sin aceptación de términos no se piden datos de identificación.
- `verify` exige vínculo en `PENDIENTE_VERIFICACION` y un `clientId` ACTIVE en wa-users-service.
- Tope de intentos por ventana (`max-attempts` / `window-minutes`): al agotar → `BLOQUEADO` + HTTP 423.
- Un número ACTIVO no puede vincularse a otro `clientId` (409 anti-fraude).
- `verificationData` no se persiste ni se audita; mensajes al cliente sin secretos ni ids internos.
- block/unbind desde ACTIVO (unbind también desde BLOQUEADO); respuesta con `credentialRevocationRequired=true`.
- Re-vinculación desde `DESVINCULADO`: términos + verificación completa.
- Limitación: el `clientId` lo aporta el consumidor; wa-users-service no se consulta por documento.

## 7. Dónde está el resto

| Recurso | Ubicación |
|---------|-----------|
| OpenAPI | [openapi.yaml](openapi.yaml) · también `services/wa-channel-service/delivery/openapi/` |
| README | `services/wa-channel-service/README.md` |
| API con curl | `services/wa-channel-service/delivery/api.md` |
| Arquitectura | `services/wa-channel-service/delivery/architecture-overview.md` |
| QA / colecciones | `services/wa-channel-service/delivery/qa-manual.md`, `delivery/postman/`, `delivery/bruno/` |
| Guía del componente | `services/wa-channel-service/AGENTS.md` |
