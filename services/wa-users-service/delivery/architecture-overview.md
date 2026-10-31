# Arquitectura — wa-users-service

## Qué es

Microservicio Spring Boot 3 (Java 21) que administra usuarios bancarios del canal (hito H1):
alta, consulta por documento, actualización de datos permitidos y cambio de estado.
Es la base de identidad para H2/H3; **no** gestiona WhatsApp, verificación de identidad ni sesiones.

Identidad Maven: `com.bcra:wa-users-service` · paquete base `com.bcra.users.ws` (`project.yaml`).

## Capas (módulo `user`)

```
com.bcra.users.ws/
├── UserApplication.java
├── user/
│   ├── controller/     # UserController + UserCreateExceptionHandler (auditoría de altas inválidas)
│   ├── service/        # UserService, AuditService
│   ├── repository/     # UserRepository, AuditLogRepository
│   ├── mapper/         # UserMapper (MapStruct)
│   ├── entity/         # User, Documento, UserStatus, Audit*
│   ├── dto/            # Request / Response
│   ├── exception/      # dominio (NotFound, Duplicate, …)
│   └── validation/     # ContactoPresente
├── config/             # OpenApiConfig
└── exception/          # ErrorResponse + GlobalExceptionHandler
```

## Flujo de una petición

```
HTTP → UserController → UserService → UserRepository → Entity
                ↓              ↓
           UserMapper ←———— UserMapper
                ↓
           DTO Response
```

Tras commit/rollback de alta, actualización o cambio de estado, `AuditService` persiste en
`REQUIRES_NEW` (referencia por documento, sin FK a User) para conservar auditoría si la operación
principal se revierte. Las altas rechazadas por Bean Validation las audita
`UserCreateExceptionHandler` como `ALTA` / `RECHAZADO` cuando el body trae un documento identificable
(tipo y número no vacíos), antes de devolver el `VALIDATION_ERROR` del contrato global.

## Endpoints

| Método | Ruta | Responsabilidad |
|--------|------|-----------------|
| POST | `/api/v1/users` | Alta ACTIVE; unicidad por documento; responde `Location` |
| GET | `/api/v1/users/{tipo}/{numero}` | Consulta completa; exige rol `USERS_FULL_READ` |
| GET | `/api/v1/users/{tipo}/{numero}/status` | Documento + estado (H3) |
| PATCH | `/api/v1/users/{tipo}/{numero}` | Datos permitidos |
| PATCH | `/api/v1/users/{tipo}/{numero}/status` | Transiciones de estado |

## Estados y transiciones (H1)

| Desde | Hacia permitidos |
|-------|------------------|
| ACTIVE | SUSPENDED, LOGICAL_DELETE |
| SUSPENDED | ACTIVE, LOGICAL_DELETE |
| LOGICAL_DELETE | *(ninguna — sin reactivación en este hito)* |

Transición inválida → HTTP **400** `BAD_REQUEST` (contrato explícito; no 409).

## Persistencia y perfiles

| Perfil | BD | `ddl-auto` |
|--------|-----|------------|
| default | PostgreSQL (`DB_URL`, …) | `none` |
| `dev` | PostgreSQL | `update` |
| `docker` | PostgreSQL en Compose (`postgres:5432`) | `update` |
| `test` | H2 in-memory (MODE=PostgreSQL) | `create-drop` |
| `prod` | PostgreSQL (vars obligatorias) | `validate` |

## Decisiones relevantes

- Identificador único de negocio: documento (tipo + número).
- Contacto: email **o** teléfono (`@ContactoPresente`); sin formato cerrado.
- Tipos de documento: catálogo abierto (string).
- Alta **201** con header `Location: /api/v1/users/{tipo}/{numero}`.
- Consulta completa: exige principal autenticado con rol de servidor `USERS_FULL_READ`; sin él
  responde **403** `FORBIDDEN` sin PII. El servicio no configura autenticación en este hito: con la
  configuración del repo esa consulta responde 403; con el rol (p. ej. gateway) se expone el
  `UserResponse` completo hasta que Compliance defina la proyección por perfil.
- PATCH actualización: `JsonAnySetter` rechaza propiedades desconocidas.
- Auditoría interna sin endpoint HTTP; altas inválidas con documento identificable quedan en `RECHAZADO`.
- Helm: el chart no trae `DB_PASSWORD` por defecto; hay que pasarlo explícitamente (ver
  `helm/wa-users-service/README.md`).

## Alcance H1 / fuera de alcance

Ver `specs/usuarios/spec.md`. Fuera: vínculo WhatsApp (H3), verificación de identidad (H3),
credenciales de sesión (H2), productos (H4–H6), cambios de datos legales sensibles.

## Limitaciones conocidas (preguntas abiertas de la spec)

1. **Proyección de campos por perfil** — pendiente de Compliance; hoy, con rol `USERS_FULL_READ`,
   se expone el response completo.
2. **Catálogo de tipos de documento** — no cerrado; se acepta cualquier tipo no vacío.
3. **Formato de email/teléfono** — no cerrado; solo se exige presencia de al menos uno en el alta.
4. **Reactivación desde baja lógica** — no soportada en H1 (`LOGICAL_DELETE` no tiene salidas).
