---
title: Usuarios — especificación
status: borrador
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-11-13T20:51:01.596Z
---

# Usuarios — especificación

## Propósito

Administrar a las personas clientes del banco: dar de alta un nuevo cliente, consultarlo, actualizar los datos que el banco autoriza en este servicio y gestionar su estado operativo (activo, suspendido, baja lógica). Es la base de identidad sobre la cual otros servicios identifican al cliente antes de operar por cualquier canal (WhatsApp, app, sucursal).

Este servicio NO gestiona WhatsApp, NO vincula números de teléfono y NO emite ni revoca credenciales de sesión del canal.

## Actores

| Actor | Qué puede hacer |
|------|-----------------|
| Área de negocio / operaciones | Dar de alta usuarios, consultar, actualizar datos permitidos, cambiar estado |
| Soporte del banco | Consultar estado e historial resumido del usuario (sin datos de verificación del canal) |
| Otros servicios del banco | Consultar existencia y datos básicos del cliente (según política de acceso) |
| Cliente final por WhatsApp | No interactúa directamente con este servicio; lo hace a través del canal (H3) |

## Fuera de alcance

- Vincular número de WhatsApp (H3 — Canal WhatsApp).
- Verificación de identidad por el canal (H3).
- Emisión o revocación de credencial de sesión (H2 — Acceso y Sesión).
- Consulta u operación de productos: cuentas, tarjetas, seguros (H4, H5, H6).
- Cambio de datos legales sensibles (domicilio legal, estado civil, etc.) salvo política expresa del banco.
- Onboarding comercial completo de productos.

## Decisiones resueltas (de la persona)

- **Datos mínimos obligatorios del alta** (conjunto completo de Compliance): tipo de documento, número de documento, nombre, apellido, email de contacto, teléfono de contacto, dirección y fecha de nacimiento. El alta no se completa con información incompleta.
- **Regla de unicidad**: el par tipo + número de documento DEBE ser único y el email DEBE ser único, cada uno de forma independiente. Un alta con documento o email ya existente se rechaza como duplicado.
- **Estados operativos**: activo, suspendido, baja lógica.
- **Transiciones de estado permitidas** (de los flujos U4): activo → suspendido; suspendido → activo (reactivación); activo o suspendido → baja lógica.

## Supuestos

- Los identificadores de consulta acordados son el identificador interno asignado al alta y el par tipo + número de documento. La fuente menciona "identificador acordado (documento, ID interno, etc.)"; el arquitecto puede ampliar el conjunto.
- La actualización del email de contacto está permitida como dato de contacto y debe respetar la regla de unicidad.

## Requisitos

### Requisito: Alta de usuario bancario
El sistema DEBE permitir dar de alta un nuevo cliente recibiendo los datos mínimos obligatorios y, si pasan la validación de unicidad y consistencia, crearlo en estado activo con un identificador asignado.

#### Escenario: alta exitosa con datos mínimos válidos
- **DADO** que no existe un cliente con el mismo documento ni el mismo email
- **CUANDO** se reciben los datos mínimos obligatorios válidos (tipo y número de documento, nombre, apellido, email, teléfono, dirección, fecha de nacimiento)
- **ENTONCES** se crea el cliente en estado activo
- **Y** se asigna un identificador al cliente
- **Y** se registra el alta con fecha, acción y resultado

#### Escenario: alta rechazada por documento duplicado
- **DADO** que ya existe un cliente con el mismo tipo y número de documento
- **CUANDO** se reciben los datos del nuevo cliente
- **ENTONCES** no se crea el cliente
- **Y** se informa el motivo: duplicado por documento

#### Escenario: alta rechazada por email duplicado
- **DADO** que ya existe un cliente con el mismo email
- **CUANDO** se reciben los datos del nuevo cliente
- **ENTONCES** no se crea el cliente
- **Y** se informa el motivo: duplicado por email

