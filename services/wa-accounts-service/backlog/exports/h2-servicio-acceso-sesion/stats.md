# Stats — Corrida pipeline H2 Servicio de Acceso y Sesión

**Session:** `h2-servicio-acceso-sesion`  
**Generado:** 2026-06-23  
**Agente:** doc-generator (TASK-29 release)

---

## Resumen ejecutivo

| Métrica | Valor |
|---------|-------|
| Componente | `wa-auth-service` |
| Versión | `0.0.1-SNAPSHOT` |
| Endpoints REST | 8 |
| Tests | 40 |
| Clases test | 5 (+ 1 fixtures) |
| Clases main (Java) | 43 |
| Coverage LINE | ~85% |
| Umbral JaCoCo | 70% |
| `./mvnw clean verify` | OK |
| Review | APPROVED iter 1 |
| Blockers abiertos | 0 |

---

## Pipeline timeline

| Step | Task | Agente / fase | Resultado |
|------|------|---------------|-----------|
| validate | — | orchestrator | OK |
| plan | TASK-23 | analyst | base_package, A1–A3, coverage 0.70 |
| scaffold | TASK-24 | scaffold | Spring Boot 3.3.5, Mongo, JaCoCo, puerto 8082 |
| build | TASK-25 | build | 8 endpoints, auditoría, token hash |
| test | TASK-26 | test | 40 tests, verify OK, ~85% LINE |
| docs | TASK-27 | docs | README, Postman, OpenAPI examples |
| review | TASK-28 | review | APPROVED iter 1 (4 fixes, RN1) |
| release | TASK-29 | doc-generator | Bundle export |

**Inicio sesión:** 2026-06-23T20:39:05.652Z  
**Review aprobado:** TASK-28 APPROVED iter 1

---

## Código

| Categoría | Cantidad |
|-----------|----------|
| Archivos `src/main/java` | 43 |
| Archivos `src/test/java` | 6 |
| Recursos config | 3 (`application.yml`, `application-docker.yml`, `application-test.yml`) |
| Infra | `docker-compose.yml` |
| Docs repo | `README.md`, `openapi.yaml`, Postman collection |

### Endpoints por flujo

| Flujo | Endpoints |
|-------|-----------|
| A1 — Emisión | 1 |
| A2 — Renovación | 1 |
| A3 — Revocación | 3 |
| Validate (H4–H6) | 1 |
| Consulta / auditoría | 2 |
| **Total** | **8** |

---

## Tests

| Clase | Tests aprox. |
|-------|--------------|
| `SessionCredentialServiceTest` | 19 |
| `SessionCredentialControllerV1Test` | 8 |
| `GlobalExceptionHandlerTest` | 5 |
| `CredentialTokenServiceTest` | 5 |
| `SessionCredentialIntegrationTest` | 4 |
| **Total** | **40** |

**Framework:** JUnit 5, Spring Boot Test, Testcontainers Mongo, MockMvc

---

## Coverage (JaCoCo)

Fuente: `target/site/jacoco/index.html` (post-verify, TASK-26)

| Counter | Ratio reportado |
|---------|-----------------|
| LINE | **~85%** |

Umbral POM: `${jacoco.minimum.line.coverage}` = **0.70** — cumplido.

Paquetes con menor cobertura típica:
- `SessionCredentialMapperImpl` — generated MapStruct
- `GlobalExceptionHandler` — paths 500 no ejercitados en tests
- `WaAuthServiceApplication` — main class
- `UsersClient` — integración H1 opcional deshabilitada en tests

---

## Stack y dependencias

| Tecnología | Versión |
|------------|---------|
| Java | 21 |
| Spring Boot | 3.3.5 |
| MapStruct | 1.5.5.Final |
| springdoc-openapi | 2.3.0 |
| MongoDB (docker) | 7.0 |
| JaCoCo plugin | 0.8.12 |

---

## Configuración runtime

| Propiedad | Valor |
|-----------|-------|
| `server.port` | 8082 |
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/wa-auth` |
| `session.credential.ttl-hours` | 24 |
| `integration.users.enabled` | false (local) |

---

## Integración H3

| Propiedad H3 | Valor integración |
|--------------|-------------------|
| `integration.session.base-url` | `http://localhost:8082` |
| `integration.session.stub-enabled` | `false` |
| POST issue `identityVerified` | `true` (RN1 obligatorio) |

---

## Review (TASK-28)

| Iteración | Veredicto | Notas |
|-----------|-----------|-------|
| 1 | **APPROVED** | 4 fixes aplicados, RN1 corregida, compatibilidad H3 verificada |

---

## Bundle export (este directorio)

| Archivo | Líneas aprox. | Tipo |
|---------|---------------|------|
| `handoff.md` | ~320 | Integración |
| `dod.md` | ~180 | Cierre |
| `manifest.yaml` | ~130 | Índice |
| `stats.md` | ~150 | Métricas |
| `openapi.yaml` | 801 | Contrato REST |

**OpenAPI export:** copia de `openapi.yaml` raíz del proyecto (OpenAPI 3.0.1).

---

## Comandos de reproducción

```powershell
# Mongo
docker compose up -d mongo

# Build + tests + coverage
./mvnw clean verify

# Run (Plan A — Windows)
./mvnw -DskipTests package
java -jar target/wa-auth-service-0.0.1-SNAPSHOT.jar
```
