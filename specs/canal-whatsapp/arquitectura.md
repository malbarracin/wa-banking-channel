---
title: wa-channel-service — arquitectura técnica
status: borrador
source: specs/canal-whatsapp/spec.md ·
  services/wa-users-service/delivery/openapi/openapi.yaml
updated: 2026-11-27T23:09:34.053Z
---

# wa-channel-service — arquitectura técnica

## Datos del proyecto

| Campo | Valor | Fuente |
|-------|-------|--------|
| Carpeta del componente | `services/wa-channel-service` | Hito H3 |
| groupId Maven | `com.bcra` | Roadmap, convenciones técnicas |
| Paquete base | `com.bcra.channel.ws` | Roadmap, tabla de paquetes por hito |
| Nombre del artefacto | `wa-channel-service` | Roadmap |
| Java | 21 | Convención del programa |
| Spring Boot | 3.x (última estable) | Convención del programa |
| Puerto contenedor | 8080 (configurable con `SERVER_PORT`) | Roadmap |
| Base de datos | PostgreSQL | Stack del programa (JPA + Flyway) |
| API versioning | No (`api.versioning.enabled` ausente) | Decisión: todo bajo `/api/v1/` |
| Contrato a publicar | `shared/contracts/wa-channel-service/openapi.yaml` y `handoff.md` | Hito H3 |
| Contrato a respetar | `services/wa-users-service/delivery/openapi/openapi.yaml` (H1) | Hito H3 |

## Estructura de paquetes

```
services/wa-channel-service/
src/main/java/com/bcra/channel/ws/
├── WaChannelServiceApplication.java
├── config/
│   └── OpenApiConfig.java
├── exception/
│   ├── ErrorCode.java
│   ├── ErrorResponse.java
│   ├── FieldError.java
│   ├── GlobalExceptionHandler.java
│   ├── NotFoundException.java
│   └── ConflictException.java
├── binding/
│   ├── controller/
│   │   └── BindingController.java
│   ├── service/
│   │   └── BindingService.java
│   ├── repository/
│   │   ├── BindingRepository.java
│   │   └── VerificationAttemptRepository.java
│   ├── mapper/
│   │   └── BindingMapper.java
│   ├── entity/
│   │   ├── Binding.java
│   │   ├── BindingStatus.java
│   │   └── VerificationAttempt.java
│   ├── dto/
│   │   ├── BindingStatusResponse.java
│   │   ├── AcceptTermsRequest.java
│   │   ├── AcceptTermsResponse.java
│   │   ├── VerifyIdentityRequest.java
│   │   ├── VerifyIdentityResponse.java
│   │   ├── ChannelProfileResponse.java
│   │   ├── UpdatePreferencesRequest.java
│   │   ├── BlockBindingRequest.java
│   │   ├── BlockBindingResponse.java
│   │   ├── UnbindRequest.java
│   │   ├── UnbindResponse.java
│   │   └── VerifiableResultResponse.java
│   └── exception/
│       ├── BindingNotFoundException.java
│       ├── BindingConflictException.java
│       └── VerificationAttemptsExhaustedException.java
├── audit/
│   ├── controller/
│   │   └── AuditController.java
│   ├── service/
│   │   └── AuditService.java
│   ├── repository/
│   │   └── AuditRepository.java
│   ├── mapper/
│   │   └── AuditMapper.java
│   ├── entity/
│   │   ├── AuditEntry.java
│   │   └── AuditAction.java
│   └── dto/
│       └── AuditEntryResponse.java
└── client/
    └── UsersServiceClient.java
src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_bindings.sql
    └── V2__create_verification_attempts.sql
src/test/java/com/bcra/channel/ws/
├── WaChannelServiceApplicationTest.java
├── binding/
│   ├── BindingControllerTest.java
│   ├── BindingServiceTest.java
│   └── BindingIntegrationTest.java
└── audit/
    └── AuditControllerTest.java
```

## API — endpoints

Todos bajo `/api/v1/`. Sin versionado de paquetes (`api.versioning.enabled` no activo).

### GET `/api/v1/bindings/{whatsappNumber}`

Consultar estado del vínculo de un número.

- **Request:** `whatsappNumber` en path (E.164, ej. `+5491112345678`)
- **Response 200:**
  ```json
  {
    "whatsappNumber": "+5491112345678",
    "status": "ACTIVO",
    "clientId": "uuid",
    "bindingId": "uuid"
  }
  ```
  `clientId` y `bindingId` presentes solo si status ≠ `SIN_VINCULO`.
