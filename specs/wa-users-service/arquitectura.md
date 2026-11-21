---
title: Arquitectura — Servicio de Usuarios (H1)
status: borrador
source: https://app.notion.com/36875bb08ca78197a853e85505bf49a5 (página H1 —
  Servicio de Usuarios) y orden del equipo
updated: 2026-11-21T03:51:25.169Z
---

# Arquitectura — Servicio de Usuarios (H1)

## 1. Decisión de alcance y encuadre

`wa-users-service` es un **servicio de dominio Java / Spring Boot**, no un componente de IA (no hay agente, MCP, RAG,
pgvector ni Redis). Toda la discusión de "credencial fuera del prompt", memoria del agente, etc. **no aplica**: este
servicio es un backend REST clásico al que *otros* componentes (canal, capa MCP, frontend de operaciones) llaman por
HTTP. La arquitectura se diseña con el stack Java del programa (Java 21, Spring Boot 3.x, JPA, PostgreSQL, Maven +
`./mvnw`).

Datos de identidad (de la orden y de las convenciones del programa, no inventados):

| Dato | Valor | Fuente |
|------|-------|--------|
| Carpeta del componente | `services/wa-users-service` | orden del equipo |
| groupId Maven | `com.bcra` | convenciones del programa |
| Paquete base | `com.bcra.users.ws` | orden del equipo |
| Puerto | `8080` dentro del contenedor | orden del equipo |
| Dominio | Usuarios | orden del equipo |
| Contratos | `shared/contracts/wa-users-service/{openapi.yaml,handoff.md}` | orden del equipo |

## 2. Estructura del paquete (obligatoria, por capas)

Maven `groupId=com.bcra`, base package `com.bcra.users.ws`. Un módulo de dominio por agregado, cada uno con
subpaquetes por capa (regla `architecture.md` del pack Java). Prohibido el paquete plano.

```
com.bcra.users.ws
├── WsUsersApplication.java            # clase principal (@SpringBootApplication)
├── user/                              # agregado Usuarios (R1, R2, R3, R4, R5)
│   ├── controller/  UserController, UserStatusController
│   ├── service/     UserService, UserStatusService
│   ├── repository/  UserRepository
│   ├── mapper/      UserMapper (MapStruct)
│   ├── entity/      User, UserStatus (enum)
│   └── dto/         CreateUserRequest, UserResponse, UpdateUserRequest,
│                    ChangeStatusRequest, DocumentRef
├── audit/                             # agregado Auditoría (R6)
│   ├── controller/  AuditController
│   ├── service/     AuditService
│   ├── repository/  AuditEventRepository
│   ├── mapper/      AuditMapper
│   ├── entity/      AuditEvent, AuditAction (enum)
│   └── dto/         AuditEventResponse
├── config/            # OpenApiConfig, JacksonConfig (ISO-8601 UTC), SecurityConfig
└── exception/         # GlobalExceptionHandler, ErrorCode, ErrorResponse, FieldError,
                       # NotFoundException, ConflictException, base de dominio
```

### Responsabilidades por capa (regla `architecture.md`)

| Capa | Paquete | Hace | Prohibido |
|------|---------|------|-----------|
| Controller | `…controller` | HTTP, `@Valid`, status, header `X-Correlation-Id` | lógica de negocio, mapeo manual |
| Service | `…service` | reglas (unicidad, transiciones de estado), `@Transactional` | HTTP |
| Repository | `…repository` | persistencia JPA | lógica de negocio |
| Mapper | `…mapper` | Entity ↔ DTO (MapStruct) | reglas |
| Entity | `…entity` | modelo persistido (Lombok, sin `@Data`) | exponerse por API |
| DTO | `…dto` | contrato API (records) | lógica |

## 3. API — endpoints, versionado y errores

Versionado en el path bajo `/api/v1` (skill `api-versioning`; `api.versioning.enabled=true` → paquetes
`…/api/vN` por controller/dto/mapper cuando se versiona; hoy solo `v1`).

| Método | Path | Qué hace | Req body | Éxito | Errores |
|--------|------|----------|----------|-------|---------|
| POST | `/api/v1/users` | Alta de usuario (R1, R5, R6) | `CreateUserRequest` | `201` + `UserResponse` (`Location`) | `400 VALIDATION_ERROR` (datos incompletos), `409 CONFLICT` (duplicado) |
| GET | `/api/v1/users/{id}` | Consulta por ID interno (R2) | — | `200` + `UserResponse` | `404 NOT_FOUND` |
| GET | `/api/v1/users/by-document?tipo=&numero=` | Consulta por documento (R2) | — | `200` + `UserResponse` | `400 BAD_REQUEST`, `404 NOT_FOUND` |
| PATCH | `/api/v1/users/{id}` | Actualiza campos permitidos (R3) | `UpdateUserRequest` | `200` + `UserResponse` | `400 VALIDATION_ERROR`, `404 NOT_FOUND` |
| PATCH | `/api/v1/users/{id}/status` | Cambio de estado (R4) | `ChangeStatusRequest` | `200` + `UserResponse` | `404 NOT_FOUND`, `409 CONFLICT` (transición inválida) |
| GET | `/api/v1/users/{id}/audit` | Historial resumido (R6, soporte) | — | `200` + lista `AuditEventResponse` | `404 NOT_FOUND` |

