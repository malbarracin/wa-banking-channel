---
title: wa-users-service — Arquitectura
status: borrador
source: specs/usuarios/spec.md, specs/fuentes/origen.md
updated: 2026-11-27T21:00:25.474Z
---

# wa-users-service — Arquitectura

## Datos del componente

| Dato | Valor | Fuente |
|------|-------|--------|
| Carpeta | `services/wa-users-service` | Roadmap Notion |
| Grupo Maven | `com.bcra` | Roadmap Notion |
| Paquete base | `com.bcra.users.ws` | Roadmap Notion |
| Artefacto | `wa-users-service` | Roadmap Notion |
| Puerto (contenedor) | `8080` | Roadmap Notion |
| Java | 21 | Stack del proyecto |
| Spring Boot | 3.x (última estable) | Stack del proyecto |
| API versioning | desactivado → todo bajo `/api/v1` | `project.yaml` sin `api.versioning.enabled` |
| Base de datos | PostgreSQL | Stack del proyecto |

---

## Estructura de paquetes

```
services/wa-users-service/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── java/com/bcra/users/ws/
    │   │   ├── WaUsersServiceApplication.java
    │   │   ├── config/
    │   │   │   └── OpenApiConfig.java
    │   │   ├── exception/
    │   │   │   ├── ErrorCode.java               (enum: VALIDATION_ERROR, BAD_REQUEST, NOT_FOUND, CONFLICT, INTERNAL_ERROR)
    │   │   │   ├── ErrorResponse.java           (record: code, message, details, timestamp)
    │   │   │   ├── FieldError.java              (record: field, message)
    │   │   │   ├── GlobalExceptionHandler.java  (@RestControllerAdvice)
    │   │   │   ├── NotFoundException.java       (abstracta, extends RuntimeException)
    │   │   │   └── ConflictException.java       (abstracta, extends RuntimeException)
    │   │   ├── user/
    │   │   │   ├── controller/
    │   │   │   │   └── UserController.java
    │   │   │   ├── service/
    │   │   │   │   └── UserService.java
    │   │   │   ├── repository/
    │   │   │   │   └── UserRepository.java
    │   │   │   ├── mapper/
    │   │   │   │   └── UserMapper.java          (MapStruct)
    │   │   │   ├── entity/
    │   │   │   │   └── User.java
    │   │   │   ├── dto/
    │   │   │   │   ├── CreateUserRequest.java
    │   │   │   │   ├── UpdateUserRequest.java
    │   │   │   │   ├── ChangeUserStatusRequest.java
    │   │   │   │   └── UserResponse.java
    │   │   │   └── exception/
    │   │   │       ├── UserNotFoundException.java      (extends NotFoundException)
    │   │   │       └── DuplicateUserException.java     (extends ConflictException)
    │   │   │       └── InvalidStatusTransitionException.java (extends ConflictException)
    │   │   └── audit/
    │   │       ├── entity/
    │   │       │   └── AuditEntry.java
    │   │       ├── repository/
    │   │       │   └── AuditRepository.java
    │   │       ├── service/
    │   │       │   └── AuditService.java
    │   │       └── dto/
    │   │           └── AuditEntryResponse.java
    │   └── resources/
    │       └── application.yml
    └── test/
        ├── java/com/bcra/users/ws/
        │   ├── WaUsersServiceApplicationTest.java
        │   ├── user/
        │   │   └── controller/
        │   │       └── UserControllerTest.java
        │   └── audit/
        │       └── service/
        │           └── AuditServiceTest.java
        └── resources/
            └── application.yml
```

---

## Módulos de dominio

### Módulo `user`

Gestiona el ciclo de vida del cliente bancario: alta, consulta, actualización de datos permitidos y cambio de estado operativo.

#### Entidad: `User`

| Campo | Tipo Java | Columna | Notas |
|-------|-----------|---------|-------|
| `id` | `UUID` | `id` | PK, generado |
| `externalId` | `String` | `external_id` | identificador de negocio, único |
| `uniquenessField` | `String` | `uniqueness_field` | valor del campo de unicidad del banco (configurable) |
| `displayName` | `String` | `display_name` | nombre de tratamiento |
| `contactEmail` | `String` | `contact_email` | dato de contacto no sensible |
| `contactPhone` | `String` | `contact_phone` | dato de contacto no sensible |
| `status` | `UserStatus` (enum) | `status` | `ACTIVE`, `SUSPENDED`, `INACTIVE` |
| `createdAt` | `Instant` | `created_at` | timestamp del alta |
| `updatedAt` | `Instant` | `updated_at` | timestamp de la última modificación |

`UserStatus` enum: `ACTIVE`, `SUSPENDED`, `INACTIVE` (baja lógica).

La regla de unicidad se evalúa sobre `uniquenessField`; su valor concreto lo configura el banco. La entidad expone el campo para que el banco pueda cambiar la semántica sin tocar el esquema.

#### Entidad: `AuditEntry` (módulo `audit`)

