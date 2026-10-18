---
title: Arquitectura — wa-users-service (H1 Usuarios)
status: borrador
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-10-18T20:01:35.299Z
---

# Arquitectura — wa-users-service (H1 Usuarios)

Plan técnico para el tech-lead. **Sin código.** Fuente funcional: `usuarios/spec` (aprobada). Identidad Java desde `project.yaml`.

## Identidad y stack

| Parámetro | Valor | Fuente |
|-----------|-------|--------|
| Artifact / nombre | `wa-users-service` | `project.yaml` → `project.name` |
| groupId | `com.bank` | `project.yaml` → `java.group_id` |
| Base package | `com.bank.users` | `project.yaml` → `java.base_package` |
| Java | 21 | default scaffold (`java.version` ausente) |
| Spring Boot | 3.x (última estable línea 3) | default scaffold |
| Persistencia | JPA + PostgreSQL | default `service.database` ausente |
| Puerto | 8080 | default `service.port` ausente |
| API versioning | **desactivado** — todo bajo `/api/v1` sin paquetes `api/vN` | `api.versioning` ausente |
| Optimización nativa | no | `service.optimization` ausente |
| Modo | **CREATE_FROM_ZERO** (no hay `pom.xml`) | repo |

Stack obligatorio: Web, Validation, Actuator, JPA, Lombok, MapStruct, springdoc, JaCoCo ≥ 80%, Maven Wrapper (`./mvnw`).

## Módulos de dominio

| Módulo | Responsabilidad |
|--------|-----------------|
| `user` | Alta, consulta, actualización de campos permitidos, cambio de estado, aptitud vía estado |
| `audit` | Registro de altas, cambios de estado y actualizaciones relevantes (sin API pública en H1) |

Transversales: `config/` (OpenAPI), `exception/` (contrato de error global).

## Árbol de paquetes (OBLIGATORIO — subpaquetes por capa)

```
com.bank.users/
├── UsersServiceApplication.java
├── config/
│   └── OpenApiConfig.java
├── exception/
│   ├── ErrorResponse.java          # record
│   ├── FieldError.java             # record
│   ├── ErrorCode.java              # enum
│   ├── NotFoundException.java
│   ├── ConflictException.java
│   └── GlobalExceptionHandler.java
├── user/
│   ├── controller/
│   │   └── UserController.java
│   ├── service/
│   │   └── UserService.java
│   ├── repository/
│   │   └── UserRepository.java
│   ├── mapper/
│   │   └── UserMapper.java         # MapStruct
│   ├── entity/
│   │   ├── User.java
│   │   ├── UserStatus.java         # enum: ACTIVE, SUSPENDED, LOGICALLY_DELETED
│   │   └── DocumentType.java       # enum inicial (ver decisiones)
│   ├── dto/
│   │   ├── CreateUserRequest.java
│   │   ├── UpdateUserRequest.java
│   │   ├── ChangeStatusRequest.java
│   │   └── UserResponse.java
│   └── exception/
│       ├── UserNotFoundException.java
│       ├── DuplicateUserException.java
│       ├── InvalidStatusTransitionException.java
│       └── NonUpdatableFieldException.java
└── audit/
    ├── service/
    │   └── AuditService.java
    ├── repository/
    │   └── AuditLogRepository.java
    └── entity/
        ├── AuditLog.java
        └── AuditAction.java        # enum: USER_CREATED, USER_UPDATED, STATUS_CHANGED
```

Paths de archivo (src/main/java):

- `com/bank/users/UsersServiceApplication.java`
- `com/bank/users/config/OpenApiConfig.java`
- `com/bank/users/exception/*`
- `com/bank/users/user/controller/UserController.java`
- `com/bank/users/user/service/UserService.java`
- `com/bank/users/user/repository/UserRepository.java`
- `com/bank/users/user/mapper/UserMapper.java`
- `com/bank/users/user/entity/{User,UserStatus,DocumentType}.java`
- `com/bank/users/user/dto/{CreateUserRequest,UpdateUserRequest,ChangeStatusRequest,UserResponse}.java`
- `com/bank/users/user/exception/{UserNotFoundException,DuplicateUserException,InvalidStatusTransitionException,NonUpdatableFieldException}.java`
- `com/bank/users/audit/service/AuditService.java`
- `com/bank/users/audit/repository/AuditLogRepository.java`
- `com/bank/users/audit/entity/{AuditLog,AuditAction}.java`