### Contrato de errores (único, regla `error-contract`)

Todos los endpoints responden el **mismo JSON**:
`ErrorResponse(code, message, details[], timestamp)` con `code ∈ {VALIDATION_ERROR, BAD_REQUEST, NOT_FOUND,
CONFLICT, INTERNAL_ERROR}`. `GlobalExceptionHandler` (`@RestControllerAdvice`) en `exception/`; el stacktrace va
solo al log. `server.error.include-stacktrace: never`, `include-message: never`.

Excepciones de dominio nuevas (extienden `NotFoundException` / `ConflictException`):
`UserNotFoundException`, `DuplicateUserException`, `InvalidStatusTransitionException`.

### DTOs (records, contrato API)

- `CreateUserRequest(tipoDocumento, numeroDocumento, nombreCompleto, fechaNacimiento, email, telefono)` —
  baseline KYC mínimo, **todos `@NotBlank`/validados** (ver Supuestos: la lista exacta la confirma Compliance).
- `UpdateUserRequest(nombreTratamiento, email, telefono, preferencias)` — solo campos no sensibles permitidos (R3).
- `ChangeStatusRequest(nuevoEstado, motivo)`.
- `UserResponse(id, tipoDocumento, numeroDocumentoEnmascarado, nombreCompleto, estado, email, telefono, createdAt, updatedAt)`.
  El documento sale **enmascarado** (`***1234`), nunca completo en respuestas de consulta.

## 4. Modelo de datos (PostgreSQL, relacional)

Persistencia JPA sobre PostgreSQL (no pgvector/Redis: eso es para componentes de IA). Migraciones con Flyway
(`db/migration/V1__init.sql`). `spring.jpa.open-in-view: false`. H2 solo perfil `dev`.

### Tabla `users`

| Columna | Tipo | Notas |
|---------|------|-------|
| `id` | uuid PK | generado (`UUID`), identificador interno acordado |
| `tipo_documento` | varchar | enum `DocumentType` (`@Enumerated(STRING)`) |
| `numero_documento` | varchar | **único** (índice único de unicidad, R5) |
| `nombre_completo` | varchar | |
| `fecha_nacimiento` | date | |
| `email` | varchar | |
| `telefono` | varchar | |
| `estado` | varchar | enum `UserStatus{ACTIVO,SUSPENDIDO,BAJA_LOGICA}` |
| `created_at` / `updated_at` | timestamptz | |
| `version` | int | control de concurrencia optimista (`@Version`) |

Índices: `unique(tipo_documento, numero_documento)` (R5), `idx_users_estado(estado)`.

### Tabla `audit_events` (R6)

| Columna | Tipo | Notas |
|---------|------|-------|
| `id` | bigserial PK | |
| `user_id` | uuid FK→users | |
| `action` | varchar | enum `AuditAction{ALTA, ACTUALIZACION, CAMBIO_ESTADO}` |
| `resultado` | varchar | enum `AuditResult{EXITOSO, RECHAZADO}` |
| `detalle` | jsonb | resumen del cambio (sin PII sensible; claves, no valores completos) |
| `correlation_id` | varchar | trazabilidad de punta a punta |
| `actor` | varchar | rol/identificador de quien ejecutó (de headers, ver §5) |
| `created_at` | timestamptz | fecha de la acción |

Índice: `idx_audit_user(created_at desc)` por usuario. **Retención**: los eventos de auditoría **no se borran**
(regla 5 de la fuente); se conservan indefinidamente (sujeto a política de retención del banco, fuera de este
servicio). `audit_events` es solo-append (sin update/delete desde la API).

### Transiciones de estado (R4)

`ACTIVO → SUSPENDIDO`, `ACTIVO → BAJA_LOGICA`, `SUSPENDIDO → ACTIVO` (reactivar), `SUSPENDIDO → BAJA_LOGICA`.
Cualquier otra → `409 CONFLICT InvalidStatusTransitionException`. `BAJA_LOGICA` es terminal. La validación vive
en `UserStatusService`, no en el controller.

## 5. Recorrido de identidad y contexto (sin pasar por ningún modelo)

No hay modelo de lenguaje en este servicio. La identidad y los permisos viajan por **headers HTTP**, evaluados en el
servicio, nunca en un body ni en un prompt:

- `X-Correlation-Id` — generado si falta; se propaga a logs (MDC) y a `audit_events.correlation_id`.
- `X-Actor-Id` / `X-Actor-Role` — identidad y rol de quien llama (negocio/operaciones, soporte, servicio interno).
  En producción se validan en el gateway/IAM del programa; este servicio confía en el header ya autenticado y lo
  usa para autorizar la operación y para la auditoría.
