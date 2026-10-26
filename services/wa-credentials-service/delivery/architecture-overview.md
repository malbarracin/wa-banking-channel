# Arquitectura — wa-credentials-service

## Qué es

Microservicio Spring Boot 3 (Java 21) que implementa el Servicio de Acceso y Sesión (H2) del canal
WhatsApp: emite una credencial de sesión cuando H3 confirma identidad verificada y vínculo activo, la
valida para H4–H6, la renueva mientras no haya riesgo y la revoca ante bloqueo, desvinculación, fraude
o decisión del banco. También expone un historial resumido para soporte.

**No** gestiona usuarios (H1), el flujo conversacional ni el vínculo número–cliente (H3).

Identidad Maven: `com.bcra:wa-credentials-service` · paquete base `com.bcra.credentials.ws`
(`project.yaml`). Base propia `wa_credentials`, puerto `8082`.

## Capas (módulo `credencial`)

```
com.bcra.credentials.ws/
├── CredentialsApplication.java
├── credencial/
│   ├── controller/     # CredencialController (/api/v1/credenciales)
│   ├── service/        # CredencialService, ValidacionService, RenovacionService,
│   │                   # RevocacionMasivaService, HistorialCredencialService,
│   │                   # CredencialAuditService, JwtService
│   ├── repository/     # CredencialRepository, CredencialAuditLogRepository
│   ├── mapper/         # CredencialMapper (MapStruct)
│   ├── entity/         # CredencialSesion, CredencialAuditLog y enums
│   ├── dto/            # Request / Response
│   ├── client/         # UsersServiceClient (H1) + adaptador REST
│   └── exception/      # excepciones de dominio
├── config/             # OpenApiConfig, JwtConfig, RestClientConfig, RenovacionConfig
└── exception/          # ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler
```

## Flujo de una petición

```
HTTP → CredencialController → Service → Repository → Entity
              ↓                   ↓
        CredencialMapper ←———— CredencialMapper
              ↓
         DTO Response
```

- **Emisión / revocación individual**: `CredencialService` usa `TransactionTemplate`; el cambio de
  estado y la auditoría de éxito se confirman juntos. Si la transacción falla, la auditoría de rechazo
  se guarda en `REQUIRES_NEW` con `CredencialAuditService`.
- **Validación**: `ValidacionService` verifica el JWT con `JwtService`, la credencial persistida por
  `jti`, la correspondencia de identidad/vencimiento y el estado del cliente en H1. Todo rechazo se
  audita como `VALIDACION_FALLIDA` y se responde **401** uniforme.
- **Renovación**: `RenovacionService` corre con `@Scheduled` (solo si
  `credencial.renovacion.habilitada=true`), rota cada credencial próxima a expirar en una transacción
  propia y audita `REVOCACION`/`REEMPLAZO` y `RENOVACION`/`RENOVACION`.
- **Revocación masiva**: `RevocacionMasivaService` revoca en una sola transacción y genera una
  auditoría por credencial; ante cualquier fallo se revierte el lote completo.
- **Historial**: `HistorialCredencialService` es de solo lectura; calcula `EXPIRADA` en la respuesta
  sin tocar la entidad.

## Endpoints

| Método | Ruta | Responsabilidad |
|--------|------|-----------------|
| POST | `/api/v1/credenciales/emitir` | Emite credencial; invalida la activa anterior |
| POST | `/api/v1/credenciales/validar` | Autoriza o rechaza una credencial presentada |
| POST | `/api/v1/credenciales/{numero}/revocacion` | Revocación inmediata de una credencial |
| POST | `/api/v1/credenciales/revocacion-masiva` | Revocación por política/riesgo (todas o por cliente) |
| GET | `/api/v1/credenciales/{numero}/historial` | Estado efectivo y eventos del número |

La renovación no tiene endpoint.

## Modelo y estados

`CredencialSesion`: `id`, `jti` (único; nunca se expone en el historial), `numeroWhatsapp` (E.164),
`documentoTipo` + `documentoNumero` (referencia a H1, sin FK), `vinculoId` (referencia interna estable
derivada de número y documento: el contrato de H3 no envía su UUID), `estado`, `fechaEmision`,
`fechaExpiracion`, `fechaRevocacion`, `motivoRevocacion`, `riesgo`, `revocadaPor`, `createdAt`,
`updatedAt`.

