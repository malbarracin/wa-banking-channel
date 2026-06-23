# Stats — Corrida pipeline H4 Servicio de Cuentas

**Session:** `h4-servicio-cuentas`  
**Generado:** 2026-06-23  
**Agente:** doc-generator (TASK-40 release)

---

## Resumen ejecutivo

| Métrica | Valor |
|---------|-------|
| Componente | `wa-accounts-service` |
| Versión | `0.0.1-SNAPSHOT` |
| Alcance | Piloto F1 (listado + saldo) |
| Endpoints REST | 2 |
| Tests | 26 |
| Clases test | 5 (+ 3 soporte) |
| Clases main (Java) | 29 |
| Coverage LINE | ~86% |
| Umbral JaCoCo | 70% |
| `./mvnw clean verify` | OK |
| Review | APPROVED (TASK-39) |
| Blockers abiertos | 0 |

---

## Pipeline timeline

| Step | Task | Agente / fase | Resultado |
|------|------|---------------|-----------|
| validate | — | orchestrator | OK |
| plan | TASK-34 | analyst | F1 acotado, integración H2/H3 |
| scaffold | TASK-35 | scaffold | Spring Boot 3.3.5, Mongo, JaCoCo, puerto 8083 |
| build | TASK-36 | build | 2 endpoints F1, filtro auth H2 |
| test | TASK-37 | test | 26 tests, verify OK, ~86% LINE |
| docs | TASK-38 | docs | README, Postman, OpenAPI examples |
| review | TASK-39 | review | APPROVED, 0 CRITICAL/HIGH |
| release | TASK-40 | doc-generator | Bundle export |

**Inicio sesión:** 2026-06-23T21:35:48.061Z  
**Review aprobado:** TASK-39 APPROVED

---

## Código

| Categoría | Cantidad |
|-----------|----------|
| Archivos `src/main/java` | 29 |
| Archivos `src/test/java` | 8 |
| Recursos config | 4 (`application.yml`, `application-local.yml`, `application-docker.yml`, `application-test.yml`) |
| Infra | `docker-compose.yml` |
| Docs repo | `README.md`, Postman collection |
| Seed data | `src/main/resources/data/accounts-seed.json` |

### Endpoints por flujo

| Flujo | Endpoints |
|-------|-----------|
| F1 — Listado cuentas | 1 |
| F1 — Consulta saldo | 1 |
| **Total F1** | **2** |

---

## Tests

| Clase | Tests |
|-------|-------|
| `AccountServiceTest` | 6 |
| `SessionCredentialValidatorTest` | 6 |
| `AccountControllerIT` | 6 |
| `GlobalExceptionHandlerIT` | 6 |
| `SessionValidationClientTest` | 2 |
| **Total** | **26** |

**Framework:** JUnit 5, Spring Boot Test, Testcontainers Mongo, MockMvc

---

## Coverage (JaCoCo)

Fuente: `target/site/jacoco/index.html` (post-verify, TASK-37)

| Counter | Ratio reportado |
|---------|-----------------|
| LINE | **~86%** (178/206 líneas) |
| INSTRUCTION | ~86% |
| BRANCH | ~61% |

Umbral POM: `${jacoco.minimum.line.coverage}` = **0.70** — cumplido.

Paquetes con menor cobertura típica:
- `com.wa.banking.accounts.config` — filtro auth paths edge, seed loader
- `AccountsApplication` — main class
- `AccountMapperImpl` — generated MapStruct parcial

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
| `server.port` | 8083 |
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/wa-accounts` |
| `integration.session.base-url` | `http://localhost:8082` |
| `integration.session.stub-enabled` | `true` (local) |

---

## Integración H2 / H3

| Relación | Detalle |
|----------|---------|
| H2 validate | `POST :8082/api/v1/sessions/credentials/validate` |
| H3 consumidor | Reenvía `Authorization` + `X-Credential-Id` a `:8083` |
| Stub local | Acepta `cred-demo-001` / `demo-token` → `user-demo-001` |

---

## Review (TASK-39)

| Veredicto | Notas |
|-----------|-------|
| **APPROVED** | 0 CRITICAL/HIGH; auth H2, error contract, data isolation OK |

---

## Bundle export (este directorio)

| Archivo | Tipo |
|---------|------|
| `handoff.md` | Integración H3 |
| `dod.md` | Cierre quality gates |
| `pr.md` | PR summary |
| `manifest.yaml` | Índice bundle |
| `stats.md` | Métricas pipeline |
| `openapi.yaml` | Contrato F1 piloto |

**OpenAPI export:** snapshot alineado con `/v3/api-docs.yaml` del servicio en `:8083`.

---

## Comandos de reproducción

```powershell
# Mongo
docker compose up -d mongo

# Build + tests + coverage
./mvnw clean verify

# Run (Plan A — Windows)
./mvnw -DskipTests package
java -jar target/wa-accounts-service-0.0.1-SNAPSHOT.jar
```
