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
│   ├── controller/     # HTTP (/api/v1/users)
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
principal se revierte.

## Endpoints

| Método | Ruta | Responsabilidad |
|--------|------|-----------------|
| POST | `/api/v1/users` | Alta ACTIVE; unicidad por documento |
| GET | `/api/v1/users/{tipo}/{numero}` | Consulta completa (provisional) |
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
- Consulta: UserResponse completo hasta política de Compliance.
- PATCH actualización: `JsonAnySetter` rechaza propiedades desconocidas.
- Auditoría interna sin endpoint HTTP.

## Alcance H1 / fuera de alcance

Ver `specs/usuarios/spec.md`. Fuera: vínculo WhatsApp (H3), verificación de identidad (H3),
credenciales de sesión (H2), productos (H4–H6), cambios de datos legales sensibles.

## Limitaciones conocidas (preguntas abiertas de la spec)

1. **Filtrado de campos por perfil** — pendiente de Compliance; hoy se expone el response completo.
2. **Catálogo de tipos de documento** — no cerrado; se acepta cualquier tipo no vacío.
3. **Formato de email/teléfono** — no cerrado; solo se exige presencia de al menos uno en el alta.
4. **Reactivación desde baja lógica** — no soportada en H1 (`LOGICAL_DELETE` no tiene salidas).
