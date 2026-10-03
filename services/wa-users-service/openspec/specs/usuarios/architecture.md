# Arquitectura técnica — Dominio `usuarios`

> Rol: arquitecto · Run: 20261003-fa8d · Fecha: 2026-10-03  
> Base package: `com.bank.users` (from project.yaml `java.base_package`)  
> Maven groupId: `com.bank` (from project.yaml `java.group_id`)

---

## 1. Estructura de paquetes

```
com.bank.users/
├── UsersServiceApplication.java
├── user/
│   ├── controller/
│   │   └── UserController.java
│   ├── service/
│   │   └── UserService.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   └── AuditEventRepository.java
│   ├── mapper/
│   │   └── UserMapper.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── UserStatus.java          (enum: ACTIVE, SUSPENDED, INACTIVE)
│   │   ├── DocumentType.java        (enum: DNI, PASAPORTE, etc.)
│   │   └── AuditEvent.java
│   └── dto/
│       ├── CreateUserRequest.java
│       ├── UpdateUserRequest.java
│       ├── ChangeStatusRequest.java
│       ├── UserResponse.java
│       └── AuditEventType.java      (enum: ALTA, CAMBIO_ESTADO, MODIFICACION_DATOS, ALTA_RECHAZADA)
├── config/
│   └── OpenApiConfig.java
└── exception/
    ├── GlobalExceptionHandler.java
    ├── UserNotFoundException.java
    ├── UserAlreadyExistsException.java
    ├── InvalidStateTransitionException.java
    └── ErrorResponse.java
```

Rutas de archivo (src/main/java):

```
src/main/java/com/bank/users/UsersServiceApplication.java
src/main/java/com/bank/users/user/controller/UserController.java
src/main/java/com/bank/users/user/service/UserService.java
src/main/java/com/bank/users/user/repository/UserRepository.java
src/main/java/com/bank/users/user/repository/AuditEventRepository.java
src/main/java/com/bank/users/user/mapper/UserMapper.java
src/main/java/com/bank/users/user/entity/User.java
src/main/java/com/bank/users/user/entity/UserStatus.java
src/main/java/com/bank/users/user/entity/DocumentType.java
src/main/java/com/bank/users/user/entity/AuditEvent.java
src/main/java/com/bank/users/user/dto/CreateUserRequest.java
src/main/java/com/bank/users/user/dto/UpdateUserRequest.java
src/main/java/com/bank/users/user/dto/ChangeStatusRequest.java
src/main/java/com/bank/users/user/dto/UserResponse.java
src/main/java/com/bank/users/user/dto/AuditEventType.java
src/main/java/com/bank/users/config/OpenApiConfig.java
src/main/java/com/bank/users/exception/GlobalExceptionHandler.java
src/main/java/com/bank/users/exception/UserNotFoundException.java
src/main/java/com/bank/users/exception/UserAlreadyExistsException.java
src/main/java/com/bank/users/exception/InvalidStateTransitionException.java
src/main/java/com/bank/users/exception/ErrorResponse.java
```

---

## 2. Entidades JPA

### `User` — entidad principal

| Campo | Tipo Java | Columna DB | Notas |
|-------|-----------|------------|-------|
| `id` | `Long` | `id` | PK, auto-generado |
| `documentType` | `DocumentType` (enum) | `document_type` | `@Enumerated(STRING)` |
| `documentNumber` | `String` | `document_number` | Not null; unicidad con documentType |
| `firstName` | `String` | `first_name` | Nombre legal |
| `lastName` | `String` | `last_name` | Apellido legal |
| `displayName` | `String` | `display_name` | Nombre de tratamiento (actualizable) |
| `email` | `String` | `email` | Actualizable |
| `birthDate` | `LocalDate` | `birth_date` | |
| `status` | `UserStatus` (enum) | `status` | `@Enumerated(STRING)` |
| `createdAt` | `Instant` | `created_at` | Set en alta, inmutable |
| `updatedAt` | `Instant` | `updated_at` | Actualizado en cada cambio |

Índice único: `(document_type, document_number)`.

Lombok: `@Getter` `@Setter` `@NoArgsConstructor` + equals/hashCode sobre `id`. **No usar `@Data`**.

### `AuditEvent` — auditoría

| Campo | Tipo Java | Columna DB | Notas |
|-------|-----------|------------|-------|
| `id` | `Long` | `id` | PK, auto-generado |
| `userId` | `Long` | `user_id` | FK lógica (no constraint FK para evitar bloqueo en baja) |
| `eventType` | `AuditEventType` (enum) | `event_type` | `@Enumerated(STRING)` |
| `previousStatus` | `UserStatus` (enum, nullable) | `previous_status` | Sólo en CAMBIO_ESTADO |
| `newStatus` | `UserStatus` (enum, nullable) | `new_status` | Sólo en CAMBIO_ESTADO |
| `modifiedFields` | `String` | `modified_fields` | Campos modificados (CSV o JSON) |
| `result` | `String` | `result` | "OK" / "REJECTED" |
| `reason` | `String` | `reason` | Motivo del rechazo (nullable) |
| `occurredAt` | `Instant` | `occurred_at` | Timestamp del evento |

