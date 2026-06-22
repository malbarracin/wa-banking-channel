# Stats — Corrida pipeline H1 Servicio de Usuarios

**Session:** `h1-servicio-usuarios`  
**Generado:** 2026-06-22  
**Agente:** doc-generator (TASK-8 release)

---

## Resumen ejecutivo

| Métrica | Valor |
|---------|-------|
| Componente | `wa-users-service` |
| Versión | `0.0.1-SNAPSHOT` |
| Endpoints REST | 6 |
| Tests | 18 |
| Clases test | 3 |
| Clases main (Java) | 30 |
| Coverage LINE | ~90% (200/223) |
| Umbral JaCoCo | 70% |
| `./mvnw clean verify` | OK |
| Review | APPROVED iter 1 |
| Blockers abiertos | 0 |

---

## Pipeline timeline

| Step | Task | Agente / fase | Resultado |
|------|------|---------------|-----------|
| validate | — | orchestrator | OK |
| plan | TASK-2 | analyst | base_package, U1–U4, coverage 0.70 |
| scaffold | TASK-3 | scaffold | Spring Boot 3.3.5, Mongo, JaCoCo |
| build | TASK-4 | build | 6 endpoints, auditoría |
| test | TASK-5 | test | 18 tests, verify OK |
| docs | TASK-6 | docs | README, PostAPI, OpenAPI examples |
| review | TASK-7 | review | APPROVED iter 1 (6 fixes) |
| release | TASK-8 | doc-generator | Bundle export |

**Inicio sesión:** 2026-06-22T21:41:01.942Z  
**Review aprobado:** 2026-06-22T21:45:00.000Z

---

## Código

| Categoría | Cantidad |
|-----------|----------|
| Archivos `src/main/java` | 30 |
| Archivos `src/test/java` | 3 |
| Recursos config | 2 (`application.yml`, `application-docker.yml`) |
| Infra | `docker-compose.yml`, `Dockerfile` (si presente) |
| Docs repo | `README.md`, Postman collection |

### Endpoints por flujo

| Flujo | Endpoints |
|-------|-----------|
| U1 | 1 |
| U2 | 2 |
| U3 | 1 |
| U4 | 1 |
| Auditoría | 1 |
| **Total** | **6** |

---

## Tests

| Clase | Tests aprox. |
|-------|--------------|
| `UserControllerV1IntegrationTest` | 10 |
| `StatusTransitionPolicyTest` | 5 |
| `UserServiceImplTest` | 4 |
| **Total** | **18** |

**Framework:** JUnit 5, Spring Boot Test, Testcontainers Mongo

---

## Coverage (JaCoCo)

Fuente: `target/site/jacoco/jacoco.csv` (post-verify)

| Counter | Covered | Missed | Ratio |
|---------|---------|--------|-------|
| LINE | 200 | 23 | **89.7%** |

Paquetes con menor cobertura:
- `UserMapperV1Impl` — generated MapStruct (branches parciales)
- `GlobalExceptionHandler` — paths 500 no ejercitados en tests
- `WaUsersServiceApplication` — main class

Umbral POM: `${jacoco.minimum.line.coverage}` = **0.70** — cumplido.

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

## Review (TASK-7)

| Iteración | Veredicto | Notas |
|-----------|-----------|-------|
| 0 | NEEDS_CHANGES | 1 HIGH (DuplicateKeyException), 3 MEDIUM, 2 LOW |
| 1 | **APPROVED** | 6 fixes verificados, verify OK |

---

## Bundle export (este directorio)

| Archivo | Líneas aprox. | Tipo |
|---------|---------------|------|
| `handoff.md` | ~180 | Integración |
| `dod.md` | ~150 | Cierre |
| `manifest.yaml` | ~95 | Índice |
| `stats.md` | ~120 | Métricas |
| `openapi.yaml` | 731 | Contrato REST |

**OpenAPI export:** snapshot live desde `GET /v3/api-docs.yaml` (OpenAPI 3.0.1, ~24 KB).

---

## Comandos de reproducción

```powershell
# Mongo
docker compose up -d mongo

# Build + tests + coverage
./mvnw clean verify

# Run (Plan A — Windows)
./mvnw -DskipTests package
java -jar target/wa-users-service-0.0.1-SNAPSHOT.jar
```