#### Escenario: alta rechazada por dato inválido o incompleto
- **DADO** que falta alguno de los datos mínimos obligatorios o alguno es inválido
- **CUANDO** se reciben los datos del nuevo cliente
- **ENTONCES** no se crea el cliente
- **Y** se informa el motivo: dato inválido o información incompleta

### Requisito: Consulta de usuario
El sistema DEBE permitir consultar un cliente por un identificador acordado y devolver únicamente los datos autorizados para quien consulta, junto con su estado actual.

#### Escenario: consulta por identificador interno
- **DADO** que existe un cliente con el identificador interno asignado
- **CUANDO** se lo consulta por ese identificador
- **ENTONCES** se devuelven los datos autorizados para quien consulta y su estado actual

#### Escenario: consulta por documento
- **DADO** que existe un cliente con ese tipo y número de documento
- **CUANDO** se lo consulta por documento
- **ENTONCES** se devuelven los datos autorizados para quien consulta y su estado actual

#### Escenario: consulta de cliente inexistente
- **DADO** que no existe un cliente con el identificador indicado
- **CUANDO** se lo consulta
- **ENTONCES** se informa que no se encontró

#### Escenario: consulta por otro servicio del banco devuelve solo datos básicos
- **DADO** que otro servicio del banco consulta existencia y datos del cliente
- **CUANDO** realiza la consulta según la política de acceso
- **ENTONCES** se devuelven solo los datos básicos autorizados por la política de privacidad
- **Y** no se exponen datos de verificación del canal

### Requisito: Actualización de datos permitidos
El sistema DEBE permitir actualizar los campos autorizados de un cliente identificado y registrar la modificación.

#### Escenario: actualización de datos de tratamiento y contacto
- **DADO** que existe el cliente
- **CUANDO** se actualizan campos permitidos (nombre para tratamiento, datos de contacto, preferencias generales no sensibles)
- **ENTONCES** se guardan los cambios
- **Y** se confirma el guardado al solicitante
- **Y** se audita la modificación con fecha, acción y resultado

#### Escenario: actualización de email respeta unicidad
- **DADO** que se actualiza el email de contacto del cliente
- **CUANDO** el nuevo email ya pertenece a otro cliente
- **ENTONCES** no se guarda el cambio
- **Y** se informa el motivo: email duplicado

#### Escenario: actualización de dato sensible rechazada
- **DADO** que se intenta modificar un dato legal sensible (domicilio legal, estado civil, etc.)
- **CUANDO** no hay política expresa del banco que lo permita en este servicio
- **ENTONCES** no se guarda el cambio
- **Y** se informa que el campo no es actualizable en este servicio

### Requisito: Cambio de estado operativo
El sistema DEBE permitir cambiar el estado operativo del cliente entre activo, suspendido y baja lógica, validando que el cambio cumpla la política del banco, y registrarlo.

#### Escenario: suspensión de un cliente activo
- **DADO** que el cliente está activo
- **CUANDO** se solicita pasarlo a suspendido
- **ENTONCES** su estado pasa a suspendido
- **Y** se registra el cambio con fecha, acción y resultado
- **Y** se notifica a los servicios dependientes si aplica

#### Escenario: reactivación de un cliente suspendido
- **DADO** que el cliente está suspendido
- **CUANDO** se solicita reactivarlo
- **ENTONCES** su estado pasa a activo
- **Y** se registra el cambio con fecha, acción y resultado

#### Escenario: baja lógica de un cliente
- **DADO** que el cliente está activo o suspendido
- **CUANDO** se solicita la baja lógica
- **ENTONCES** su estado pasa a baja lógica
- **Y** se registra el cambio con fecha, acción y resultado
- **Y** se notifica a los servicios dependientes si aplica

#### Escenario: cambio de estado no permitido se rechaza
- **DADO** que se solicita un cambio que no cumple la política del banco (ej. reactivar un cliente en baja lógica sin regularización)
- **CUANDO** se procesa la solicitud
- **ENTONCES** no se cambia el estado
- **Y** se informa el motivo