### Enums

```java
// UserStatus
ACTIVE, SUSPENDED, INACTIVE

// DocumentType
DNI, PASAPORTE, CUIT, CUIL
// (ampliable — solo valores conocidos en esta iteración)

// AuditEventType
ALTA, CAMBIO_ESTADO, MODIFICACION_DATOS, ALTA_RECHAZADA
```

Reglas de transición de estado (a implementar en `UserService`):

| Estado origen | Destino permitido |
|---------------|-------------------|
| `ACTIVE` | `SUSPENDED`, `INACTIVE` |
| `SUSPENDED` | `ACTIVE`, `INACTIVE` |
| `INACTIVE` | — (terminal, ninguno) |

---

## 3. Contratos de API

Base path: `/api/v1/users`

### POST `/api/v1/users` — Alta de usuario

**Request** (`CreateUserRequest`):
```json
{
  "documentType": "DNI",
  "documentNumber": "30123456",
  "firstName": "Juan",
  "lastName": "Pérez",
  "displayName": "Juancho",
  "email": "juan.perez@example.com",
  "birthDate": "1990-05-15"
}
```

Campos obligatorios: `documentType`, `documentNumber`, `firstName`, `lastName`, `birthDate`.  
Campos opcionales: `displayName`, `email`.

**Response 201** (`UserResponse`): usuario creado con estado `ACTIVE`.  
**Response 400**: validación fallida (`VALIDATION_ERROR`).  
**Response 409**: documento ya registrado (`CONFLICT`).

---

### GET `/api/v1/users/{id}` — Consulta por id interno

**Response 200** (`UserResponse`).  
**Response 404**: usuario no encontrado (`NOT_FOUND`).

---

### GET `/api/v1/users?documentType={type}&documentNumber={number}` — Consulta por documento

**Response 200** (`UserResponse`).  
**Response 404**: usuario no encontrado.

---

### PATCH `/api/v1/users/{id}` — Actualización de datos permitidos

**Request** (`UpdateUserRequest`):
```json
{
  "displayName": "Juancho actualizado",
  "email": "nuevo@example.com"
}
```

Campos permitidos: `displayName`, `email`. Cualquier otro campo en el payload → 400 `BAD_REQUEST`.  
Todos los campos son opcionales (al menos uno debe estar presente).

**Response 200** (`UserResponse`): datos actualizados.  
**Response 400**: payload inválido o campo no permitido.  
**Response 404**: usuario no encontrado.

---

### PATCH `/api/v1/users/{id}/status` — Cambio de estado

**Request** (`ChangeStatusRequest`):
```json
{
  "status": "SUSPENDED",
  "reason": "Solicitud del cliente"
}
```

`reason` opcional.

**Response 200** (`UserResponse`): usuario con nuevo estado.  
**Response 400**: transición no permitida (`BAD_REQUEST`).  
**Response 404**: usuario no encontrado.

---

### GET `/api/v1/users/{id}/eligible` — Verificación de elegibilidad de vínculo

Usado por servicios externos (ej. H3 Canal WhatsApp) para saber si el usuario puede iniciar un nuevo vínculo.

**Response 200**:
```json
{
  "userId": 1,
  "eligible": true
}
```

`eligible = true` solo si `status == ACTIVE`.  
**Response 404**: usuario no encontrado.

---

## 4. DTOs

### `UserResponse`

```json
{
  "id": 1,
  "documentType": "DNI",
  "documentNumber": "30123456",
  "firstName": "Juan",
  "lastName": "Pérez",
  "displayName": "Juancho",
  "email": "juan.perez@example.com",
  "birthDate": "1990-05-15",
  "status": "ACTIVE",
  "createdAt": "2026-10-03T19:38:00Z",
  "updatedAt": "2026-10-03T19:38:00Z"
}
```

### Error contract (`ErrorResponse`)

```json
{
  "code": "NOT_FOUND | VALIDATION_ERROR | BAD_REQUEST | CONFLICT | INTERNAL_ERROR",
  "message": "string",
  "details": [],
  "timestamp": "2026-10-03T19:38:00Z"
}
```

Mapping HTTP:
- 400 → `VALIDATION_ERROR` / `BAD_REQUEST`
- 404 → `NOT_FOUND`
- 409 → `CONFLICT`
- 500 → `INTERNAL_ERROR` (sin stacktrace)

---

## 5. Mapper (MapStruct)

`UserMapper` en `com.bank.users.user.mapper`:
- `User → UserResponse`
- `CreateUserRequest → User` (sin `id`, `status`, `createdAt`, `updatedAt`)