- Control de acceso por rol (regla de "cada usuario accede solo a lo suyo"): operaciones de negocio/operaciones
  pueden crear/actualizar/cambiar estado; soporte solo consulta estado e historial; servicios internos consultan
  datos básicos. Se implementa con Spring Security a nivel de método/endpoint leyendo el rol del header. La
  credencial de sesión del canal (H2) **nunca** llega a este servicio.

## 6. Seguridad, PII y auditoría

- **PII enmascarada**: `numero_documento` se expone enmascarado en `UserResponse` (`***1234`); `toString()` de
  entidades no incluye PII; logs no imprimen documento, email ni teléfono completos.
- **Sin secretos en el repo**: credenciales de Postgres por variables de entorno; solo `.env.example`.
- **Auditoría** (R6): toda alta, actualización y cambio de estado inserta un `audit_events` con fecha, acción,
  resultado y `correlation_id`, en la misma transacción que el cambio.
- **Validación de entrada** (Bean Validation) y `details` por campo en `VALIDATION_ERROR`; mensajes sin jerga ni
  stacktrace al cliente.

## 7. Observabilidad

- `correlationId` de punta a punta: filtro que lee/genera `X-Correlation-Id`, lo pone en MDC (structlog/logback JSON)
  y lo persiste en auditoría. Se propaga a eventos salientes a servicios dependientes.
- Métricas (Actuator + Micrometer): latencia por endpoint (`http.server.requests`), contador de altas, de altas
  rechazadas por duplicado, de cambios de estado y de escalamientos; `health`/`info` expuestos.
- Logs JSON estructurados, sin PII.

## 8. Notificación a servicios dependientes (R4)

Cuando cambia el estado (activo↔suspendido↔baja), se publica un evento `wa.users.status-changed` (prefijo `wa.`
de los topics del programa) con `{userId, estadoAnterior, estadoNuevo, correlationId}`. La publicación es
**fire-and-forget** tras el commit (outbox o evento Spring + listener), de modo que la operación transaccional no
depende del broker. El detalle del broker lo define el frente de plataforma; este servicio solo publica el evento y
lo documenta en el contrato. *Si el broker aún no existe, el evento se registra en log y el hook queda preparado.*

## 9. Configuración y puertos

- `application.yml`: datasource por env, `spring.jpa.open-in-view: false`, `spring.mvc.problemdetails.enabled: false`,
  `server.error.include-stacktrace: never`, actuator `health,info`.
- Puerto `8080` interno (mapeo a host lo define DevOps). Perfiles `dev`/`test`/`prod`.
- Variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_ACTOR_HEADER_*` (nombres), todas con `.env.example` vacío.

## 10. Contratos

- **Publica**: `shared/contracts/wa-users-service/openapi.yaml` (esta API, con ejemplos y errores) y `handoff.md`
  (cómo consumirlo: base path, headers, estados, correlación). Los redactan dev/documentador a partir de esta
  arquitectura; esta spec fija endpoints, DTOs y códigos de error.
- **Consume**: ninguno (es el primer servicio de dominio). Solo comparte el broker para el evento de §8.

## 11. Supuestos del arquitecto (a confirmar; no bloquean la estructura)

1. **Datos mínimos de alta (Compliance)**: se asume baseline KYC estándar — `tipoDocumento`, `numeroDocumento`,
   `nombreCompleto`, `fechaNacimiento`, `email`, `telefono`. Cambiar la lista solo toca `CreateUserRequest` y sus
   validaciones. **Confirmar con Compliance.**
2. **Identificador de consulta**: se da **ambos** — ID interno (uuid) como canónico y lookup por documento — así no
   se bloquea el frente que lo necesite. **Confirmar cuál es el acordado.**
3. **Campos permitidos de actualización**: se asumen no sensibles — `nombreTratamiento`, `email`, `telefono`,
   `preferencias` (no domicilio legal, no estado civil). **Confirmar con el banco.**
4. **Contrato previo**: no existe OpenAPI en el repo (verificado: repo greenfield). No hay contrato que respetar.

Estos supuestos afectan DTOs y validaciones (detalle local); la estructura de paquetes, el modelo de datos, el
contrato de errores, auditoría, seguridad y observabilidad no cambian.

## 12. ADRs

Ver `docs/adr/`:

- `0001-stack-java-spring-boot.md` — por qué Java/Spring Boot/JPA/PostgreSQL (dominio transaccional, no IA).
- `0002-identificador-interno-y-unicidad-por-documento.md` — uuid interno + índice único por documento.
- `0003-auditoria-append-only-y-retencion.md` — `audit_events` solo-append, retención indefinida.
- `0004-evento-de-cambio-de-estado-a-dependientes.md` — publicación `wa.users.status-changed` desacoplada.
