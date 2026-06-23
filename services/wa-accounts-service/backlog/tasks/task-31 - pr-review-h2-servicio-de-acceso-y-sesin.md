---
id: TASK-31
title: "[pr-review] H2 — Servicio de Acceso y Sesión"
status: Done
priority: medium
created: "2026-06-23T21:10:00.000Z"
parent: TASK-22
---

# [pr-review] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: pr-review

**Objetivo**: Revisar el Pull Request de H2 publicado en GitHub, evaluar seguridad/arquitectura/calidad del código Java, publicar comentarios inline en el PR y emitir veredicto formal (`APPROVE` | `REQUEST_CHANGES` | `COMMENT`).

**Contexto**: PR creado en step `pr` anterior. Review interno TASK-28 ya aprobó iter 1 con 4 fixes (RN1, compat H3). Este step es review **formal en GitHub** — readonly, sin modificar código. Alcance: microservicio `wa-auth-service` con gestión de credenciales de sesión, token opaco BCrypt, endpoints sin JWT/OAuth (limitación documentada para piloto).

**Descripcion detallada**:

1. **Identificar PR**:
   - `gh pr view <NUMBER>` — obtener `url`, `headRefOid`, `baseRefName`, `files`
   - Confirmar PR abierto (no merged/closed)

2. **Diff completo**:
   - `gh pr diff <NUMBER>`
   - `gh pr view <NUMBER> --json files`
   - Revisar **todos** los hunks; foco obligatorio:
     - `src/main/java/**` — Controller, Service, Repository, Entity, DTO, Mapper, Error handler, Integration
     - `src/test/java/**` — 40 tests (unit, MockMvc, Testcontainers)
     - `pom.xml`, `application*.yml`, `docker-compose.yml`

3. **Checklist de review H2** (desde spec + artefactos release):

   **Seguridad (rule `03-java-security-owasp`)**:
   - Token opaco: no loguear token/pepper; hash BCrypt en persistencia
   - `session.credential.pepper` default `change-me-in-production` — marcar si commiteado sin override env (MEDIUM/HIGH según contexto)
   - Endpoints sin auth explícita — documentado como limitación piloto; evaluar si es HIGH para endpoints mutantes
   - Actuator: `/actuator/env` no expuesto en prod profile
   - RN5: token solo en respuesta emisión; audit sin secretos
   - Validación input: `@Valid`, no concatenación en queries Mongo

   **Arquitectura (rule `04-java-architecture-patterns`)**:
   - Capas MVC respetadas: `SessionCredentialControllerV1` → `SessionCredentialServiceImpl` → repositories
   - Entity no expuesta en API; MapStruct para mapping
   - Lógica RN1–RN6 en service, no en controller

   **Calidad (rule `05-java-code-quality`)**:
   - Null safety, no `Optional.get()` ciego
   - SLF4J sin PII/tokens
   - `@RequiredArgsConstructor`, inyección por constructor

   **Spring API / Mongo (rule `06-java-spring-api`)**:
   - HTTP codes: 201 emisión, 204 revoke, 200 validate con `valid=false` en inválida
   - Error contract `{ code, message, details, timestamp }`
   - `@Document`, queries parametrizadas

4. **Clasificar issues** (rule `07-pr-review-verdict`):
   - CRITICAL/HIGH → comentario **inline obligatorio** en línea exacta
   - Formato: `[SEVERITY][CATEGORY] Título — Impacto — Sugerencia — Rule: XX`

5. **Publicar review en GitHub** (rule `02-gh-pr-review-workflow`):
   - Comentarios inline vía `gh api` o review batch
   - Veredicto:
     - 0 CRITICAL + 0 HIGH → `gh pr review --approve`
     - ≥1 CRITICAL/HIGH → `gh pr review --request-changes`
     - Solo preguntas → `gh pr review --comment`
   - Verificar: `gh pr view --json reviews,reviewDecision`

6. **Referencias cruzadas** (solo lectura):
   - `backlog/exports/h2-servicio-acceso-sesion/handoff.md` — contrato H3/H4–H6
   - `backlog/exports/h2-servicio-acceso-sesion/openapi.yaml` — coherencia REST
   - `backlog/exports/h2-servicio-acceso-sesion/dod.md` — quality gates ya cumplidos

7. **Output** (rule `08-pr-review-output-format`):
   - RESUMEN con veredicto, PR URL, conteo issues
   - ISSUES TABLE con `posted_to_pr: true` para CRITICAL/HIGH
   - FIX_ROUTING si NEEDS_CHANGES
   - JSON mínimo con `success`, `pr_url`, `verdict`, `inline_comments_posted`

**Entregables**:
- Review visible en GitHub (comentarios inline + review body)
- Veredicto formal (`APPROVED` | `NEEDS_CHANGES` | `COMMENT`)
- Reporte estructurado para orquestador

**Criterios de aceptacion**:
- [ ] Diff completo revisado (main + test + pom + config)
- [ ] Cada CRITICAL/HIGH tiene comentario inline en GitHub (`posted_to_pr: true`)
- [ ] No se aprobó con CRITICAL/HIGH abiertos
- [ ] Review publicada vía `gh pr review` (no solo en chat IDE)
- [ ] `reviewDecision` refleja el veredicto
- [ ] Agente **readonly** — cero commits/push/código modificado

**Reglas aplicables**: `01-output-format`, `01-pr-review-scope`, `02-change-policy`, `02-gh-pr-review-workflow`, `03-java-security-owasp`, `04-java-architecture-patterns`, `05-java-code-quality`, `06-java-spring-api`, `07-pr-review-verdict`, `08-pr-review-output-format`

**Definition of Done**: Review publicada en GitHub con veredicto verificable. Si `APPROVED` → PR listo para merge humano/CI. Si `NEEDS_CHANGES` → FIX_ROUTING indica steps a re-ejecutar (`build`, `test`, `pr`). Skill `01-pr-code-review` cumplida.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

pr-review APPROVED — 0 CRITICAL/HIGH, 5 MEDIUM no bloqueantes, review en GitHub