- **Errors:**
  - 400 `BAD_REQUEST` — número con formato inválido
  - 404 `NOT_FOUND` — número sin vínculo registrado (SIN_VINCULO → se devuelve igual 200 con status `SIN_VINCULO`, no 404)
  - 500 `INTERNAL_ERROR`

### POST `/api/v1/bindings/{whatsappNumber}/terms`

Registrar aceptación de términos y condiciones.

- **Request body:** `AcceptTermsRequest { accepted: boolean }`
- **Response 200:** `AcceptTermsResponse { bindingId: uuid, status: "PENDIENTE_VERIFICACION", message: string }`
- **Errors:**
  - 400 `BAD_REQUEST` — `accepted` es false
  - 409 `CONFLICT` — el número ya tiene vínculo en estado `ACTIVO`
  - 500 `INTERNAL_ERROR`

### POST `/api/v1/bindings/{whatsappNumber}/verify`

Verificación de identidad del cliente.

- **Request body:** `VerifyIdentityRequest { uniquenessValue: string, verificationData: string }`
- **Response 200:** `VerifyIdentityResponse { bindingId: uuid, status: string, attemptsRemaining: int, verifiableResult: VerifiableResultResponse }` (`verifiableResult` presente solo si status = `ACTIVO`)
- **Errors:**
  - 400 `BAD_REQUEST` — datos inválidos
  - 404 `NOT_FOUND` — cliente no encontrado en H1 (incluye mensaje de derivación al alta)
  - 409 `CONFLICT` — cliente suspendido/inactivo, o número ya vinculado a otro cliente
  - 423 `VERIFICATION_LOCKED` — intentos agotados, vínculo bloqueado (código nuevo)
  - 500 `INTERNAL_ERROR`

> **Nota:** 423 es el código HTTP estándar para recurso bloqueado. Se agrega `VERIFICATION_LOCKED` al `ErrorCode` enum.

### GET `/api/v1/bindings/{whatsappNumber}/profile`

Consultar perfil del canal.

- **Response 200:** `ChannelProfileResponse { whatsappNumber, clientId, displayName, bindingStatus, languagePreference, notificationsEnabled, createdAt, updatedAt }`
- **Errors:**
  - 404 `NOT_FOUND` — sin vínculo activo
  - 500 `INTERNAL_ERROR`

### PATCH `/api/v1/bindings/{whatsappNumber}/preferences`

Actualizar preferencias del canal.

- **Request body:** `UpdatePreferencesRequest { languagePreference: string, notificationsEnabled: boolean }`
- **Response 200:** `ChannelProfileResponse`
- **Errors:**
  - 400 `VALIDATION_ERROR` — campos inválidos
  - 404 `NOT_FOUND` — sin vínculo activo
  - 500 `INTERNAL_ERROR`

### POST `/api/v1/bindings/{whatsappNumber}/block`

Bloquear vínculo (cliente o banco).

- **Request body:** `BlockBindingRequest { actor: string, reason: string }`
- **Response 200:** `BlockBindingResponse { bindingId, status: "BLOQUEADO", message: string }`
- **Errors:**
  - 400 `BAD_REQUEST` — actor faltante
  - 404 `NOT_FOUND` — vínculo no encontrado
  - 409 `CONFLICT` — vínculo ya bloqueado o desvinculado
  - 500 `INTERNAL_ERROR`

### POST `/api/v1/bindings/{whatsappNumber}/unbind`

Desvincular número (cliente o banco).

- **Request body:** `UnbindRequest { actor: string, reason: string }`
- **Response 200:** `UnbindResponse { bindingId, status: "DESVINCULADO", message: string }`
- **Errors:**
  - 400 `BAD_REQUEST` — actor faltante
  - 404 `NOT_FOUND` — vínculo no encontrado
  - 409 `CONFLICT` — ya desvinculado
  - 500 `INTERNAL_ERROR`

### GET `/api/v1/bindings/{whatsappNumber}/history`

Historial resumido para soporte.

- **Headers requeridos:** `X-Internal-Role: support`
- **Response 200:** `List<AuditEntryResponse>` (ordenado por fecha desc)
- **Errors:**
  - 400 `BAD_REQUEST` — header ausente
  - 403 `FORBIDDEN` — rol inválido (código nuevo: `FORBIDDEN`)
  - 404 `NOT_FOUND` — número sin vínculo
  - 500 `INTERNAL_ERROR`

