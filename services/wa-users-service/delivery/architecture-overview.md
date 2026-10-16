# Arquitectura — wa-users-service

Microservicio Spring Boot 3 / Java 21 del canal WhatsApp bancario (hito H1).
Fuente de requisitos: `specs/usuarios/spec.md`, plan técnico `specs/arquitectura/spec.md`.

## Capas

```
Request → UserController → UserService → UserRepository → User (JPA)
                ↓               ↓
           UserMapper ←—— AuditService → AuditEntry
                ↓
           DTO / Response
```

| Capa | Paquete | Rol |
|------|---------|-----|
| Controller | `com.bank.users.user.controller` | HTTP, `@Valid`, status |
| Service | `…user.service` / `…audit.service` | Negocio, transacciones, auditoría |
| Repository | `…user.repository` / `…audit.repository` | Persistencia |
| Mapper | `…user.mapper` | Entity ↔ DTO (MapStruct) |
| Entity | `…user.entity` / `…audit.entity` | Modelo persistido |
| DTO | `…user.dto` | Contrato API |
| Exception | `com.bank.users.exception` (+ `user.exception`) | Error contract global |

## Decisiones definitivas del piloto H1

| Tema | Valor |
|------|-------|
| Unicidad | `(tipoDocumento, numeroDocumento)` |
| ID de recurso | UUID en path |
| Alta mínima | tipo/número documento + `nombreTratamiento` |
| Actualizables | `nombreTratamiento`, `email`, `telefonoContacto`, `preferencias` |
| Estados | `ACTIVO`, `SUSPENDIDO`, `BAJA_LOGICA` |
| Transiciones | ACTIVO↔SUSPENDIDO; ACTIVO\|SUSPENDIDO→BAJA_LOGICA; sin reactivar desde BAJA_LOGICA |
| Aptitud canal | solo ACTIVO |
| Auth | sin JWT (protección por red/gateway) |
| Eventos | sin Kafka / notificaciones a dependientes |

## Endpoints

| Método | Ruta | Caso de uso |
|--------|------|-------------|
| POST | `/api/v1/users` | Alta |
| GET | `/api/v1/users/{id}` | Consulta + historial |
| PATCH | `/api/v1/users/{id}` | Actualización parcial |
| PATCH | `/api/v1/users/{id}/status` | Cambio de estado |
| GET | `/api/v1/users/{id}/channel-eligibility` | Aptitud para H3 |

## Persistencia

PostgreSQL (JPA). Tests: H2 en memoria. Auditoría append-only en `audit_entries`.
Perfiles: `dev`/`docker` (`ddl-auto=update`), `prod` (`validate`), `test` (`create-drop`).

## Fuera de alcance H1

WhatsApp, verificación de canal, credenciales de sesión, productos bancarios, Kafka, filtros por actor.
