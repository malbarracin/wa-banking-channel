---
id: TASK-10
title: "[pr-review] H1 — Servicio de Usuarios"
status: To Do
priority: medium
created: "2026-06-22T22:10:04.560Z"
parent: TASK-1
labels:
assignee:
---

# [pr-review] H1 — Servicio de Usuarios

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: pr-review

**Objetivo**: Revisar en GitHub el Pull Request creado en el step `pr`, evaluar el diff Java/Spring/Mongo contra rules de seguridad, arquitectura, calidad y API REST, publicar comentarios inline en issues CRITICAL/HIGH, y emitir veredicto (`APPROVE` | `REQUEST_CHANGES` | `COMMENT`) visible en el PR.

**Contexto**: PR de H1 — Servicio de Usuarios hacia `develop`. Implementación ya validada internamente (TASK-7 APPROVED iter 1, 0 blockers en `dod.md`), pero este review es **independiente en GitHub** (agente readonly). Spec: sin JWT en piloto; endpoints públicos en red de confianza — no marcar ausencia de `@PreAuthorize` como HIGH si está documentado en alcance. Consumidores futuros (H3) dependen de `canLinkChannel` y contrato OpenAPI.

**Descripcion detallada**:

1. **Prerrequisitos**: PR abierto (`pr_url` / `pr_number` del step `pr`). `gh auth status` con permisos de review. Si PR cerrado/merged o inexistente → `BLOCKED`.

2. **Resolver PR**:
   ```bash
   gh pr view <number|url> --json number,url,title,state,baseRefName,headRefName,headRefOid,files
   gh pr diff <number>
   gh pr view <number> --json files
   ```

3. **Artefactos de referencia** (contrato esperado — no modificar):
   - `backlog/exports/h1-servicio-usuarios/handoff.md` — endpoints, transiciones de estado, error contract
   - `backlog/exports/h1-servicio-usuarios/openapi.yaml` — contrato REST snapshot
   - `backlog/exports/h1-servicio-usuarios/dod.md` — quality gates y criterios spec §7
   - `backlog/exports/h1-servicio-usuarios/manifest.yaml` — operaciones `provides`

4. **Alcance de revisión** (todo el diff del PR, prioridad):
   - `src/main/java/com/wa/banking/users/**` — Controller, Service, Repository, Entity, DTO, Mapper, Error handler, Config
   - `src/test/java/**` — integración Testcontainers, unitarios, política de estados
   - `pom.xml`, `src/main/resources/application*.yml`, `docker-compose.yml`, `Dockerfile` (si presente)

5. **Checklist por rule** (documentar cada hallazgo con file, line, severity, category, suggestion, target_step):

   | Rule | Foco H1 concreto |
   |------|------------------|
   | `03-java-security-owasp` | No concatenación de input en queries Mongo; no secretos en YAML/logs; actuator `/actuator/env` no expuesto en prod; dependencias sin CVE crítico en `pom.xml`; endpoints mutantes sin auth → **MEDIUM/documentar** (spec: JWT=NO), no CRITICAL |
   | `04-java-architecture-patterns` | Capas MVC respetadas (`UserController` → `UserServiceImpl` → repos); DTOs/records separados de `BankUserEntity`; MapStruct `UserMapperV1`; sin lógica de negocio en controller |
   | `05-java-code-quality` | Null safety (`orElseThrow`, no `.get()` ciego); `GlobalExceptionHandler` sin stack en cliente; `@Slf4j` sin PII; manejo `DuplicateKeyException` unicidad documento |
   | `06-java-spring-api` | 6 endpoints REST con códigos HTTP correctos (201 POST, 404 NOT_FOUND, 400 VALIDATION_ERROR/BAD_REQUEST); `@Valid` en DTOs; error contract `{code,message,details,timestamp}`; Mongo índice unicidad documento; paginación audit |

6. **Validación funcional vs spec** (criterios §7 en diff):
   - U1: alta ACTIVE, rechazo duplicado
   - U2: consulta ID/documento, 404
   - U3: PATCH campos permitidos only
   - U4: transiciones ACTIVE↔SUSPENDED↔SOFT_DELETED, auditoría STATUS_CHANGED
   - `canLinkChannel=false` para SUSPENDED/SOFT_DELETED

7. **Publicar en GitHub** (`02-gh-pr-review-workflow`):
   - Comentarios **inline obligatorios** para CRITICAL y HIGH (API `gh api` o review batch)
   - Resumen en body del review con conteo por severidad
   - Veredicto (`07-pr-review-verdict`):
     - 0 CRITICAL + 0 HIGH → `gh pr review --approve`
     - ≥1 CRITICAL/HIGH → `gh pr review --request-changes`
     - Solo preguntas → `gh pr review --comment`
   - Verificar: `gh pr view --json reviews,reviewDecision`

8. **Salida**: JSON + markdown según `08-pr-review-output-format` (issues table, `posted_to_pr: true` para CRITICAL/HIGH, `target_step` routing si NEEDS_CHANGES).

**Entregables**:
- Review publicado en GitHub (comentarios inline + veredicto)
- JSON con `verdict`, `gh_review_event`, `inline_comments_posted`, summary por severidad
- Resumen markdown (`08-pr-review-output-format`)
- Tabla ISSUES con `posted_to_pr` por hallazgo CRITICAL/HIGH

**Criterios de aceptacion**:
- [ ] Diff completo revisado (`src/main/java/**`, `src/test/java/**`, `pom.xml`, configs)
- [ ] Contrato validado contra `openapi.yaml` y `handoff.md` (6 operaciones, error codes)
- [ ] Todo CRITICAL/HIGH tiene comentario inline en línea exacta del PR
- [ ] No se aprueba con CRITICAL/HIGH abiertos
- [ ] `gh pr view` muestra `reviewDecision` alineado al veredicto
- [ ] Agente **no modificó código** ni ejecutó merge
- [ ] JSON `success: true` solo si review visible en GitHub

**Reglas aplicables**: `01-output-format`, `01-pr-review-scope`, `02-change-policy`, `02-gh-pr-review-workflow`, `03-java-security-owasp`, `04-java-architecture-patterns`, `05-java-code-quality`, `06-java-spring-api`, `07-pr-review-verdict`, `08-pr-review-output-format`

**Definition of Done**: Review visible en GitHub con veredicto publicado; si `NEEDS_CHANGES`, issues CRITICAL/HIGH documentados con `target_step` (`build`/`test`/`docs`/`pr`) para re-ejecución del orquestador. Review solo en chat = **BLOCKED**.
<!-- SECTION:DESCRIPTION:END -->