### Requisito: Unicidad del cliente
El sistema DEBE garantizar que ningún cliente duplique el par tipo + número de documento ni el email, cada uno de forma independiente.

#### Escenario: dos clientes con mismo documento no coexisten
- **DADO** que existe un cliente con un tipo y número de documento
- **CUANDO** se intenta crear o dejar a otro cliente con ese mismo documento
- **ENTONCES** el sistema lo rechaza como duplicado

#### Escenario: dos clientes con mismo email no coexisten
- **DADO** que existe un cliente con un email
- **CUANDO** se intenta crear o dejar a otro cliente con ese mismo email
- **ENTONCES** el sistema lo rechaza como duplicado

### Requisito: Restricción de vínculo de canal según estado
El sistema DEBE reflejar el estado operativo de forma que un cliente suspendido o en baja lógica no pueda iniciar nuevos vínculos de canal hasta regularizar su estado.

#### Escenario: cliente suspendido no habilita nuevo vínculo de canal
- **DADO** que el cliente está suspendido
- **CUANDO** otro servicio (H3 — Canal WhatsApp) consulta si puede vincular un nuevo número
- **ENTONCES** el estado reportado impide iniciar el nuevo vínculo hasta regularizar

#### Escenario: cliente en baja lógica no habilita nuevo vínculo de canal
- **DADO** que el cliente está en baja lógica
- **CUANDO** otro servicio consulta si puede vincular un nuevo número
- **ENTONCES** el estado reportado impide iniciar el nuevo vínculo hasta regularizar

#### Escenario: cliente activo habilita nuevo vínculo de canal
- **DADO** que el cliente está activo
- **CUANDO** otro servicio consulta si puede vincular un nuevo número
- **ENTONCES** el estado reportado permite iniciar el vínculo

### Requisito: Auditoría de altas, cambios de estado y modificaciones
El sistema DEBE registrar toda alta, cambio de estado y modificación relevante con fecha, acción y resultado.

#### Escenario: alta queda registrada
- **DADO** que se da de alta un cliente
- **CUANDO** se completa el alta
- **ENTONCES** queda un registro con fecha, acción y resultado

#### Escenario: cambio de estado queda registrado
- **DADO** que se cambia el estado de un cliente
- **CUANDO** se completa el cambio
- **ENTONCES** queda un registro con fecha, acción y resultado

#### Escenario: modificación queda registrada
- **DADO** que se modifica un campo permitido de un cliente
- **CUANDO** se completa la modificación
- **ENTONCES** queda un registro con fecha, acción y resultado

### Requisito: Existencia previa del cliente para operar por canal
El sistema DEBE ser la base de identidad: todo cliente que operará por WhatsApp DEBE existir en este servicio antes de vincular el canal.

#### Escenario: el canal verifica existencia del cliente
- **DADO** que se intenta vincular un número de WhatsApp a un cliente
- **CUANDO** el canal (H3) verifica la existencia del cliente en este servicio
- **ENTONCES** solo se permite continuar si el cliente existe y está en estado que habilita el vínculo

## Arquitectura

### Identidad Java (fuente: `project.yaml`)

- **groupId**: `com.bcra` (de `project.yaml` → `java.group_id`).
- **Base package**: `com.bcra.ws.users` (de `project.yaml` → `java.base_package`).
- **Servicio**: carpeta `services/wa-users-service`, artifactId `wa-users-service`, Spring Boot 3.3.x, Java 21.
- Cita obligatoria en tasks y código: `(from project.yaml java.base_package)`. No inferir desde el nombre del repo ni de la identidad `com.ingenia.wa` del scaffold actual (ver Brechas).

### Stack

