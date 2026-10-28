# Arquitectura — wa-users-service (H1)

Vista técnica del servicio integrado. Fuente funcional: `specs/usuarios/spec.md`. Plan: `specs/arquitectura/plan.md`.

## Responsabilidad

Identidad bancaria mínima para el canal WhatsApp: alta, consulta, patch de campos permitidos y máquina
de estados. **No** incluye vínculo WhatsApp (H3), sesiones (H2), productos ni API pública de auditoría.

## Capas (MVC por módulo)

```
Request → UserController (@Valid) → UserService (@Transactional)
              ↓                         ↓
         UserMapper              UserRepository / AuditService
              ↓                         ↓
         UserResponse                 User / AuditLog
```

| Capa | Paquete | Rol |
|------|---------|-----|
| Controller | `user.controller` | HTTP, validación, status codes |
| Service | `user.service` | Unicidad, contacto, transiciones, aptitud |
| Repository | `user.repository` | JPA |
| Mapper | `user.mapper` | MapStruct Entity ↔ DTO |
| Entity / DTO | `user.entity` / `user.dto` | Persistencia / contrato API |
| Audit | `audit.*` | Escritura de `AuditLog` (sin controller) |
| Exception | `exception` + `user.exception` | Contrato global de error |

Base package: `com.bank.users` (`project.yaml`).

## Dominio relevante

- **User**: id UUID, documento (tipo+número único), displayName, email/phone, birthDate, status, preferences.
- **UserStatus**: `ACTIVE` | `SUSPENDED` | `LOGICALLY_DELETED` (terminal).
- **channelLinkEligible**: `true` solo si `status == ACTIVE` (derivado en el mapper; sin endpoint aparte).
- **DocumentType**: `DNI`, `PASSPORT`, `LC`, `LE`, `CUIT`.

## Auditoría

Cada alta, actualización relevante de datos y cambio de estado registra un `AuditLog` en la **misma
transacción** que el cambio de usuario (`AuditService` desde `UserService`):

| Acción | Momento |
|--------|---------|
| `USER_CREATED` | Tras persistir el alta |
| `USER_UPDATED` | Si cambió al menos un campo permitido |
| `STATUS_CHANGED` | Tras aplicar la transición |

Sin endpoints de lectura de auditoría en H1.

## Contrato HTTP

Todo bajo `/api/v1/users` (sin paquetes versionados). Errores: `{code, message, details, timestamp}`
vía `GlobalExceptionHandler`. Detalle por operación: [`api.md`](api.md) y [`openapi/`](openapi/).

## Persistencia y perfiles

- Runtime: PostgreSQL; tests: H2 (perfil `test`).
- `spring.jpa.open-in-view: false`.
- Actuator: `/actuator/health`, `/actuator/info`.
- Operación local/CI: [`devops-local.md`](devops-local.md).