Scaffold adicional (CREATE_FROM_ZERO): `pom.xml`, `mvnw`/`mvnw.cmd`/`.mvn/wrapper/`, `src/main/resources/application.yml`, `src/test/resources/application.yml` (H2), `.gitignore`, `README.md` inicial, test `UsersServiceApplicationTest` (`contextLoads`).

**Prohibido** paquete plano (`user.UserController` junto a `user.UserService`).

## Modelo de dominio

### User (entity)

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | UUID | PK; identificador interno asignado en el alta |
| `documentType` | `DocumentType` | Parte de unicidad |
| `documentNumber` | String | Parte de unicidad; índice único compuesto con tipo |
| `displayName` | String | Nombre para tratamiento |
| `email` | String, nullable | Contacto |
| `phone` | String, nullable | Contacto |
| `birthDate` | LocalDate | Fecha de nacimiento |
| `status` | `UserStatus` | `ACTIVE` / `SUSPENDED` / `LOGICALLY_DELETED` |
| `preferences` | JSON / Map | Preferencias generales no sensibles (catálogo abierto) |
| `createdAt` / `updatedAt` | Instant | Auditoría técnica de fila |

Restricciones:

- Unicidad BD: `(document_type, document_number)` unique.
- Alta: al menos uno de `email` o `phone` obligatorio (validación de clase en request).
- `toString()` / logs: **no** loguear documento completo ni PII innecesaria.

### UserStatus — transiciones permitidas (H1)

| Desde | Hacia | Caso |
|-------|-------|------|
| — | ACTIVE | Alta |
| ACTIVE | SUSPENDED | Suspender |
| SUSPENDED | ACTIVE | Reactivar |
| ACTIVE | LOGICALLY_DELETED | Baja lógica |
| SUSPENDED | LOGICALLY_DELETED | Baja lógica |

**Decisión de arquitectura (pregunta abierta de la spec):** `LOGICALLY_DELETED` es **terminal** en H1 — no se permite reactivar a `ACTIVE`. Si el banco decide lo contrario, se agrega la transición sin romper el contrato HTTP (mismo endpoint de estado).

**Aptitud para vínculo de canal:** un usuario es apto solo si `status == ACTIVE`. La consulta (`UserResponse.status` + opcional `channelLinkEligible: boolean`) lo expone; no hay endpoint separado en H1.

### DocumentType (enum inicial)

Valores iniciales: `DNI`, `PASSPORT`, `LC`, `LE`, `CUIT`. Catálogo definitivo pendiente del banco (pregunta abierta); ampliar el enum no rompe clientes si solo se agregan valores.

### AuditLog (entity)

| Campo | Tipo |
|-------|------|
| `id` | UUID |
| `userId` | UUID |
| `action` | `AuditAction` |
| `result` | String / enum (`SUCCESS` / `FAILURE` si aplica) |
| `detail` | String corto (estado resultante, campos tocados) — sin PII completa |
| `occurredAt` | Instant |

Sin controller en H1: solo escritura desde `UserService` vía `AuditService`.

## Contratos HTTP (`/api/v1`)

Base: `@RequestMapping("/api/v1/users")` en `UserController`. Sin paquetes versionados.

### POST `/api/v1/users` — Alta

- **Request** `CreateUserRequest`: `documentType`, `documentNumber`, `displayName`, `email?`, `phone?`, `birthDate`; validación: todos obligatorios salvo email/phone; **al menos uno** de email/phone.
- **Response** `201` + `UserResponse`.
- **Errores:**

| HTTP | code | Caso |
|------|------|------|
| 400 | `VALIDATION_ERROR` | Datos faltantes o formato inválido (`@Valid`) |
| 409 | `CONFLICT` | Duplicado tipo+número (`DuplicateUserException`) |
| 500 | `INTERNAL_ERROR` | Inesperado |