- Java 21, Spring Boot 3.3.x, Spring MVC (`spring-boot-starter-web`), Spring Data MongoDB, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, Lombok, MapStruct, springdoc-openapi, JaCoCo.
- **Persistencia**: MongoDB. Colecciones `users` y `user_audit_events`. Base `wa_users` en perfil `local` (`mongodb://localhost:27017/wa_users`); URI por variable de entorno `SPRING_DATA_MONGODB_URI` en `docker`.
- **Build**: Maven Wrapper (`./mvnw`). No usar `mvn` global. Cierre con `./mvnw clean verify` (gate de JaCoCo en `verify`).
- Sin WebFlux, sin Gradle/Kotlin, sin cambio de persistencia (Mongo fijo).

### Estructura de paquetes (MVC por capas + versionado de API)

`api.versioning.enabled: true` en `application.yml` → la capa de API se versiona por paquete; dominio (service/repository/entity/domain) se comparte.

```
com.bcra.ws.users/
├── UsersServiceApplication.java
├── api/v1/
│   ├── controller/   UserController                (@RequestMapping("/api/v1/users"))
│   ├── dto/          UserCreateRequest, UserResponse, UserUpdateRequest,
│   │                 UserStatusChangeRequest, UserAuditEventResponse
│   └── mapper/       UserMapper (MapStruct, componentModel = "spring")
├── config/           OpenApiConfig (GroupedOpenApi por versión), NativeHints (solo si native)
├── exception/        ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler,
│                    ConflictException, NotFoundException, DuplicateDocumentException,
│                    DuplicateEmailException, UserNotFoundException, InvalidStatusTransitionException
├── domain/          UserStatus, DocumentType, AuditAction, AuditResult
├── entity/          UserEntity, UserAuditEventEntity (Mongo @Document)
├── repository/      UserRepository, UserAuditEventRepository, UserIndexInitializer
└── service/         UserService, UserServiceImpl, UserAuditService
```

El versionado se expresa en el paquete `api/v1` + `@RequestMapping("/api/v1/...")` + `GroupedOpenApi` por versión. Una futura v2 agregaría `api/v2` con sus propias controller/dto/mapper; service, repository y entity se mantienen compartidos.

### Modelo de persistencia (Mongo)

- **`users`** — `UserEntity` (`@Document(collection = "users")`, `@Id String id`).
  - Índice único `uk_document` sobre `(documentType, documentNumber)`.
  - **Índice único `uk_email` sobre `email`** (nuevo).
  - Campos: `documentType` (enum), `documentNumber`, `firstName`, `lastName`, `email`, `phone`, **`address`** (nuevo), **`birthDate`** (nuevo, `LocalDate`), `preferredName`, `preferredLanguage`, `status` (enum), `createdAt`, `updatedAt`.
- **`user_audit_events`** — `UserAuditEventEntity` (`@Document(collection = "user_audit_events")`), append-only, índice por `(userId, timestamp desc)`. Campos: `id`, `userId`, `action` (`AuditAction`), `result` (`AuditResult`), `timestamp`, `summary` (`Map<String,Object>`), `actor`.
- `UserIndexInitializer` (`ApplicationRunner`) asegura los índices únicos `uk_document` y `uk_email` al arranque.

### Lombok en entidades

