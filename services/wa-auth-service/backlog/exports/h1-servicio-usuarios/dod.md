# Definition of Done — H1 Servicio de Usuarios

**Componente:** `wa-users-service`  
**Spec Notion:** [36875bb08ca78197a853e85505bf49a5](https://www.notion.so/36875bb08ca78197a853e85505bf49a5)  
**Epic:** TASK-1  
**Release task:** TASK-8  
**Review:** APPROVED iter 1 (TASK-7)  
**Estado:** ✅ **DONE** — listo para integración con H3

---

## Información de la feature

| Campo | Valor |
|-------|-------|
| Historia | H1 — Servicio de Usuarios |
| Módulo | `services/wa-users-service` |
| Artefacto Maven | `com.wa.banking:wa-users-service:0.0.1-SNAPSHOT` |
| Base package | `com.wa.banking.users` |
| Endpoints | 6 (`/api/v1/users/**`) |
| Flujos | U1, U2, U3, U4 + auditoría |
| Tests | 18 |
| Coverage (LINE) | ~90% (umbral mínimo 70%) |
| Build | `./mvnw clean verify` OK |

---

## Checklist quality gates (§13)

| Criterio | Estado | Evidencia |
|----------|--------|-----------|
| Compila | ✅ | `./mvnw -DskipTests compile` / verify OK |
| Tests pasan | ✅ | 18 tests (3 clases), verify verde |
| docker-compose Mongo | ✅ | `docker-compose.yml` — `mongo:7.0`, puerto 27017 |
| application.yml | ✅ | Perfil `local` default, URI Mongo configurada |
| Endpoints con validación | ✅ | `@Valid` en DTOs, Bean Validation |
| Error contract | ✅ | `GlobalExceptionHandler` → `{ code, message, details, timestamp }` |
| Actuator health | ✅ | `/actuator/health`, probes habilitados |
| Swagger / OpenAPI | ✅ | `/v3/api-docs`, `/swagger-ui/index.html`, ejemplos en anotaciones |
| Postman collection | ✅ | `postman/wa-users-service.postman_collection.json` |
| JaCoCo configurado | ✅ | `jacocoArgLine`, report HTML, check en verify |
| JaCoCo umbral LINE ≥ 70% | ✅ | `jacoco.minimum.line.coverage=0.70`, actual ~90% |
| README runbook | ✅ | Plan A (jar) + Plan B (spring-boot:run), curls, errores |
| Runtime-safe (Windows) | ✅ | Advertencia Git Bash, Plan A documentado |
| JavaDoc clases principales | ✅ | Controller, Service, Repository, Entity, DTO, Error handler |
| Review sin BLOCKERS | ✅ | TASK-7 APPROVED iter 1 |

---

## Criterios spec §7 (aceptación)

| Criterio | Estado |
|----------|--------|
| U1 — Alta usuario ACTIVE con ID | ✅ |
| U1 — Rechazo documento duplicado (400) | ✅ |
| U2 — Consulta por ID y documento | ✅ |
| U2 — 404 usuario inexistente | ✅ |
| U3 — PATCH solo campos permitidos | ✅ |
| U4 — Cambio estado con auditoría | ✅ |
| `canLinkChannel=false` en SUSPENDED/SOFT_DELETED | ✅ |
| Unicidad documento (índice compuesto) | ✅ |
| Auditoría CREATED / UPDATED / STATUS_CHANGED | ✅ |
| Fuera de alcance respetado (JWT, WhatsApp, sync) | ✅ |

---

## Archivos principales

### Código fuente (`src/main/java`) — 30 archivos

| Capa | Archivos clave |
|------|----------------|
| Controller | `UserController.java` |
| Service | `UserService.java`, `UserServiceImpl.java`, `StatusTransitionPolicy.java` |
| Repository | `BankUserRepository.java`, `UserAuditRepository.java` |
| Entity | `BankUserEntity.java`, `UserAuditEntryEntity.java`, enums |
| DTO v1 | `CreateUserRequestV1`, `UpdateUserRequestV1`, `ChangeUserStatusRequestV1`, `UserResponseV1`, `UserAuditResponseV1` |
| Mapper | `UserMapperV1.java` |
| Error | `GlobalExceptionHandler.java`, `ErrorResponse.java`, `ErrorCode.java` |
| Config | `OpenApiConfig.java`, `OpenApiExamples.java`, `SpringDataWebConfig.java` |

### Tests (`src/test/java`) — 3 archivos, 18 tests

| Archivo | Alcance |
|---------|---------|
| `UserControllerV1IntegrationTest.java` | Integración Testcontainers Mongo, flujos U1–U4 |
| `UserServiceImplTest.java` | Unitarios service |
| `StatusTransitionPolicyTest.java` | Política de transiciones |

### Infra y docs

| Archivo | Propósito |
|---------|-----------|
| `pom.xml` | Spring Boot 3.3.5, JaCoCo, MapStruct, springdoc |
| `docker-compose.yml` | Mongo 7 + app opcional |
| `README.md` | Runbook completo |
| `postman/wa-users-service.postman_collection.json` | Colección flujo U1–U4 |
| `src/main/resources/application.yml` | Config local/docker |

---

## Resumen de tests y coverage

```
./mvnw clean verify
```

| Métrica | Valor |
|---------|-------|
| Tests totales | 18 |
| Umbral JaCoCo LINE | 70% |
| Coverage LINE (JaCoCo) | ~90% (200/223 líneas instrumentadas) |
| Reporte | `target/site/jacoco/index.html` |

---

## Pipeline steps completados

| Step | Task | Estado |
|------|------|--------|
| validate | — | ✅ |
| plan | TASK-2 | ✅ |
| scaffold | TASK-3 | ✅ |
| build | TASK-4 | ✅ |
| test | TASK-5 | ✅ |
| docs | TASK-6 | ✅ |
| review | TASK-7 | ✅ APPROVED iter 1 |
| release | TASK-8 | ✅ (este bundle) |

---

## Status final

**H1 cerrado para integración.** El componente puede consumirse por H3 (Canal WhatsApp) vía REST `/api/v1/users/**` y contrato OpenAPI incluido en el bundle.
