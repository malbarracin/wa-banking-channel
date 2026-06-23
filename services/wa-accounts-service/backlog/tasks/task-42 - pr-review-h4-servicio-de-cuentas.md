---
id: TASK-42
title: "[pr-review] H4 — Servicio de Cuentas"
status: To Do
priority: medium
created: "2026-06-23T23:52:49.144Z"
parent: TASK-33
labels:
assignee:
---

# [pr-review] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: pr-review

**Objetivo**: Revisar en GitHub (modo readonly) el Pull Request abierto en el step `pr`, validando seguridad, arquitectura MVC, calidad Java/Spring y contrato REST/Mongo del piloto F1 H4; publicar comentarios inline y veredicto (`APPROVE` | `REQUEST_CHANGES` | `COMMENT`).

**Contexto**: Implementación F1 completa y review interno APPROVED (TASK-39, 0 CRITICAL/HIGH). Este step es la revisión formal en GitHub del diff del PR — independiente del review local. Consumidor H3 depende de auth H2 correcta y aislamiento por `bankUserId`. Referencias: `.soma/spec-content.md` (§5–7, §11), `dod.md`, `handoff.md`, `openapi.yaml` del bundle.

**Descripcion detallada**:

1. Resolver PR (prerrequisito: step `pr` completado o `pr_url`/`pr_number` en tarea):
   ```bash
   gh pr view <number|url> --json number,url,title,state,baseRefName,headRefName,headRefOid,files
   ```
   Si solo hay branch: `gh pr list --head <branch> --base develop --json number,url`

2. Obtener diff completo — **revisar todos los archivos**, no solo Java:
   ```bash
   gh pr diff <number>
   gh pr view <number> --json files
   ```
   Prioridad obligatoria:
   - `src/main/java/**` — controller, service, filter/auth, integration H2, error handler, entity, mapper
   - `src/test/java/**` — IT MockMvc + Testcontainers, unit tests auth/scope
   - `pom.xml`, `application*.yml`, `docker-compose.yml`

3. Checklist de revisión (mapear hallazgos a `target_step`):

   | Área | Qué verificar en este PR F1 |
   |------|----------------------------|
   | **Seguridad (03)** | Sin JWT/credencial → 401 sin consultar cuentas; validate H2 antes de operaciones; no secretos en YAML/logs; no concatenación de input en queries Mongo; scope estricto por `bankUserId` de H2 (404 para ajena/inexistente) |
   | **Arquitectura (04)** | Capas MVC respetadas; Entity no expuesta en API; DTO + MapStruct; lógica de negocio en Service, no Controller; inyección por constructor |
   | **Calidad (05)** | Sin `Optional.get()` ciego; sin catch genérico fuera de `@RestControllerAdvice`; `@Slf4j` sin PII; error contract sin stacktrace |
   | **Spring API (06)** | `@Valid`/filtro en entrada; códigos HTTP coherentes (200/401/404); actuator health; OpenAPI anotado; JaCoCo aislado (`jacocoArgLine`) |

4. Contrastar diff vs spec F1 y `dod.md`:
   - Solo 2 endpoints REST en alcance
   - Mensaje 401: *"Por seguridad, verificá tu identidad para ver tus cuentas."*
   - F2–F4 no deben aparecer como endpoints implementados
   - Tests cubren camino OK + error típico (401, 404)

5. Publicar en GitHub (`02-gh-pr-review-workflow`):
   - Comentario **inline obligatorio** por cada issue **CRITICAL** o **HIGH** (archivo + línea + impacto + sugerencia concreta)
   - Resumen en cuerpo del review con conteo por severidad
   - Veredicto según `07-pr-review-verdict`:
     - 0 CRITICAL/HIGH → `gh pr review --approve`
     - ≥1 CRITICAL/HIGH → `gh pr review --request-changes`
     - Solo preguntas → `gh pr review --comment`
   - Verificar: `gh pr view --json reviews,reviewDecision`

6. Emitir salida JSON + markdown según `08-pr-review-output-format` (tabla ISSUES, FIX_ROUTING si NEEDS_CHANGES).

**Entregables**:
- Review publicado en GitHub con `gh_review_event` visible
- Comentarios inline en issues CRITICAL/HIGH (si los hay)
- JSON: `verdict`, `summary.{critical,high,medium,low}`, `issues[]` con `posted_to_pr`
- Markdown RESUMEN + ISSUES TABLE + HANDOFF

**Criterios de aceptacion**:
- [ ] PR abierto y diff accesible; agente **no** modificó código local
- [ ] Revisados **todos** los archivos del diff (Java prod + test + pom + config)
- [ ] Validado auth H2 + aislamiento `bankUserId` + error contract vs spec §11 y `handoff.md`
- [ ] Cada CRITICAL/HIGH tiene comentario inline en la línea exacta del PR
- [ ] Veredicto gh alineado: no `APPROVE` con HIGH/CRITICAL abiertos
- [ ] `reviewDecision` en GitHub refleja el veredicto emitido
- [ ] JSON con `success: true` solo si review quedó publicado en GitHub

**Reglas aplicables**: `01-output-format`, `01-pr-review-scope`, `02-change-policy`, `02-gh-pr-review-workflow`, `03-java-security-owasp`, `04-java-architecture-patterns`, `05-java-code-quality`, `06-java-spring-api`, `07-pr-review-verdict`, `08-pr-review-output-format`

**Definition of Done**: Review visible en GitHub con veredicto coherente (`APPROVED` → merge humano/CI; `NEEDS_CHANGES` → orquestador enruta fixes a `build`/`test`/`docs`/`pr` según `target_step`). Review solo en chat = **BLOCKED**.
<!-- SECTION:DESCRIPTION:END -->