- Entidades: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`. **Evitar `@Data`** en entidades; usar `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` con `@EqualsAndHashCode.Include` solo en `id`.
- DTOs y records: `@Data`/`@Builder` o `record` según mutabilidad.

### DTOs

- **`UserCreateRequest`** (U1, alta). Requeridos (`@NotNull`/`@NotBlank`): `documentType`, `documentNumber`, `firstName`, `lastName`, `email` (`@Email`), **`phone` (`@NotBlank`)**, **`address` (`@NotBlank`)**, **`birthDate` (`@NotNull`, `@Past`)**. Opcionales: `preferredName`, `preferredLanguage` (default `"es"`).
- **`UserUpdateRequest`** (U3, PATCH parcial). Campos actualizables (todos opcionales, al menos uno requerido): `preferredName`, `email` (`@Email`), `phone`, `preferredLanguage`, `address` (dato de contacto). **No actualizables** (rechazo si aparecen en el body): `documentType`, `documentNumber`, `firstName`, `lastName`, `birthDate`, `status` y cualquier dato legal sensible.
- **`UserStatusChangeRequest`** (U4): `status` (`@NotNull`, `UserStatus`).
- **`UserResponse`** (U2): `id`, `documentType`, `documentNumber`, `firstName`, `lastName`, `email`, `phone`, `address`, `birthDate`, `preferredName`, `preferredLanguage`, `status`, `channelLinkEligible`, `createdAt`, `updatedAt`. La visibilidad por actor se filtra (ver Vistas de consulta).
- **`UserAuditEventResponse`**: `id`, `userId`, `action`, `timestamp`, `result`, `summary`, `actor`.
- `@JsonIgnoreProperties(ignoreUnknown = false)` en los DTO de entrada para rechazar campos no esperados (soporta el rechazo de dato sensible en U3).

### Endpoints (API v1)

| Método | Path | Flujo | Éxito | Errores |
|--------|------|-------|-------|--------|
| POST | `/api/v1/users` | U1 alta | 201 `UserResponse` (ACTIVE) | 400 `VALIDATION_ERROR`; 409 `CONFLICT` (doc/email duplicado) |
| GET | `/api/v1/users/{id}` | U2 por id | 200 `UserResponse` | 404 `NOT_FOUND` |
| GET | `/api/v1/users?documentType=&documentNumber=` | U2 por documento | 200 `UserResponse` | 400 `BAD_REQUEST`; 404 `NOT_FOUND` |
| PATCH | `/api/v1/users/{id}` | U3 update | 200 `UserResponse` | 400 `VALIDATION_ERROR`/`BAD_REQUEST` (sin campos / campo no actualizable); 404 `NOT_FOUND`; 409 `CONFLICT` (email duplicado) |
| PATCH | `/api/v1/users/{id}/status` | U4 cambio de estado | 200 `UserResponse` | 404 `NOT_FOUND`; 409 `CONFLICT` (transición inválida) |
| GET | `/api/v1/users/{id}/audit` | Historial de auditoría | 200 `[UserAuditEventResponse]` | 404 `NOT_FOUND` |

- Header opcional `X-Actor-Id` (auditoría).
- Actuator: `/actuator/health`, `/actuator/info`. Swagger: `/swagger-ui.html`, `/v3/api-docs`.
- El canal (H3) verifica existencia y elegibilidad leyendo `status` y `channelLinkEligible` de `GET /api/v1/users/{id}` (o por documento).

### Contrato de errores (skill `error-contract`)

Un único JSON: `{ "code", "message", "details": [{ "field", "message" }], "timestamp" }`.

- `ErrorCode` (enum): `VALIDATION_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`.
- `FieldError` (record): `field`, `message`. `details` nunca `null` (vacío si corresponde).
- `GlobalExceptionHandler` (`@RestControllerAdvice`) en `exception/`. Sin `try/catch` en controllers; sin stacktrace ni internals en la respuesta.
- Mapeo:

| HTTP | `code` | Caso |
|------|-------|------|
| 400 | `VALIDATION_ERROR` | Bean Validation (`MethodArgumentNotValidException`, campo desconocido en body) |
| 400 | `BAD_REQUEST` | Argumento ilegal (update sin campos, `documentNumber` vacío, params faltantes) |
| 404 | `NOT_FOUND` | `UserNotFoundException` |
| 409 | `CONFLICT` | `DuplicateDocumentException` (`field=documentNumber`), `DuplicateEmailException` (`field=email`), `InvalidStatusTransitionException` (`field=status`) |
| 500 | `INTERNAL_ERROR` | Fallback (sin stacktrace) |

- Excepciones de dominio extienden `ConflictException` / `NotFoundException` (bases nuevas).
- Config: `server.error.include-stacktrace: never`.

### Transiciones de estado (U4)

Estados: `ACTIVE`, `SUSPENDED`, `LOGICALLY_DELETED`. Transiciones permitidas (de la especificación):

- `ACTIVE → SUSPENDED`
- `SUSPENDED → ACTIVE`
- `ACTIVE → LOGICALLY_DELETED`
- `SUSPENDED → LOGICALLY_DELETED`
- `LOGICALLY_DELETED → (ninguna)` — la baja lógica es terminal; la reactivación requiere regularización, fuera de alcance de H1.

Transición no permitida (incluyendo `from == to`) → auditoría `FAILURE` + `409 CONFLICT` (`field=status`, message `Invalid transition FROM→TO`).

### Unicidad (U1 y U3)

- Documento: índice único `uk_document` sobre `(documentType, documentNumber)`.
- Email: índice único `uk_email` sobre `email`.
- **U1 (alta)**: pre-verificar con `existsByDocumentTypeAndDocumentNumber` y `existsByEmail` antes de guardar; lanzar `DuplicateDocumentException` o `DuplicateEmailException` con el campo correcto. El índice único es red de seguridad (`DuplicateKeyException`). Auditar `FAILURE` en rechazo.
- **U3 (update de email)**: verificar `existsByEmailAndIdNot(email, id)` antes de aplicar; si existe en otro cliente → `409 CONFLICT` (`field=email`). Auditar `FAILURE`.

### Auditoría

- `UserAuditService.record(userId, action, result, summary, actor)` → append en `user_audit_events`.
- Acciones (`AuditAction`): `USER_CREATED`, `USER_UPDATED`, `STATUS_CHANGED`. Resultados (`AuditResult`): `SUCCESS`, `FAILURE`.
- Se registra en: alta (success / failure por duplicado), update (success / failure por email duplicado), cambio de estado (success / failure por transición inválida).
- `findAuditTrail(id)` devuelve eventos ordenados desc por `timestamp`.

### Vistas de consulta (U2) y elegibilidad de canal

- `channelLinkEligible = (status == ACTIVE)`. Soporta «Restricción de vínculo de canal según estado» y «Existencia previa del cliente para operar por canal».
- **Visibilidad por actor**: el banco debe definir el conjunto exacto de campos visibles por actor (pregunta abierta). Mientras no se defina, se aplica este **default ajustable sin romper el contrato**:
  - Área de negocio/operaciones y Soporte: dataset autorizado completo (`UserResponse`).
  - Otros servicios del banco: subset básico `{ id, documentType, documentNumber, status, channelLinkEligible }` (sin `email`, `phone`, `address`, `birthDate` ni datos de contacto).
- Mecanismo: filtrado de la respuesta por actor/origen (vía header `X-Actor-Id`/origen o endpoint dedicado), de modo que la matriz definida por el banco se aplique luego como política sin cambio de esquema.

### Perfiles y configuración (`application.yml`)

- `spring.data.mongodb.uri` (env `SPRING_DATA_MONGODB_URI`), perfiles `local` y `docker`.
- Actuator: exponer `health`, `info` (mínimo).
- `server.error.include-stacktrace: never` (nuevo).
- `springdoc` API docs y swagger-ui.
- `api.versioning.enabled: true`, `default-version: v1`, `supported-versions: [v1]`.
- Sin consola de BD embebida (Mongo). `spring.jpa.open-in-view` no aplica.

### Tests y cobertura

- JUnit 5 + Spring Boot Test + AssertJ. WebMvc tests para controller; unitarios para service/audit; integración U1–U4 contra Mongo (local vía `docker compose up -d mongo`; Testcontainers donde el entorno lo permita).
- **JaCoCo mínimo LINE ≥ 80%** (subir desde el 70% actual del `pom.xml`). Gate en `verify`: `./mvnw clean verify`.
- Tests por versión de API (v1); al agregar v2, los de v1 pasan sin tocarlos.

### Nativa (opcional)

- Imagen nativa solo si `service.optimization: native` en `project.yaml` (no definido) → nativa desactivada por defecto; el `pom.xml` mantiene el profile `native` opt-in. No requerida para el MVP.

## Brechas y desviaciones de la implementación existente

El scaffold actual en `services/wa-users-service` (Mongo, funcional en U1–U4) usa `com.ingenia.wa.users` y se desalinea con `project.yaml` y la especificación aprobada. Tareas para tech-lead:

1. **Identidad (bloqueante para naming)**: renombrar paquete `com.ingenia.wa.users` → `com.bcra.ws.users` y groupId `com.ingenia.wa` → `com.bcra` en `pom.xml` (fuente: `project.yaml`). Mover `src/main/java` y `src/test/java`. Actualizar `application.yml` (`logging.level`), `Dockerfile` y CI. Quitar marcas `@author Ingenia` / `Contacto: …@ingenia.la` de javadocs (la entrega es del equipo del banco).
2. **Campos del alta faltantes**: agregar `address` (`@NotBlank`) y `birthDate` (`@NotNull`, `@Past`) a `UserCreateRequest` y `UserEntity`; hacer `phone` `@NotBlank` en el alta (hoy es opcional). `preferredName`/`preferredLanguage` siguen opcionales.
3. **Unicidad de email no implementada**: agregar índice único `uk_email` en `UserEntity`, `existsByEmail` y `existsByEmailAndIdNot` en `UserRepository`, y las validaciones en `UserServiceImpl` (alta y update de email) con `DuplicateEmailException` → `409 CONFLICT` (`field=email`). Hoy el alta y el update de email no rechazan email duplicado.
4. **Contrato de errores**: mover `api/error` → `exception/`; agregar `ErrorCode` (enum), `FieldError` (record), bases `ConflictException`/`NotFoundException` y hacer que las excepciones de dominio las extiendan; cambiar `409` duplicado y transición inválida de `code=BAD_REQUEST` a `code=CONFLICT` (y agregar `CONFLICT` a `allowableValues` de `ErrorResponse`); agregar `server.error.include-stacktrace: never`.
5. **Transición de estado incorrecta**: quitar `LOGICALLY_DELETED → ACTIVE` de `ALLOWED_TRANSITIONS` (contradice la especificación: la baja lógica es terminal) y corregir el javadoc de `UserController`/`UserStatus` que menciona «reactivación desde LOGICALLY_DELETED».
6. **Dato sensible en U3**: activar rechazo de campos no actualizables en el PATCH (`@JsonIgnoreProperties(ignoreUnknown = false)` + mapeo a `400`) en lugar de ignorarlos silenciosamente; mensaje «field X is not updatable in this service».
7. **JaCoCo**: subir el mínimo de `0.70` a `0.80` (LINE) en el `pom.xml` y en el README.
8. **Entidades**: reemplazar `@Data` por `@Getter @Setter` + `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` (solo `id`) en `UserEntity` y `UserAuditEventEntity`.
9. **Visibilidad por actor**: implementar el mecanismo de filtrado por actor/origen con el default de Vistas de consulta; la matriz exacta queda como pregunta abierta para el banco.
10. **Auditoría de rechazos**: asegurar que el update rechazado por email duplicado y la transición inválida queden registrados con `result=FAILURE` (parcialmente cubierto hoy).

## Riesgos y supuestos (de la fuente)

- **Riesgos**: datos inconsistentes con el core legacy; duplicados no detectados; baja de usuario con vínculos activos en canal.
- **Supuestos**: el banco define los datos mínimos de alta; existe una política de estados de usuario; otros sistemas pueden sincronizarse con este registro.

## Preguntas abiertas

- **Datos visibles por actor en la consulta**: la fuente indica que la consulta devuelve "solo datos autorizados" por política de privacidad, pero no enumera qué campos ve cada actor (área de negocio/operaciones, soporte, otros servicios). No bloquea el comportamiento ni la arquitectura: se implementa un mecanismo de filtrado por actor con un default ajustable (dataset completo para actores internos; subset básico para otros servicios) y la matriz exacta la define el banco antes del modelado final del contrato de consulta.