> **Nota:** 403 se agrega al `ErrorCode` enum. La autorización se implementa como `BankInternalAuthFilter` verificando el header `X-Internal-Role` contra el valor configurable `bank.channel.internal-roles.support`.

## Estados del vínculo (enum `BindingStatus`)

```java
public enum BindingStatus {
    SIN_VINCULO,           // virtual: ausencia de registro
    PENDIENTE_VERIFICACION,
    ACTIVO,
    BLOQUEADO,
    DESVINCULADO
}
```

Transiciones permitidas (validadas en `BindingService`):

| Desde | Hacia | Disparador |
|-------|-------|------------|
| (sin registro) | `PENDIENTE_VERIFICACION` | Aceptación de términos |
| `PENDIENTE_VERIFICACION` | `ACTIVO` | Verificación exitosa + anti-fraude OK |
| `PENDIENTE_VERIFICACION` | `BLOQUEADO` | Intentos agotados |
| `ACTIVO` | `BLOQUEADO` | Cliente o banco |
| `ACTIVO` | `DESVINCULADO` | Cliente o banco |
| `BLOQUEADO` | `ACTIVO` | Re-verificación |
| `BLOQUEADO` | `DESVINCULADO` | Cliente o banco |
| `DESVINCULADO` | `PENDIENTE_VERIFICACION` | Re-vinculación (términos) |

## Entidades JPA

### `Binding`

```
id: UUID (PK)
whatsappNumber: String (único, índice)
clientId: UUID (nullable hasta verificación)
status: BindingStatus (@Enumerated STRING)
termsAcceptedAt: Instant (nullable)
verifiedAt: Instant (nullable)
languagePreference: String (default "es")
notificationsEnabled: boolean (default true)
actor: String (quién disparó la última acción)
createdAt: Instant
updatedAt: Instant
```

### `VerificationAttempt`

```
id: UUID (PK)
bindingId: UUID (FK → Binding)
attemptedAt: Instant
success: boolean
```

### `AuditEntry` (módulo audit)

```
id: UUID (PK)
bindingId: UUID
whatsappNumber: String
action: AuditAction (@Enumerated STRING)
result: String (OK | ERROR | BLOQUEADO)
actor: String
detail: String (nullable — sin datos de verificación ni secretos)
ocurredAt: Instant
```

## Interacción con H1 (wa-users-service)

El cliente HTTP `UsersServiceClient` (en `client/`) llama a H1 con `RestClient` (Spring 6). Solo usa:

- `GET /api/v1/users/{id}` — consulta por `uniquenessValue` (ver nota)
- Busca cliente por `uniquenessValue` usando `GET /api/v1/users?uniquenessValue=...` si H1 expone búsqueda; si no, se acepta solo verificar existencia por ID devuelto por el flujo.

> **Nota sobre el contrato de H1:** `GET /api/v1/users/{id}` recibe UUID interno. H1 no expone búsqueda por `uniquenessValue` en el contrato actual. Durante el paso de verificación, H3 recibirá el `uniquenessValue` del cliente. Para resolverlo a un `id` sin modificar el contrato de H1, se implementa la búsqueda como parámetro de query `GET /api/v1/users?search={uniquenessValue}` **si H1 lo agrega**; de lo contrario, el primer paso de `verify` crea el pedido y H1 retorna 404 cuando el cliente no existe. Esta es una limitación conocida del contrato actual de H1 que el tech-lead debe resolver al crear la tarea correspondiente.

Respuestas de H1 mapeadas:
- 200 con `status: ACTIVE` → cliente válido para vincular
- 200 con `status: SUSPENDED` o `INACTIVE` → rechazar vinculación
- 404 → derivar al alta bancaria
- 5xx → propagar como 500 `INTERNAL_ERROR`

## Contrato de errores

Se usa el contrato estándar del programa (`ErrorResponse`, `ErrorCode`, `GlobalExceptionHandler` en `exception/`).
Se agregan al enum `ErrorCode`:

| Código | HTTP | Cuándo |
|--------|------|--------|
| `VERIFICATION_LOCKED` | 423 | Intentos de verificación agotados, vínculo bloqueado |
| `FORBIDDEN` | 403 | Acceso a endpoint de soporte sin rol válido |

Excepciones de dominio a crear (en `binding/exception/` o `audit/exception/`):

