# Definition of Done — H4 Servicio de Cuentas (Piloto F1)

**Componente:** `wa-accounts-service`  
**Epic:** TASK-33  
**Release task:** TASK-40  
**Review:** APPROVED (TASK-39, 0 CRITICAL/HIGH)  
**Estado:** ✅ **DONE** — listo para integración H3 y consumo del bundle release

---

## Información de la feature

| Campo | Valor |
|-------|-------|
| Historia | H4 — Servicio de Cuentas |
| Módulo | `services/wa-accounts-service` |
| Artefacto Maven | `com.wa.banking:wa-accounts-service:0.0.1-SNAPSHOT` |
| Base package | `com.wa.banking.accounts` |
| Alcance release | Piloto F1 — listado + saldo |
| Endpoints | 2 (`/api/v1/accounts/**`) |
| Puerto | 8083 |
| Tests | 26 |
| Coverage (LINE) | ~86% (umbral mínimo 70%) |
| Build | `./mvnw clean verify` OK |

---

## Checklist quality gates (§13)

| Criterio | Estado | Evidencia |
|----------|--------|-----------|
| Compila | ✅ | `./mvnw -DskipTests compile` / verify OK |
| Tests pasan | ✅ | 26 tests (5 clases), verify verde |
| docker-compose Mongo | ✅ | `docker-compose.yml` — `mongo:7.0`, puerto 27017 |
| application.yml | ✅ | Perfil `local` default, URI `mongodb://localhost:27017/wa-accounts`, puerto 8083 |
| Endpoints con validación | ✅ | Filtro H2 + scope `bankUserId` en service |
| Error contract | ✅ | `GlobalExceptionHandler` → `{ code, message, details, timestamp }` |
| Actuator health | ✅ | `/actuator/health`, probes habilitados |
| Swagger / OpenAPI | ✅ | `/v3/api-docs`, `/swagger-ui/index.html`, ejemplos en anotaciones |
| Postman collection | ✅ | `postman/wa-accounts-service.json` |
| JaCoCo configurado | ✅ | `jacocoArgLine`, report HTML, check en verify |
| JaCoCo umbral LINE ≥ 70% | ✅ | `jacoco.minimum.line.coverage=0.70`, actual ~86% |
| README runbook | ✅ | Plan A (jar) + Plan B (spring-boot:run), curls, errores |
| Runtime-safe (Windows) | ✅ | Advertencia Git Bash, Plan A documentado |
| JavaDoc clases principales | ✅ | Controller, Service, Repository, Entity, DTO, Error handler |
| Review sin BLOCKERS | ✅ | TASK-39 APPROVED, 0 CRITICAL/HIGH |
| Bundle release export | ✅ | `backlog/exports/h4-servicio-cuentas/` |

---

## Criterios spec F1 (aceptación)

| Criterio | Estado |
|----------|--------|
| F1 — `GET /api/v1/accounts` listado titular | ✅ |
| F1 — `GET /api/v1/accounts/{id}/balance` saldo | ✅ |
| Auth — `Authorization` + `X-Credential-Id` obligatorios | ✅ |
| Auth — validate H2 antes de operaciones | ✅ |
| Auth — 401 con mensaje orientado al cliente | ✅ |
| Scope — solo cuentas del `bankUserId` H2 | ✅ |
| 404 — cuenta ajena o inexistente | ✅ |
| Stub H2 configurable para local | ✅ |
| Seed demo en perfil local | ✅ |
| F2–F4 fuera de alcance | ✅ |
| Sin exposición de Entity en API | ✅ |
| MapStruct Entity ↔ DTO | ✅ |

---

## Integración downstream / upstream

| Relación | Integración | Estado |
|----------|-------------|--------|
| H3 consumidor | `GET /api/v1/accounts*` con headers sesión | ✅ Documentado |
| H2 dependencia | `POST /validate` en `:8082` | ✅ Documentado |
| Stub local | `integration.session.stub-enabled=true` | ✅ Default local |

---

## Archivos principales

### Código fuente (`src/main/java`) — 29 archivos

| Capa | Archivos clave |
|------|----------------|
| Controller | `AccountControllerV1.java` |
| Service | `AccountService.java`, `AccountServiceImpl.java` |
| Repository | `AccountRepository.java` |
| Entity | `AccountEntity.java` |
| DTO v1 | `AccountResponse`, `AccountListResponse`, `AccountBalanceResponse` |
| Mapper | `AccountMapper.java` |
| Error | `GlobalExceptionHandler.java`, `ErrorResponse.java`, `ErrorCode.java` |
| Integration | `SessionCredentialValidator.java`, `SessionValidationClient.java` |
| Config | `SecurityFilterConfig.java`, `OpenApiConfig.java`, `AccountSeedDataLoader.java` |
| App | `AccountsApplication.java` |

### Tests (`src/test/java`) — 8 archivos, 26 tests

| Clase | Rol |
|-------|-----|
| `AccountControllerIT` | MockMvc + Testcontainers: HTTP 200/401/404 |
| `AccountServiceTest` | Unit: listado, saldo, scope titular |
| `SessionCredentialValidatorTest` | Unit: stub/real validate |
| `SessionValidationClientTest` | Unit: cliente HTTP H2 |
| `GlobalExceptionHandlerIT` | Error contract |
| `AbstractMongoIntegrationTest` | Base Testcontainers |
| `TestAccountData` / `ErrorContractTestController` | Soporte test |

### Infra y docs

| Archivo | Propósito |
|---------|-----------|
| `docker-compose.yml` | Mongo 7.0 |
| `application.yml` / `application-local.yml` | Perfil local, puerto 8083, integración H2 |
| `README.md` | Runbook, curls, Postman |
| `postman/wa-accounts-service.json` | Colección F1 |
| `backlog/exports/h4-servicio-cuentas/openapi.yaml` | Contrato exportado |

---

## Pipeline completado

| Step | Task | Resultado |
|------|------|-----------|
| plan | TASK-34 | OK |
| scaffold | TASK-35 | OK |
| build | TASK-36 | OK |
| test | TASK-37 | 26 tests, verify OK, ~86% LINE |
| docs | TASK-38 | README, OpenAPI, Postman |
| review | TASK-39 | APPROVED |
| release | TASK-40 | Bundle export (este directorio) |

---

## Status final

**H4 F1 cerrado para integración.** H3 puede consumir `:8083` reenviando credencial H2. Bundle listo en `backlog/exports/h4-servicio-cuentas/`.
