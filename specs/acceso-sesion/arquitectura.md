---
title: Acceso y Sesión — arquitectura
status: borrador
source: specs/acceso-sesion/spec.md
updated: 2026-10-26T18:20:46.789Z
---

# Arquitectura — Servicio de Acceso y Sesión (H2)

## Base

- **Servicio nuevo** (no existe `pom.xml` todavía): `services/wa-access-service` (o `wa-credentials-service`), análogo a `services/wa-users-service` (H1) y `services/wa-channel-service` (H3).
- Maven groupId: **com.bcra** (de `project.yaml` → `java.group_id`, ya fijado por H1; se reutiliza).
- Base package: **com.bcra.credentials.ws** — **propuesto**, siguiendo el patrón `<group_id>.<dominio>.ws`. `project.yaml` hoy solo tiene un bloque `java.*` (el de H1); no define identidad para este servicio. Antes de TASK-1 (bootstrap), el tech-lead debe confirmar este valor y dejarlo en `project.yaml`. No bloquea este plan: es la convención ya usada en el repo.
- Artifact id propuesto: `wa-credentials-service`.
- Módulo de dominio: **credencial** (la credencial de sesión; es el agregado central de H2).
- Puerto propuesto: **8082** (H1 8080, H3 8081; evita choque al correr los servicios en local).
- Persistencia: PostgreSQL en runtime, H2 en test (mismo patrón que H1/H3) — **base de datos propia** del servicio (`wa_credentials`, separada): cada microservicio es dueño de sus datos.
- Integraciones salientes (síncronas, REST, sin service discovery):
  - **H1 (wa-users-service)**: consultar existencia/estado del cliente (validar que corresponde al cliente al validar credencial). Base URL configurable (`clients.wa-users-service.base-url`).
  - **H3 (wa-channel-service)**: H2 no llama a H3; H3 solicita emisión/revocación a H2. H2 valida que el vínculo esté activo consultando a H3 o confiando en el flag `vinculoActivo`+`identidadVerificada` provisto por H3 en la emisión (ver Riesgos).

## Formato de la credencial (decisión de arquitectura)

La fuente excluye el formato técnico. Se decide: **JWT firmado (HS256)** con un secret en configuración (`credencial.jwt.secret`, variable de entorno `CREDENTIAL_JWT_SECRET`). Claims mínimos: `sub` (número WhatsApp), `doc` (tipo+número de documento), `iat`, `exp`, `jti` (id de credencial). El token se entrega al canal (H3) para propagarlo a H4/H5/H6 por header `X-Credencial-Sesion` (o equivalente); **nunca se muestra al cliente final**.

Vigencia y renovación parametrizables (política del banco), con defaults razonables para poder probar:
- `credencial.vigencia-minutos` (default 1440 = 24h)
- `credencial.renovacion-ventana-minutos` (default 30: si queda menos, se renueva)
- `credencial.max-credenciales-activas-por-vinculo` = 1 (una activa por vínculo; nueva emisión invalida la anterior)

## Árbol de paquetes

```
com.bcra.credentials.ws/
├── CredentialsApplication.java
├── credencial/
│   ├── controller/      CredencialController
│   ├── service/         CredencialService, RenovacionService, ValidacionService, CredencialAuditService
│   ├── repository/      CredencialRepository, CredencialAuditLogRepository
│   ├── mapper/          CredencialMapper (MapStruct)
│   ├── entity/          CredencialSesion, CredencialEstado, MotivoRevocacion, CredencialAuditLog, AuditAccion, AuditResultado
│   ├── dto/             EmitirCredencialRequest, EmitirCredencialResponse, RevocarCredencialRequest,
│   │                    ValidarCredencialRequest, ValidarCredencialResponse, RevocacionMasivaRequest,
│   │                    HistorialCredencialResponse, EventoCredencialResponse
│   ├── client/          UsersServiceClient (+ dto ClienteEstadoResponse)
│   └── exception/       CredencialNoEncontradaException, CredencialInvalidaException,
│                        CredencialExpiradaException, CredencialRevocadaException, EmisionNoAutorizadaException,
│                        VinculoNoActivoException
├── config/              OpenApiConfig, JwtConfig, RestClientConfig (timeouts + base URLs de H1)
└── exception/           ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler,
                        NotFoundException, ConflictException, UnauthorizedException
```

`client/` es un subpaquete adicional del módulo (adaptador saliente a H1), igual de aceptable que `config/`/`exception/` transversales.

## Entidades