| Campo | Tipo Java | Columna | Notas |
|-------|-----------|---------|-------|
| `id` | `UUID` | `id` | PK |
| `userId` | `UUID` | `user_id` | FK a `User` |
| `action` | `AuditAction` (enum) | `action` | `ALTA`, `MODIFICACION`, `CAMBIO_ESTADO` |
| `result` | `String` | `result` | `OK`, `ERROR` |
| `previousStatus` | `UserStatus` | `previous_status` | nullable, solo en `CAMBIO_ESTADO` |
| `newStatus` | `UserStatus` | `new_status` | nullable, solo en `CAMBIO_ESTADO` |
| `changedFields` | `String` | `changed_fields` | nullable, campos modificados en `MODIFICACION` |
| `actor` | `String` | `actor` | identificador del actor que realizó la operación |
| `occurredAt` | `Instant` | `occurred_at` | timestamp de la operación |

---

## Endpoints

Todos bajo `/api/v1/users` (sin versionado de paquetes; `api.versioning` no activado).

### POST /api/v1/users — Alta de cliente

**Request body** (`CreateUserRequest`):

```json
{
  "uniquenessValue": "20-12345678-9",
  "displayName": "Juan Pérez",
  "contactEmail": "juan@example.com",
  "contactPhone": "+5491112345678"
}
```

> Los campos obligatorios concretos son configurables (`bank.users.required-fields`). Bean Validation los marca con `@NotBlank` condicionalmente o el servicio los valida contra la configuración.

**Response 201** (`UserResponse`):

```json
{
  "id": "uuid",
  "externalId": "uuid-o-string-de-negocio",
  "uniquenessValue": "20-12345678-9",
  "displayName": "Juan Pérez",
  "contactEmail": "juan@example.com",
  "contactPhone": "+5491112345678",
  "status": "ACTIVE",
  "createdAt": "2026-11-27T12:00:00Z"
}
```

**Errores:**

| HTTP | `code` | Caso |
|------|--------|------|
| 400 | `VALIDATION_ERROR` | Campos obligatorios faltantes o con formato inválido |
| 409 | `CONFLICT` | Ya existe un cliente con ese valor de unicidad (`DuplicateUserException`) |
| 500 | `INTERNAL_ERROR` | Error inesperado |

---

### GET /api/v1/users/{id} — Consulta por identificador

`{id}` es el `id` interno (UUID). El servicio devuelve solo los campos autorizados por la política de privacidad (configurable).

**Response 200** (`UserResponse`): misma estructura que el alta.

**Errores:**

| HTTP | `code` | Caso |
|------|--------|------|
| 404 | `NOT_FOUND` | No existe el cliente con ese `id` (`UserNotFoundException`) |
| 500 | `INTERNAL_ERROR` | Error inesperado |

---

### PATCH /api/v1/users/{id} — Actualización de datos permitidos

**Request body** (`UpdateUserRequest`):

```json
{
  "displayName": "Juan P.",
  "contactEmail": "nuevo@example.com",
  "contactPhone": "+5491198765432"
}
```

> Solo los campos de la lista configurable (`bank.users.updatable-fields`) son procesados; los demás se ignoran o se rechazan con `BAD_REQUEST`.

**Response 200** (`UserResponse`): el usuario actualizado.

**Errores:**

| HTTP | `code` | Caso |
|------|--------|------|
| 400 | `BAD_REQUEST` | Se intenta modificar un campo no permitido |
| 404 | `NOT_FOUND` | No existe el cliente (`UserNotFoundException`) |
| 500 | `INTERNAL_ERROR` | Error inesperado |

---

### PATCH /api/v1/users/{id}/status — Cambio de estado operativo

**Request body** (`ChangeUserStatusRequest`):

```json
{
  "status": "SUSPENDED",
  "actor": "operador-123"
}
```

**Response 200** (`UserResponse`): el usuario con el nuevo estado.

**Errores:**

| HTTP | `code` | Caso |
|------|--------|------|
| 400 | `BAD_REQUEST` | Valor de `status` inválido (no es uno de los enum) |
| 404 | `NOT_FOUND` | No existe el cliente (`UserNotFoundException`) |
| 409 | `CONFLICT` | La transición de estado no está permitida (`InvalidStatusTransitionException`) |
| 500 | `INTERNAL_ERROR` | Error inesperado |

---

## Mappers MapStruct

### `UserMapper` (`com.bcra.users.ws.user.mapper`)

| Método | De | A | Notas |
|--------|----|---|-------|
| `toEntity(CreateUserRequest)` | `CreateUserRequest` | `User` | El `id`, `createdAt`, `status` los asigna el service |
| `toResponse(User)` | `User` | `UserResponse` | Aplica la política de privacidad (filtrado de campos): el mapper proyecta solo los campos autorizados |
| `updateEntity(UpdateUserRequest, @MappingTarget User)` | `UpdateUserRequest` | `User` (mutado) | Solo los campos permitidos; el service valida cuáles aplican |

---

## Contrato de errores

Todo el servicio usa el JSON único de error del contrato (skill `error-contract`):