Annotation: `@Mapper(componentModel = "spring")`

---

## 6. Repositorios

```java
// UserRepository extends JpaRepository<User, Long>
Optional<User> findByDocumentTypeAndDocumentNumber(DocumentType type, String number);
boolean existsByDocumentTypeAndDocumentNumber(DocumentType type, String number);

// AuditEventRepository extends JpaRepository<AuditEvent, Long>
List<AuditEvent> findByUserIdOrderByOccurredAtDesc(Long userId);
```

---

## 7. Estrategia de tests

### Cobertura mínima: 80% (JaCoCo — gate `./mvnw clean verify`)

| Capa | Tipo de test | Qué cubre |
|------|-------------|-----------|
| `UserService` | JUnit 5 + Mockito (unit) | Lógica de negocio: alta, duplicado, actualización, transiciones de estado, auditoría |
| `UserController` | `@WebMvcTest` (slice) | HTTP: status codes, validaciones `@Valid`, serialización JSON |
| `UserRepository` | `@DataJpaTest` (slice) | Unicidad, queries por documento |
| Integración | `@SpringBootTest` + TestContainers o H2 (perfil test) | Flujo completo: alta → consulta → cambio estado |

Archivos de test (rutas):
```
src/test/java/com/bank/users/user/service/UserServiceTest.java
src/test/java/com/bank/users/user/controller/UserControllerTest.java
src/test/java/com/bank/users/user/repository/UserRepositoryTest.java
src/test/java/com/bank/users/integration/UserIntegrationTest.java
```

### Escenarios prioritarios para test

1. Alta exitosa → status 201, estado ACTIVE, evento de auditoría ALTA
2. Alta duplicada → status 409, evento ALTA_RECHAZADA
3. Alta con datos incompletos → status 400
4. Consulta por id existente → status 200
5. Consulta por id inexistente → status 404
6. Actualización de campos permitidos → status 200, evento MODIFICACION_DATOS
7. Intento de actualizar campos no permitidos → status 400
8. Suspensión de usuario ACTIVE → status 200, evento CAMBIO_ESTADO
9. Reactivación de usuario SUSPENDED → status 200
10. Baja lógica → status 200, estado INACTIVE, terminal
11. Transición inválida desde INACTIVE → status 400
12. Verificación elegibilidad ACTIVE → eligible: true
13. Verificación elegibilidad SUSPENDED/INACTIVE → eligible: false

---

## 8. Dependencias POM requeridas

| Dependencia | Scope | Notas |
|-------------|-------|-------|
| `spring-boot-starter-web` | compile | Spring MVC |
| `spring-boot-starter-data-jpa` | compile | JPA / Hibernate |
| `spring-boot-starter-validation` | compile | `@Valid`, Bean Validation |
| `spring-boot-starter-actuator` | compile | `/actuator/health` |
| `lombok` | provided | Boilerplate |
| `mapstruct` | compile | Mapeo Entity↔DTO |
| `mapstruct-processor` + `lombok-mapstruct-binding` | annotationProcessor | Orden importa |
| `h2` | test | Base de datos en tests |
| `jacoco-maven-plugin` | plugin | Gate 80% line coverage |
| `springdoc-openapi-starter-webmvc-ui` | compile | Swagger UI (opcional) |

Base de datos de producción: no definida en la spec — se asume configuración externa vía `application.yml` (datasource). El `dev-backend` deberá usar H2 para tests y dejar el datasource de producción parametrizado.

---

## 9. Configuración (`application.yml`)

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate   # producción; usar create-drop en test
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

Perfil `test`: H2 en memoria, `ddl-auto: create-drop`.

---

## 10. Riesgos y decisiones pendientes

| ID | Riesgo / Decisión | Impacto | Propietario |
|----|-------------------|---------|-------------|
| OQ-1 | Campos mínimos de alta no definidos por Compliance | Define payload de `CreateUserRequest`; se asume el mínimo de esta arquitectura | analista / negocio |
| OQ-2 | Unicidad solo tipo+número, o tipo+número ignorando baja lógica | Afecta `UserAlreadyExistsException` y lógica de duplicado; se asume unicidad absoluta (baja lógica bloquea) | analista / negocio |
| OQ-3 | Lista exacta de campos actualizables | Payload de `UpdateUserRequest`; se asume `displayName` + `email` | analista / negocio |
| OQ-4 | Política de exposición de campos por actor | `UserResponse` actual expone todos; si hay diferencia por actor, se necesita segundo DTO | analista / negocio |
| OQ-5 | Reactivación desde baja lógica por proceso especial | Si existe, agrega transición INACTIVE→ACTIVE; por ahora terminal | analista / negocio |
| DB | Motor de base de datos de producción | Datasource parametrizado — no bloquea implementación | devops |