- **credencial.entity.CredencialSesion**: id (UUID), `jti` (string, único), `numeroWhatsapp` (string normalizado E.164), `documentoTipo` + `documentoNumero` (referencia al cliente en H1, sin FK física), `vinculoId` (referencia al vínculo en H3, sin FK física), `estado` (`CredencialEstado`), `fechaEmision`, `fechaExpiracion`, `fechaRevocacion` (nullable), `motivoRevocacion` (`MotivoRevocacion`, nullable), `riesgo` (boolean, señal de riesgo asociada), `revocadaPor` (nullable: CANAL, BANCO, SISTEMA), auditoría (createdAt/updatedAt).
- **credencial.entity.CredencialEstado** (enum): `ACTIVA`, `REVOCADA`, `EXPIRADA`.
- **credencial.entity.MotivoRevocacion** (enum): `BLOQUEO`, `DESVINCULACION`, `FRAUDE`, `POLITICA_BANCO`, `REVOCACION_MASIVA`, `REEMPLAZO`.
- **credencial.entity.CredencialAuditLog**: `accion` (`AuditAccion`: `EMISION`, `RENOVACION`, `REVOCACION`, `VALIDACION_FALLIDA`), `resultado` (`AuditResultado`: `EXITOSO`/`RECHAZADO`), `numeroWhatsapp`, `documentoTipo`/`documentoNumero`, `motivo` (breve, sin secretos ni contenido de la credencial), `jti` (solo id, no el token), fecha.

## DTOs

- `EmitirCredencialRequest(numeroWhatsapp, documentoTipo, documentoNumero, vinculoActivo, identidadVerificada)`
- `EmitirCredencialResponse(credencial, fechaExpiracion)` — `credencial` es el JWT (solo al canal, nunca al cliente final).
- `RevocarCredencialRequest(motivo, solicitadoPor)` (CANAL/BANCO/SISTEMA)
- `ValidarCredencialRequest(credencial)` (el JWT presentado)
- `ValidarCredencialResponse(vigente, numeroWhatsapp, documentoTipo, documentoNumero, fechaExpiracion)`
- `RevocacionMasivaRequest(criterio, segmento?)` — criterio: TODAS, POR_CLIENTE, POR_RIESGO, etc.
- `HistorialCredencialResponse(numeroWhatsapp, documentoTipo, documentoNumero, estadoActual, eventos: List<EventoCredencialResponse>)`
- `EventoCredencialResponse(accion, resultado, fecha, motivo?)`

## Endpoints

Todos bajo `/api/v1/credenciales` (sin versionado por ahora — `api.versioning` no está habilitado en `project.yaml`).

| Método y path | Request | Éxito | Errores (HTTP — `code`) |
|---|---|---|---|
| `POST /api/v1/credenciales/emitir` | `EmitirCredencialRequest` | 201 `EmitirCredencialResponse` | 400 `VALIDATION_ERROR`; 403 `FORBIDDEN`/`CONFLICT` (sin confirmación de verificación/vínculo activo → `EmisionNoAutorizadaException`); 409 `CONFLICT` (número ya tiene credencial activa → se reemplaza, no error; solo si hay conflicto de datos) |
| `POST /api/v1/credenciales/{numero}/revocacion` | `RevocarCredencialRequest` | 204 No Content | 400 `VALIDATION_ERROR`; 404 `NOT_FOUND` (sin credencial activa) |
| `POST /api/v1/credenciales/validar` | `ValidarCredencialRequest` | 200 `ValidarCredencialResponse` | 401 `UNAUTHORIZED` (inválida/expirada/revocada) |
| `POST /api/v1/credenciales/revocacion-masiva` | `RevocacionMasivaRequest` | 200 `{revocadas: N}` | 400 `VALIDATION_ERROR` |
| `GET /api/v1/credenciales/{numero}/historial` | — | 200 `HistorialCredencialResponse` | 404 `NOT_FOUND` |

Notas:
- La **renovación** no tiene endpoint público: es automática (job programado) mientras el vínculo siga activo y sin señal de riesgo. Si hay señal de riesgo, no se renueva y el cliente se deriva a re-verificación (H3).
- `POST /api/v1/credenciales/{numero}/revocacion` es el endpoint que H3 consume (best-effort) ante bloqueo/desvinculación.
- `401 UNAUTHORIZED` y `403` son códigos sobre la tabla base de `error-contract`: `UnauthorizedException` → 401, y `EmisionNoAutorizadaException` (extends `ConflictException` o nueva base `ForbiddenException` → 403). Agregar `ForbiddenException` a `exception/` si se usa 403.

