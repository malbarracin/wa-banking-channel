# Definition of Done — H2 Servicio de Acceso y Sesión

**Componente:** `wa-auth-service`  
**Epic:** TASK-22  
**Release task:** TASK-29  
**Review:** APPROVED iter 1 (TASK-28)  
**Estado:** ✅ **DONE** — listo para integración con H3 (stub off) y H4–H6 (validate)

---

## Información de la feature

| Campo | Valor |
|-------|-------|
| Historia | H2 — Servicio de Acceso y Sesión |
| Módulo | `services/wa-auth-service` |
| Artefacto Maven | `com.wa.banking:wa-auth-service:0.0.1-SNAPSHOT` |
| Base package | `com.wa.banking.auth` |
| Endpoints | 8 (`/api/v1/sessions/credentials/**`) |
| Flujos | A1, A2, A3 + validate + auditoría |
| Tests | 40 |
| Coverage (LINE) | ~85% (umbral mínimo 70%) |
| Build | `./mvnw clean verify` OK |

---

## Checklist quality gates (§13)

| Criterio | Estado | Evidencia |
|----------|--------|-----------|
| Compila | ✅ | `./mvnw -DskipTests compile` / verify OK |
| Tests pasan | ✅ | 40 tests (5 clases), verify verde |
| docker-compose Mongo | ✅ | `docker-compose.yml` — `mongo:7.0`, puerto 27017 |
| application.yml | ✅ | Perfil `local` default, URI `mongodb://localhost:27017/wa-auth`, puerto 8082 |
| Endpoints con validación | ✅ | `@Valid` en DTOs, Bean Validation |
| Error contract | ✅ | `GlobalExceptionHandler` → `{ code, message, details, timestamp }` |
| Actuator health | ✅ | `/actuator/health`, probes habilitados |
| Swagger / OpenAPI | ✅ | `/v3/api-docs`, `/swagger-ui/index.html`, ejemplos en anotaciones |
| Postman collection | ✅ | `postman/wa-auth-service.postman_collection.json` |
| JaCoCo configurado | ✅ | `jacocoArgLine`, report HTML, check en verify |
| JaCoCo umbral LINE ≥ 70% | ✅ | `jacoco.minimum.line.coverage=0.70`, actual ~85% |
| README runbook | ✅ | Plan A (jar) + Plan B (spring-boot:run), curls, errores |
| Runtime-safe (Windows) | ✅ | Advertencia Git Bash, Plan A documentado |
| JavaDoc clases principales | ✅ | Controller, Service, Repository, Entity, DTO, Error handler |
| Review sin BLOCKERS | ✅ | TASK-28 APPROVED iter 1 (4 fixes, RN1 corregida) |

---

## Criterios spec §7 (aceptación)

| Criterio | Estado |
|----------|--------|
| A1 — Emisión con `identityVerified=true` (RN1) | ✅ |
| A1 — Rechazo si identidad no verificada (400) | ✅ |
| A1 — Reemplazo credencial ACTIVE previa (RN2) | ✅ |
| A2 — Renovación TTL si ACTIVE | ✅ |
| A3 — Revocación inmediata DELETE (H3) | ✅ |
| A3 — Revocación con motivo POST /revoke | ✅ |
| A3 — Revocación masiva por bankUserId | ✅ |
| Validate — H4–H6 valid/revoked/expired/wrong-token | ✅ |
| Validate — `valid=false` sin error HTTP en inválida | ✅ |
| GET status — metadata sin token | ✅ |
| GET audit — historial paginado | ✅ |
| Auditoría sin secretos en logs/audit | ✅ |
| Compatibilidad H3 SessionClient (issue + delete) | ✅ |
| Fuera de alcance respetado (JWT, WhatsApp, productos) | ✅ |

---

## Integración downstream

| Consumidor | Integración | Estado |
|------------|-------------|--------|
| H3 | `integration.session.base-url=http://localhost:8082`, `stub-enabled=false` | ✅ Documentado |
| H3 | POST issue con `identityVerified: true` (RN1) | ✅ Contrato verificado |
| H4–H6 | POST `/validate` con `credentialId` + `token` | ✅ Documentado |

---

## Archivos principales

### Código fuente (`src/main/java`) — 43 archivos

| Capa | Archivos clave |
|------|----------------|
| Controller | `SessionCredentialControllerV1.java` |
| Service | `SessionCredentialService.java`, `SessionCredentialServiceImpl.java`, `CredentialTokenService.java` |
| Repository | `SessionCredentialRepository.java`, `SessionAuditEntryRepository.java`, `SessionMongoConfig.java` |
| Entity | `SessionCredentialEntity.java`, `SessionAuditEntryEntity.java`, enums |
| DTO v1 | `IssueCredentialRequest`, `ValidateCredentialRequest/Response`, `CredentialStatusResponse`, etc. |
| Mapper | `SessionCredentialMapper.java` |
| Error | `GlobalExceptionHandler.java`, `ErrorResponse.java`, `ErrorCode.java` |
| Integration | `UsersClient.java`, `IntegrationConfig.java`, `SessionCredentialProperties.java` |
| Config | `OpenApiConfig.java`, `WaAuthServiceApplication.java` |

### Tests (`src/test/java`) — 6 archivos, 40 tests

| Clase | Rol |
|-------|-----|
| `SessionCredentialServiceTest` | Unit: A1–A3, validate, revoke-by-user |
| `SessionCredentialControllerV1Test` | MockMvc: HTTP + error contract |
| `SessionCredentialIntegrationTest` | Testcontainers Mongo: flujo E2E |
| `CredentialTokenServiceTest` | Hash/verificación token |
| `GlobalExceptionHandlerTest` | Error contract |
| `SessionCredentialTestFixtures` | Fixtures compartidos |

### Infra y docs

| Archivo | Propósito |
|---------|-----------|
| `docker-compose.yml` | Mongo 7.0 |
| `application.yml` | Perfil local, puerto 8082, TTL, actuator |
| `openapi.yaml` | Contrato OpenAPI 3.0.1 |
| `README.md` | Runbook, curls, Postman |
| `postman/wa-auth-service.postman_collection.json` | Colección con flujo H2 |

---

## Pipeline completado

| Step | Task | Resultado |
|------|------|-----------|
| plan | TASK-23 | OK |
| scaffold | TASK-24 | OK |
| build | TASK-25 | OK |
| test | TASK-26 | 40 tests, verify OK, ~85% LINE |
| docs | TASK-27 | README, OpenAPI, Postman |
| review | TASK-28 | APPROVED iter 1 |
| release | TASK-29 | Bundle export (este directorio) |

---

## Status final

**H2 cerrado para integración.** H3 puede desactivar stub y apuntar a `:8082`. H4–H6 pueden consumir `POST /validate`.