```json
{
  "code": "NOT_FOUND",
  "message": "No existe el usuario solicitado",
  "details": [],
  "timestamp": "2026-11-27T12:00:00Z"
}
```

Excepciones de dominio en `user/exception/`:
- `UserNotFoundException extends NotFoundException` → HTTP 404, `NOT_FOUND`
- `DuplicateUserException extends ConflictException` → HTTP 409, `CONFLICT`
- `InvalidStatusTransitionException extends ConflictException` → HTTP 409, `CONFLICT`

`GlobalExceptionHandler` en `exception/` (compartido por toda la API).

---

## Dependencias POM (a incorporar en la plantilla)

| Dependencia | Uso |
|-------------|-----|
| `spring-boot-starter-web` | API REST |
| `spring-boot-starter-data-jpa` | Persistencia |
| `spring-boot-starter-validation` | Bean Validation |
| `spring-boot-starter-actuator` | Health, info |
| `org.postgresql:postgresql` | Driver PostgreSQL |
| `org.mapstruct:mapstruct` | Mapeo Entity ↔ DTO |
| `org.projectlombok:lombok` | Boilerplate |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | Swagger UI |
| `org.flywaydb:flyway-core` | Migraciones de esquema |
| `org.flywaydb:flyway-database-postgresql` | Soporte Flyway para PG |
| `com.h2database:h2` (test) | BD en memoria para tests |
| `org.springframework.boot:spring-boot-starter-test` (test) | JUnit 5, AssertJ, MockMvc |

---

## Estrategia de tests

**Cobertura mínima: 80% líneas** (JaCoCo, gate en `./mvnw verify`).

| Test | Qué cubre | Estrategia |
|------|-----------|------------|
| `WaUsersServiceApplicationTest` | Context loads | `@SpringBootTest` con H2 |
| `UserControllerTest` | Todos los endpoints: happy path y cada error | `@WebMvcTest` + Mockito (service mockeado) |
| `UserServiceTest` | Lógica de negocio: alta, unicidad, actualización, cambio de estado | JUnit 5 puro + Mockito (repository mockeado) |
| `AuditServiceTest` | Registro de auditoría tras cada operación | JUnit 5 + Mockito |
| `UserRepositoryTest` | Consultas JPA personalizadas | `@DataJpaTest` con H2 |

**Casos de error obligatorios en `UserControllerTest`:**
- POST con campos faltantes → 400 `VALIDATION_ERROR`, `$.details` no vacío
- POST con duplicado → 409 `CONFLICT`, `$.code == "CONFLICT"`
- GET con id inexistente → 404 `NOT_FOUND`
- PATCH /status con transición inválida → 409 `CONFLICT`
- Verificar `$.code`, `$.details` (array), `$.timestamp` en cada error

---

## Riesgos y decisiones de diseño

| Riesgo / Decisión | Mitigación |
|-------------------|------------|
| Los campos mínimos del alta son configurables | `UserService` lee `bank.users.required-fields` de `application.yml` al validar; el test usa un valor por defecto razonable (`uniquenessValue`) |
| La regla de unicidad es configurable | Se evalúa sobre `uniquenessField`; el schema tiene índice único en esa columna; el valor lo popula el service desde el campo indicado por configuración |
| Las transiciones de estado son configurables | `UserService` implementa un mapa `allowedTransitions` cargado desde `bank.users.allowed-transitions` en `application.yml` |
| La política de privacidad es configurable | `UserMapper.toResponse` puede filtrar campos según `bank.users.public-fields`; valor por defecto: todos excepto datos sensibles |
| Auditoría en la misma transacción | `AuditService` corre dentro de `@Transactional` del `UserService` para garantizar consistencia |
| Sin autenticación en este hito | El campo `actor` en `ChangeUserStatusRequest` lo provee el llamador; H2 (Acceso y Sesión) lo propaga |

---

## Configuración relevante (`application.yml`)

```yaml
bank:
  users:
    required-fields: uniquenessValue,displayName   # campos mínimos del alta (configurable)
    uniqueness-field: uniquenessValue              # campo que define unicidad (configurable)
    updatable-fields: displayName,contactEmail,contactPhone  # campos actualizables (configurable)
    public-fields: id,externalId,displayName,contactEmail,contactPhone,status,createdAt  # política de privacidad (configurable)
    allowed-transitions:                           # transiciones de estado válidas (configurable)
      ACTIVE: [SUSPENDED, INACTIVE]
      SUSPENDED: [ACTIVE, INACTIVE]
      INACTIVE: []                                 # baja lógica: sin reactivación por defecto
```

Estos valores son **por defecto**; el banco los sobreescribe en cada entorno sin tocar código.

---

## Contrato que entrega este componente

- `shared/contracts/wa-users-service/openapi.yaml` — API como quedó (endpoints, esquemas, formato de error)
- `shared/contracts/wa-users-service/handoff.md` — qué expone, cómo se levanta, variables de entorno sin valores, qué exige para autorizar

Ambos los genera el rol documentador al cierre; la arquitectura los define acá como contrato de forma.