## Mappers

- `credencial.mapper.CredencialMapper` (MapStruct): `CredencialSesion` ↔ `EmitirCredencialResponse`/`ValidarCredencialResponse`; `CredencialAuditLog` ↔ `EventoCredencialResponse`.

## Repositories

- `CredencialRepository` (JpaRepository): `findByNumeroWhatsappAndEstado`, `findByJti`, `findByDocumentoTipoAndDocumentoNumero`. Índice único parcial en Postgres sobre `numero_whatsapp` donde `estado = 'ACTIVA'` (blindaje de unicidad de credencial activa por número).
- `CredencialAuditLogRepository`: `findByNumeroWhatsappOrderByFechaDesc`.

## Services

- `CredencialService`: emitir (valida confirmación de verificación+vínculo activo; invalida credencial activa anterior; genera JWT; persiste; audita), revocar (inmediato; audita), renovar (job), revocación masiva.
- `ValidacionService`: valida JWT (firma, exp, estado en BD no revocada) y devuelve a qué cliente corresponde.
- `RenovacionService`: job programado (`@Scheduled`) que renueva credenciales próximas a expirar sin señal de riesgo; si hay riesgo, no renueva y marca para re-verificación.
- `CredencialAuditService`: mismo contrato que H1/H3 (`registrar(accion, resultado, numeroWhatsapp, documentoTipo?, documentoNumero?, motivo?)`, `REQUIRES_NEW`) + `consultarHistorial`.

## Clientes externos

- `UsersServiceClient` (interfaz) + impl `RestClient`: `consultarEstado(tipo, numero): ClienteEstadoResponse` (para validar que el cliente existe y está activo al emitir/validar). Timeouts configurados en `RestClientConfig`.

## Error contract

- `exception/ErrorResponse`, `FieldError`, `ErrorCode` (agrega `UNAUTHORIZED` y `FORBIDDEN` a los 5 códigos base), `GlobalExceptionHandler`, `NotFoundException`, `ConflictException` + nuevas bases `UnauthorizedException` (401) y `ForbiddenException` (403).
- Ninguna respuesta de error incluye el token ni secretos.

## Tests

- Unit: `CredencialService` (emisión autorizada/no, reemplazo, revocación, renovación con/sin riesgo), `ValidacionService`, `RenovacionService` (mockeando repositorios y `UsersServiceClient`).
- Integración: MockMvc por endpoint + `@RestClientTest`/WireMock para H1. JaCoCo ≥ 80%.
- Un test de error por endpoint que verifique status + `$.code` + `$.timestamp`.

## Riesgos / decisiones pendientes

- **Identidad Java del servicio nueva** (`base_package`, `artifactId`, puerto): propuestos arriba por convención con H1/H3; el tech-lead debe confirmarlos y completar `project.yaml` antes de la TASK de bootstrap.
- **Confirmación de verificación/vínculo activo en emisión**: H3 confirma por el request (`vinculoActivo`+`identidadVerificada`). Alternativa: H2 consulta a H3 por HTTP. Se modela por flags en el request para no acoplar; si se prefiere consulta a H3, ajustar `client/` sin tocar el contrato HTTP.
- **Señal de riesgo**: booleano `riesgo` en la credencial; su origen (canal, motor de riesgo) se define luego. Por ahora H3 o el banco la setean vía revocación/emisión.
- **Revocación masiva**: alcance (todas, segmento, criterio de riesgo) queda parametrizable; default implementa "todas las activas" y "por cliente".
- **Renovación automática**: job `@Scheduled` con ventana configurable; en tests se puede disparar manualmente.
- **Secret JWT**: debe venir por variable de entorno; nunca commiteado. En dev, default inseguro solo en perfil `dev`.
- **Compatibilidad con H3**: H3 ya referencia `clients.credenciales-service.base-url` y `POST /api/v1/credenciales/{numero}/revocacion`. Este plan mantiene ese path para no romper H3.

## Dependencias con otros hitos

- **H1 (Servicio de Usuarios)**: H2 lo consume por HTTP (`UsersServiceClient`) para validar cliente al emitir/validar.
- **H3 (Servicio Canal WhatsApp)**: H3 solicita emisión (tras verificar identidad) y revocación (bloqueo/desvinculación). H2 no llama a H3.
- **H4/H5/H6 (productos)**: consumen `POST /api/v1/credenciales/validar` para autorizar operaciones.
