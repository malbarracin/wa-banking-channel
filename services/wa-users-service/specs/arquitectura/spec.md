---
title: Arquitectura — plan técnico wa-users-service
status: borrador
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-10-16T17:49:19.380Z
---

# Arquitectura — plan técnico wa-users-service

## Propósito

Plan técnico (sin código) para implementar H1 — Servicio de Usuarios como microservicio Spring Boot greenfield. Insumo del tech-lead para partir el trabajo en TASK.

Trazabilidad funcional: `usuarios/spec` (aprobada) + `fuentes/origen`.

## Identidad y stack

| Dato | Valor | Fuente |
|------|-------|--------|
| Artifact / nombre | `wa-users-service` | `project.yaml` `project.name` |
| Maven groupId | `com.bank` | `project.yaml` `java.group_id` |
| Base package | `com.bank.users` | `project.yaml` `java.base_package` |
| Clase main | `UsersServiceApplication` | convención scaffold |
| Java | 21 | default scaffold (`java.version` ausente) |
| Spring Boot | 3.x (última estable BOM) | stack pack |
| Persistencia | PostgreSQL (JPA) | default `service.database` (ausente en yaml) |
| Puerto | 8080 | default `service.port` (ausente en yaml) |
| API versioning | deshabilitado → todo bajo `/api/v1` | `api.versioning` ausente |
| Optimización nativa | no | `service.optimization` ausente |
| Auth JWT en este hito | **no** | propiedad Notion H1 `Requiere credencial JWT = NO` |

Modo: **CREATE_FROM_ZERO** (no hay `pom.xml` ni `src/`). Primera entrega = scaffold completo; features encima.

Recomendación al TL: completar en `project.yaml` los bloques `service` y `api.versioning` con los valores de la tabla (hoy se usan defaults del pack).

## Decisiones de diseño (ancladas a H1)

No son inventos silenciosos: salen de ejemplos explícitos de H1 / property del hito. Quedan como contrato provisional hasta que Compliance cierre las preguntas abiertas de `usuarios/spec`.

| Decisión | Valor provisional | Ancla |
|----------|-------------------|-------|
| Unicidad | `tipoDocumento` + `numeroDocumento` únicos | H1 §3 / §5 RN2 «ej. no duplicar documento» |
| ID de recurso | UUID interno (`id`) en path; documento solo en body/filtros | H1 §3 «documento, ID interno, etc.» |
| Alta mínima | `tipoDocumento`, `numeroDocumento`, `nombreTratamiento` obligatorios; `email` y `telefonoContacto` opcionales (contacto) | H1 §3 actualización cita nombre/contacto; Compliance no enumera — mínimo operable |
| Campos actualizables | `nombreTratamiento`, `email`, `telefonoContacto`, `preferencias` (mapa JSON no sensible) | H1 §3 «nombre para tratamiento, contacto, preferencias…» |
| No actualizables vía API | domicilio legal, estado civil u otros datos legales sensibles | H1 §3 Fuera de alcance |
| Estados | enum `ACTIVO`, `SUSPENDIDO`, `BAJA_LOGICA` | H1 §3 |
| Transiciones | `ACTIVO↔SUSPENDIDO`; `ACTIVO|SUSPENDIDO→BAJA_LOGICA`; desde `BAJA_LOGICA` **no** reactivar en este hito | H1 U4 + pregunta abierta → matriz conservadora |
| Aptitud vínculo canal | solo `ACTIVO` → `aptoParaVinculoCanal=true` | H1 §5 RN1/RN3 |
| Notificación a dependientes (U4) | **fuera de alcance H1** (sin eventos/Kafka) | pregunta abierta en `usuarios/spec` |
| Seguridad | sin Spring Security JWT; error contract + validación; red/gateway externos | property JWT=NO |
| Auditoría | módulo `audit` persistente (append-only) | H1 §3 / §5 RN5 |

## Módulos y paquetes (OBLIGATORIO por capas)

Base: `com.bank.users`.

```
com.bank.users/
├── UsersServiceApplication.java
├── config/                          # OpenApiConfig, beans
├── exception/                       # ErrorResponse, ErrorCode, GlobalExceptionHandler,
│                                    # NotFoundException, ConflictException
├── user/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── mapper/
│   ├── entity/
│   ├── dto/
│   └── exception/                   # UserNotFoundException, DuplicateUserException,
│                                    # InvalidUserStatusTransitionException
└── audit/
    ├── service/
    ├── repository/
    └── entity/
```

Sin paquetes `api/vN` (versionado off). Controllers bajo `/api/v1/...`.

### Archivos a crear (paths)

**Scaffold**

- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`
- `src/main/resources/application.yml`
- `src/test/resources/application.yml` (H2)
- `src/main/java/com/bank/users/UsersServiceApplication.java`
- `src/test/java/com/bank/users/UsersServiceApplicationTest.java`
- `src/main/java/com/bank/users/exception/ErrorResponse.java`
- `src/main/java/com/bank/users/exception/FieldError.java`
- `src/main/java/com/bank/users/exception/ErrorCode.java`
- `src/main/java/com/bank/users/exception/NotFoundException.java`
- `src/main/java/com/bank/users/exception/ConflictException.java`
- `src/main/java/com/bank/users/exception/GlobalExceptionHandler.java`
- `src/main/java/com/bank/users/config/OpenApiConfig.java`
- `.gitignore`, `README.md` inicial

**Módulo user**

- `.../user/entity/User.java`
- `.../user/entity/UserStatus.java` (enum)
- `.../user/repository/UserRepository.java`
- `.../user/service/UserService.java`
- `.../user/mapper/UserMapper.java`
- `.../user/controller/UserController.java`
- `.../user/dto/CreateUserRequest.java`
- `.../user/dto/UpdateUserRequest.java`
- `.../user/dto/ChangeUserStatusRequest.java`
- `.../user/dto/UserResponse.java`
- `.../user/dto/UserChannelEligibilityResponse.java`
- `.../user/exception/UserNotFoundException.java`
- `.../user/exception/DuplicateUserException.java`
- `.../user/exception/InvalidUserStatusTransitionException.java`

**Módulo audit**

- `.../audit/entity/AuditEntry.java`
- `.../audit/repository/AuditEntryRepository.java`
- `.../audit/service/AuditService.java`

(Sin controller HTTP de audit en H1: exposición vía historial resumido en consulta de soporte / campo en `UserResponse`.)

## Modelo de dominio

### `User` (JPA)

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | UUID | PK, generado |
| `tipoDocumento` | String | parte unicidad |
| `numeroDocumento` | String | parte unicidad |
| `nombreTratamiento` | String | |
| `email` | String, nullable | contacto |
| `telefonoContacto` | String, nullable | contacto; **no** es vínculo WhatsApp |
| `preferencias` | JSON / `@JdbcTypeCode(SqlTypes.JSON)` Map | no sensibles |
| `status` | `UserStatus` STRING | |
| `createdAt` / `updatedAt` | Instant | |

Constraint único: `(tipoDocumento, numeroDocumento)`.
`toString`/logs: sin PII completa (no loguear documento completo ni email).

### `UserStatus`

`ACTIVO`, `SUSPENDIDO`, `BAJA_LOGICA`.

### `AuditEntry`

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | UUID | |
| `userId` | UUID, nullable | null si alta fallida sin id |
| `action` | enum/string | `ALTA`, `ACTUALIZACION`, `CAMBIO_ESTADO` |
| `result` | enum/string | `OK`, `RECHAZADO` |
| `detail` | String | motivo corto, sin PII |
| `occurredAt` | Instant | |

## Contratos HTTP (`/api/v1`)

Error uniforme en todos: `ErrorResponse` (`code`, `message`, `details[]`, `timestamp`) — skill `error-contract`.

### POST `/api/v1/users` — Alta

- **Request** `CreateUserRequest`: `tipoDocumento`*, `numeroDocumento`*, `nombreTratamiento`*; `email`, `telefonoContacto` opcionales (`@Valid`).
- **Response** 201 `UserResponse` (id, datos permitidos, status=`ACTIVO`, `aptoParaVinculoCanal=true`).
- **Errores**: 400 `VALIDATION_ERROR`; 409 `CONFLICT` (`DuplicateUserException`); 500 `INTERNAL_ERROR`.
- **Side effect**: `AuditEntry` ALTA OK/RECHAZADO.

### GET `/api/v1/users/{id}` — Consulta

- **Response** 200 `UserResponse` (datos autorizados + status + `aptoParaVinculoCanal` + `historialResumido` opcional/lista corta de audit).
- **Errores**: 404 `NOT_FOUND` (`UserNotFoundException`); 400 si UUID inválido (`BAD_REQUEST`).

### PATCH `/api/v1/users/{id}` — Actualización datos permitidos

- **Request** `UpdateUserRequest`: solo campos permitidos, todos opcionales (al menos uno presente).
- Campos legales sensibles en body → 400 `BAD_REQUEST` (rechazo explícito; no se aplican).
- **Response** 200 `UserResponse`.
- **Errores**: 404 `NOT_FOUND`; 400 `VALIDATION_ERROR` / `BAD_REQUEST`; 500.
- **Side effect**: audit ACTUALIZACION OK.

### PATCH `/api/v1/users/{id}/status` — Cambio de estado

- **Request** `ChangeUserStatusRequest`: `status`* (`SUSPENDIDO` \| `ACTIVO` \| `BAJA_LOGICA`).
- Valida matriz de transiciones en `UserService`.
- **Response** 200 `UserResponse`.
- **Errores**: 404 `NOT_FOUND`; 409 `CONFLICT` (`InvalidUserStatusTransitionException`); 400 `VALIDATION_ERROR`.
- **Side effect**: audit CAMBIO_ESTADO OK/RECHAZADO.

### GET `/api/v1/users/{id}/channel-eligibility` — Aptitud vínculo canal (H3)

- **Response** 200 `UserChannelEligibilityResponse`: `{ "userId", "status", "aptoParaVinculoCanal" }`.
- `aptoParaVinculoCanal == (status == ACTIVO)`.
- **Errores**: 404 `NOT_FOUND`.
- Cumple RN1/RN3 y AC de vínculo sin implementar WhatsApp.

## Capas y responsabilidades

| Capa | Responsabilidad |
|------|-----------------|
| Controller | HTTP, `@Valid`, status codes; sin lógica de negocio ni try/catch |
| Mapper (MapStruct) | Entity ↔ DTO; `aptoParaVinculoCanal` derivado de status |
| Service | unicidad, transiciones, campos permitidos, orquestación audit; `@Transactional` |
| Repository | Spring Data JPA |
| Entity | modelo persistido; no exponer por API |
| AuditService | append-only; invocado desde `UserService` |

Flujo: Request → Controller → Service → Repository/Entity; Mapper en bordes HTTP.

## Dependencias POM (mínimas)

Del parent Spring Boot 3 (sin hardcodear versiones BOM):

- `spring-boot-starter-web`, `validation`, `data-jpa`, `actuator`
- `postgresql` (runtime); `h2` (test)
- `lombok`, `mapstruct` + processors (`lombok-mapstruct-binding`)
- `springdoc-openapi-starter-webmvc-ui`
- `spring-boot-starter-test` (test)
- Plugin JaCoCo line coverage **≥ 80%** en `verify`

Sin WebFlux, sin Spring Security (JWT=NO), sin Kafka en H1.

## Estrategia de tests

JaCoCo ≥ 80%. Por capa:

| Área | Qué cubrir |
|------|------------|
| `UserService` (unit) | alta OK/duplicado; update campos; transiciones OK/invalidas; eligibility |
| `UserController` (MockMvc) | cada endpoint feliz + al menos un error con `$.code`, `$.details`, `$.timestamp` |
| `GlobalExceptionHandler` | VALIDATION_ERROR, NOT_FOUND, CONFLICT, INTERNAL_ERROR |
| Integración (opcional slice) | persistencia unicidad + audit en alta/cambio estado |
| `UsersServiceApplicationTest` | `contextLoads` |

Excluir de JaCoCo si hace falta: `*Application`, config pura, DTOs/records sin lógica.

## Fuera de alcance (arquitectura)

- Vinculación WhatsApp / verificación canal / credenciales sesión (H2/H3)
- Productos (cuentas, tarjetas, seguros)
- Eventos a servicios dependientes
- API versioning multi-versión
- Docker/CI (rol devops)
- OpenAPI export `delivery/` (rol documentador; `OpenApiConfig` sí en scaffold/feature)

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Compliance cambia campos mínimos | DTOs/entity aislados; contract provisional documentado |
| Duplicados vs core legacy | unicidad local estricta; sync legacy fuera de H1 |
| Baja lógica con vínculos activos en H3 | eligibility endpoint; H3 debe consultar antes de vincular |
| PII en logs/toString | regla arquitectura: no loguear documento/email completos |
| `project.yaml` incompleto (service/api) | TL completa yaml o tasks citan defaults de esta spec |

## Preguntas abiertas heredadas

Siguen abiertas en `usuarios/spec` (no bloquean este plan si se aceptan las decisiones provisionales de arriba):

1. Lista exacta Compliance de datos mínimos de alta
2. Regla de unicidad definitiva del banco
3. Identificadores de consulta definitivos
4. Lista cerrada de campos actualizables
5. Datos autorizados por actor (operaciones / soporte / otros servicios)
6. Transiciones desde `BAJA_LOGICA`
7. Notificación a dependientes en U4
8. Interpretación fina de JWT=NO (sin auth vs solo “no JWT de canal”) — **asumido: sin JWT en el servicio**

## Orden sugerido de implementación (para el TL)

1. Scaffold CREATE_FROM_ZERO + error contract + OpenAPI config
2. Entidades/repos `user` + `audit` + `AuditService`
3. Alta + consulta (`POST`/`GET`)
4. Actualización + cambio de estado + eligibility
5. Tests de calidad / cobertura
6. DevOps + documentación (roles correspondientes)