| Clase | Extiende | Cuándo se lanza |
|-------|----------|-----------------|
| `BindingNotFoundException` | `NotFoundException` | Vínculo no encontrado |
| `BindingConflictException` | `ConflictException` | Conflicto de estado o duplicado |
| `VerificationAttemptsExhaustedException` | `RuntimeException` | Intentos agotados (→ 423) |

## Esquema de base de datos

Migraciones Flyway en `src/main/resources/db/migration/`:

- `V1__create_bindings.sql` — tabla `bindings` con índice único en `whatsapp_number`
- `V2__create_verification_attempts.sql` — tabla `verification_attempts` con FK a `bindings`
- `V3__create_channel_audit.sql` — tabla `channel_audit_entries`

## Parámetros configurables (`application.yml`)

```yaml
bank:
  channel:
    verification:
      policy: document-code        # método de verificación (configurable)
      max-attempts: 3              # tope de intentos por ventana
      window-minutes: 60           # ventana de tiempo
    name: Banco                    # nombre del banco en mensajes al cliente
    support-phone: "0800-XXX-XXXX" # derivación a soporte
    onboarding-url: "https://..." # alta bancaria
    internal-roles:
      support: support             # valor esperado en X-Internal-Role
```

## Estrategia de tests (JaCoCo ≥ 80%)

| Clase de test | Tipo | Qué verifica |
|--------------|------|---------------|
| `WaChannelServiceApplicationTest` | Context load | Arranca sin errores |
| `BindingControllerTest` | MockMvc | Cada endpoint: status HTTP, cuerpo, headers; al menos un caso de error por endpoint (status + `$.code` + `$.timestamp`) |
| `BindingServiceTest` | Unit (Mockito) | Lógica de negocio: transiciones, conteo de intentos, anti-fraude, llamada a H1 |
| `BindingIntegrationTest` | Spring + H2 + WireMock | Flujo completo de onboarding: términos → verificación → vínculo activo; agotamiento de intentos → bloqueo; duplicado de número |
| `AuditControllerTest` | MockMvc | Historial con `X-Internal-Role: support`; 403 sin header |
| `GlobalExceptionHandlerTest` | MockMvc | Formato `ErrorResponse` para 400, 404, 409, 423, 403, 500 |

WireMock mockea el wa-users-service (H1) en los tests de integración.

## Dependencias POM (adicionales al stack base)

| Dependencia | Uso |
|-------------|-----|
| `spring-boot-starter-web` | Web MVC |
| `spring-boot-starter-data-jpa` | JPA |
| `spring-boot-starter-validation` | Bean Validation |
| `spring-boot-starter-actuator` | Salud |
| `flyway-core` | Migraciones |
| `postgresql` (runtime) | Driver PostgreSQL |
| `springdoc-openapi-starter-webmvc-ui` | Swagger / OpenAPI |
| `spring-boot-starter-test` | Tests (scope test) |
| `h2` (test) | Base de datos en memoria para tests |
| `wiremock-spring-boot` o `wiremock-standalone` | Mock de H1 en integración (scope test) |

## Riesgos y dependencias

1. **Contrato de H1 sin búsqueda por `uniquenessValue`:** el contrato actual de wa-users-service no expone búsqueda por `uniquenessValue`. El tech-lead debe evaluar si se agrega un endpoint de búsqueda a H1 (sin modificar el contrato existente, solo sumando) o si se define un protocolo alternativo. Si H1 no se puede cambiar en este ciclo, el flujo de verificación debe recibir el `id` interno del cliente directamente del orquestador (H0.2), y no del `uniquenessValue`. Esta decisión se debe registrar antes de implementar `BindingService.verify()`.

2. **Autorización de endpoints de soporte y bloqueo por banco:** se asume `X-Internal-Role` configurable. No es Spring Security completo; es suficiente para el piloto. El tech-lead confirma al crear las tareas.

3. **Re-vinculación:** el flujo de re-vinculación reutiliza los mismos endpoints (`/terms` y `/verify`); `BindingService` detecta el estado previo y ejecuta el flujo desde el principio. No requiere endpoints nuevos.

4. **shared/contracts/wa-users-service/openapi.yaml:** no existe en la rama actual. El contrato de referencia para tests de WireMock es `services/wa-users-service/delivery/openapi/openapi.yaml` (mismo contenido, diferente ruta). El tech-lead debe considerar crear el directorio `shared/contracts/` o ajustar la ruta en la documentación del componente.