| Enum | Valores |
|------|---------|
| `CredencialEstado` | `ACTIVA`, `REVOCADA`, `EXPIRADA` |
| `MotivoRevocacion` | `BLOQUEO`, `DESVINCULACION`, `FRAUDE`, `POLITICA_BANCO`, `REVOCACION_MASIVA`, `REEMPLAZO` |
| `RevocadaPor` | `CANAL`, `BANCO`, `SISTEMA` |
| `AuditAccion` | `EMISION`, `RENOVACION`, `REVOCACION`, `VALIDACION_FALLIDA` |
| `AuditResultado` | `EXITOSO`, `RECHAZADO` |

`CredencialAuditLog` guarda acción, resultado, número, documento, `jti`, motivo y fecha. El motivo
libre se sustituye por un texto fijo: nunca se persisten secretos ni el contenido del JWT.

## Credencial (JWT HS256)

Claims: `sub` (número E.164), `doc` (`tipo:numero`), `iat`, `exp`, `jti`. Firma con
`CREDENTIAL_JWT_SECRET` (≥ 32 bytes UTF-8, obligatorio, sin default). Vigencia
`CREDENTIAL_VIGENCIA_MINUTOS` (default 1440). El token se entrega solo a H3: no se incluye en logs,
auditoría, mensajes al cliente ni en la respuesta del historial.

## Persistencia y perfiles

| Perfil | BD | Esquema |
|--------|----|---------|
| default | PostgreSQL (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | Flyway `db/migration`, `ddl-auto=none` |
| `dev` | PostgreSQL | Flyway, `ddl-auto=none`, `show-sql=false` |
| `docker` | PostgreSQL host `postgres` | Flyway, `ddl-auto=none` |
| `test` | H2 en memoria (`MODE=PostgreSQL`) | `ddl-auto=create-drop`, Flyway off, renovación off |

`V001__credencial.sql` crea credencial y auditoría e impone, con un índice único parcial de PostgreSQL,
un `jti` único y una sola credencial `ACTIVA` por número. `V002__auditoria_validacion_sin_identidad.sql`
permite auditar rechazos sin identidad. H2 no soporta el índice parcial: por eso Flyway se desactiva
en `test`.

## Integración con H1

`UsersServiceClient` consulta `GET /api/v1/users/{tipo}/{numero}/status` con `RestClient` y timeouts
finitos (`WA_USERS_SERVICE_CONNECT_TIMEOUT`, `WA_USERS_SERVICE_READ_TIMEOUT`), sin reintentos.
404 → `ClienteNoEncontradoException`; error HTTP, de transporte o respuesta inválida →
`UsersServiceUnavailableException`. Una falla nunca se interpreta como cliente activo.

H2 **no** llama a H3: H3 solicita emisión y revocación. La renovación es interna y no entrega el JWT a
H3 (entrega fiable/outbox fuera de este hito).

## Contrato de error

Un solo `ErrorResponse` (`code`, `message`, `details`, `timestamp` UTC) generado por
`GlobalExceptionHandler` en `exception/`. `details` nunca es `null`; no se exponen valores rechazados,
stacktraces ni detalles internos. Códigos propios de H2: `UNAUTHORIZED` (401), `FORBIDDEN` (403) y
`SERVICE_UNAVAILABLE` (503), además de los base.

## Decisiones relevantes

- Una sola credencial `ACTIVA` por número, reforzada por índice parcial; la emisión y la renovación
  actualizan la anterior antes del `INSERT`.
- El JWT es el mecanismo de credencial (decisión de arquitectura, fuera de la fuente funcional).
- La emisión confía en las confirmaciones de H3 (`vinculoActivo`, `identidadVerificada`) y verifica el
  estado del cliente en H1; no consulta el vínculo a H3.
- Revocación masiva implementada para `TODAS` y `POR_CLIENTE`.
- La auditoría de rechazos corre en transacción independiente para conservarse ante rollback.

## Alcance y limitaciones conocidas

Fuera de alcance: alta de usuarios (H1), flujo de verificación y gestión del vínculo (H3), operación de
productos (H4–H6), formato externo de la credencial.

Pendiente de definición de negocio: valor definitivo de vigencia y ventana de renovación, criterio
concreto de "señal de riesgo" y de "señal de fraude", alcance fino de la revocación masiva y
mecanismo de entrega del JWT renovado a H3 (hoy el job es solo interno). Ver
[`specs/acceso-sesion/spec.md`](../../../specs/acceso-sesion/spec.md) y
[`specs/acceso-sesion/arquitectura.md`](../../../specs/acceso-sesion/arquitectura.md).