### GET `/api/v1/users/{id}` — Consulta por ID interno

- **Response** `200` + `UserResponse` (solo datos autorizados: id, documentType, documentNumber, displayName, email, phone, birthDate, status, preferences, `channelLinkEligible`).
- **Errores:**

| HTTP | code | Caso |
|------|------|------|
| 404 | `NOT_FOUND` | No existe (`UserNotFoundException`) |
| 400 | `BAD_REQUEST` | UUID mal formado |

### GET `/api/v1/users?documentType={}&documentNumber={}` — Consulta por documento

- Ambos query params obligatorios.
- **Response** `200` + `UserResponse`.
- **Errores:**

| HTTP | code | Caso |
|------|------|------|
| 400 | `BAD_REQUEST` / `VALIDATION_ERROR` | Params faltantes o tipo inválido |
| 404 | `NOT_FOUND` | No existe |

### PATCH `/api/v1/users/{id}` — Actualización de datos permitidos

- **Request** `UpdateUserRequest`: solo `displayName?`, `email?`, `phone?`, `preferences?` (parcial). Tras el patch debe seguir valiendo “al menos un contacto” si se tocan email/phone.
- Campos legales / no autorizados **no existen** en el DTO. Si el cliente envía propiedades desconocidas → `400` `BAD_REQUEST` / mensaje de campo no actualizable (`NonUpdatableFieldException` o `HttpMessageNotReadableException` mapeado). Jackson: `fail-on-unknown-properties` en este endpoint/DTO.
- **Response** `200` + `UserResponse`.
- **Errores:**

| HTTP | code | Caso |
|------|------|------|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` | Valor inválido o campo no permitido |
| 404 | `NOT_FOUND` | Usuario inexistente |
| 409 | `CONFLICT` | Estado que impida update si se define política (opcional H1: permitir update en cualquier estado salvo que se decida lo contrario — **permitir** en ACTIVE/SUSPENDED; rechazar en LOGICALLY_DELETED con `409 CONFLICT`) |

### PATCH `/api/v1/users/{id}/status` — Cambio de estado

- **Request** `ChangeStatusRequest`: `status` (destino).
- **Response** `200` + `UserResponse`.
- **Errores:**

| HTTP | code | Caso |
|------|------|------|
| 400 | `VALIDATION_ERROR` | status inválido / ausente |
| 404 | `NOT_FOUND` | Usuario inexistente |
| 409 | `CONFLICT` | Transición no permitida (`InvalidStatusTransitionException`) |

### UserResponse (contrato de salida)

Campos: `id`, `documentType`, `documentNumber`, `displayName`, `email`, `phone`, `birthDate`, `status`, `preferences`, `channelLinkEligible` (derivado: `status == ACTIVE`).

Sin domicilio legal, estado civil ni datos de verificación de canal.

## Capas y flujo

```
Request → UserController (@Valid) → UserService (@Transactional)
              ↓                         ↓
         UserMapper              UserRepository / AuditService
              ↓                         ↓
         UserResponse                 User / AuditLog
```

- Controller: HTTP + validación; **sin** lógica de negocio ni `try/catch` de dominio.
- Service: reglas (unicidad, contacto, transiciones, aptitud), llama a `AuditService` en alta / update / status.
- Mapper MapStruct: Entity ↔ DTO; **no** mapeo manual en controller/service.
- Repository: Spring Data JPA; `existsByDocumentTypeAndDocumentNumber`, `findByDocumentTypeAndDocumentNumber`.

## Contrato de errores

JSON único (`error-contract`): `code`, `message`, `details` (nunca null), `timestamp` UTC.

Excepciones de dominio:

| Excepción | Extiende | HTTP / code |
|-----------|----------|-------------|
| `UserNotFoundException` | `NotFoundException` | 404 / `NOT_FOUND` |
| `DuplicateUserException` | `ConflictException` | 409 / `CONFLICT` |
| `InvalidStatusTransitionException` | `ConflictException` | 409 / `CONFLICT` |
| `NonUpdatableFieldException` | Runtime → handler `BAD_REQUEST` o base dedicada | 400 / `BAD_REQUEST` |

Config: `server.error.include-stacktrace: never`, `include-message: never`.

## Persistencia y configuración

- PostgreSQL vía env (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); tests con H2 en memoria.
- `spring.jpa.open-in-view: false`.
- Schema: `ddl-auto=update` en dev; DevOps puede migrar a Flyway después (fuera de este plan si no hay tarea).
- Actuator: `health`, `info`.

## Estrategia de tests (JaCoCo ≥ 80%)

| Área | Qué cubrir |
|------|------------|
| `UserController` (MockMvc) | Alta 201; validación 400+body; duplicado 409; GET por id/documento 200/404; PATCH datos 200 + campo desconocido 400; PATCH status feliz + transición inválida 409 |
| `UserService` (unit) | Unicidad, contacto mínimo, máquina de estados, `channelLinkEligible`, auditoría invocada |
| `GlobalExceptionHandler` | status + `$.code` + `$.details` + `$.timestamp` |
| Integración | `@SpringBootTest` + H2: flujo alta → consulta → update → suspender → reactivar → baja |
| Scaffold | `contextLoads` |

Cada endpoint con al menos un test de error que verifique el cuerpo del contrato.

## Archivos scaffold / POM (deps relevantes)

- Starters: web, validation, actuator, data-jpa; runtime PostgreSQL; test H2.
- Lombok + MapStruct + lombok-mapstruct-binding (annotation processors).
- springdoc-openapi-starter-webmvc-ui.
- jacoco-maven-plugin check 0.80.
- Sin Spring Cloud / messaging en H1 (microservicio REST autocontenido; otros servicios consumen HTTP).

## Orden de implementación sugerido (para el tech-lead)

1. **Bootstrap / scaffold** — pom, wrapper, application, `exception/`, Application, contextLoads.
2. **Módulo user — entidad + repo + alta + consulta**.
3. **Actualización de datos permitidos + rechazo de campos no permitidos**.
4. **Cambio de estado + aptitud (`channelLinkEligible`)**.
5. **Módulo audit** cableado en los flujos anteriores.
6. **OpenAPI config** (mínimo; documentador completa ejemplos).
7. DevOps / docs / PR — roles posteriores.

## Decisiones y riesgos

| Tema | Decisión | Riesgo |
|------|----------|--------|
| Baja lógica → activo | No permitido en H1 | Si el banco lo habilita, solo cambia la tabla de transiciones |
| Preferencias | Mapa JSON opaco | Catálogo concreto pendiente; no bloquea API |
| Tipos de documento | Enum inicial ampliable | Catálogo banco puede pedir renombres → posible v2 |
| Consulta aptitud | Campo en `UserResponse` | Suficiente para H3 sin endpoint extra |
| Notificación a otros servicios | Fuera de alcance H1 | Sin eventos/Kafka en este plan |
| AuthN/AuthZ de la API | No pedido en H1 | Asumir red interna / gateway externo; no Spring Security en scaffold salvo que persona lo pida |
| `project.yaml` sin `service.*` / `api.versioning` | Defaults del scaffold | Tech-lead puede completar yaml sin cambiar el diseño |

## Fuera de alcance (confirmado)

WhatsApp / vínculo de canal (H3), sesiones (H2), productos, cambio de datos legales sensibles, API de consulta de auditoría, versionado multi-versión, mensajería asíncrona.

## Trazabilidad requisito → diseño

| Requisito (`usuarios/spec`) | Diseño |
|-----------------------------|--------|
| Alta con datos mínimos | `POST /api/v1/users` + `CreateUserRequest` |
| Unicidad por documento | Unique constraint + `DuplicateUserException` |
| Consulta por identificador | `GET .../{id}` y `GET ...?documentType&documentNumber` |
| Actualización datos permitidos | `PATCH .../{id}` + DTO restringido |
| Cambio de estado operativo | `PATCH .../{id}/status` + máquina de estados |
| Aptitud vínculo canal | `channelLinkEligible` en response |
| Auditoría | módulo `audit` + llamadas desde `UserService` |
