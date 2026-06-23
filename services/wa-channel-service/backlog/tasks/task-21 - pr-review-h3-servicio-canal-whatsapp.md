---
id: TASK-21
title: "[pr-review] H3 — Servicio Canal WhatsApp"
status: To Do
priority: medium
created: "2026-06-23T20:18:36.758Z"
parent: TASK-12
labels:
assignee:
---

# [pr-review] H3 — Servicio Canal WhatsApp

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: pr-review

**Objetivo**: Revisar en GitHub el Pull Request de H3 — Servicio Canal WhatsApp publicado en el step `pr`, evaluar el diff completo contra rules Java/Spring/OWASP, publicar comentarios inline para CRITICAL/HIGH y emitir veredicto formal (`APPROVE` | `REQUEST_CHANGES` | `COMMENT`).

**Contexto**: PR con ~51 archivos prod + 6 clases test, 13 endpoints REST, integraciones `UsersClient`/`SessionClient`, MongoDB, error contract, JaCoCo. Review local TASK-18 fue APPROVED; este step valida el diff remoto en GitHub. Agente **readonly** — no modifica código ni hace merge.

**Descripcion detallada**:

1. **Resolver PR**:
   - Input: `pr_url` o `pr_number` del step `pr`.
   - `gh auth status`
   - `gh pr view <NUMBER|URL> --json number,url,title,state,baseRefName,headRefName,headRefOid,files`
   - Si `state != OPEN` → **BLOCKED**.

2. **Diff completo**:
   - `gh pr diff <NUMBER>`
   - `gh pr view <NUMBER> --json files`
   - Revisar **todos** los archivos del diff, priorizando:
     - `src/main/java/com/wa/banking/channel/**` (Controller, Service, Repository, Entity, DTO, Mapper, Clients, Error)
     - `src/test/java/**` (49 tests: unit, @WebMvcTest, Testcontainers)
     - `pom.xml`, `application.yml`, `application-docker.yml`, `docker-compose.yml`

3. **Checklist por rules** (WhatsAppLink H3):

   | Rule | Foco en este PR |
   |------|-----------------|
   | `03-java-security-owasp` | Sin secretos en YAML; queries Mongo parametrizadas; OTP MVP documentado; endpoints sin JWT (limitación piloto — MEDIUM si no documentado); actuator no expone env en prod profile |
   | `04-java-architecture-patterns` | Capas MVC: Controller sin lógica de negocio; DTO vs Entity; MapStruct; Service con RN1–RN8 |
   | `05-java-code-quality` | Null safety (`Optional`, no `.get()` ciego); SLF4J sin PII; sin `catch (Exception)` fuera de handler |
   | `06-java-spring-api` | `@Valid` en requests; códigos HTTP (201 POST, 404 NOT_FOUND, 409 duplicado RN1); error contract `{code,message,details,timestamp}`; Mongo `@Document`/índices |

4. **Clasificar hallazgos** (`07-pr-review-verdict`):
   - CRITICAL/HIGH → bloquean APPROVE.
   - Documentar: `file`, `line`, `severity`, `category`, `title`, `suggestion`, `target_step`.

5. **Publicar en GitHub** (`02-gh-pr-review-workflow`):
   - Comentarios **inline obligatorios** para cada CRITICAL/HIGH:

   ```bash
   HEAD_SHA=$(gh pr view <NUMBER> --json headRefOid -q .headRefOid)
   gh api --method POST repos/{owner}/{repo}/pulls/<NUMBER>/comments \
     -f body='[HIGH][SECURITY] ...' \
     -f commit_id="$HEAD_SHA" \
     -f path='src/main/java/.../WhatsAppLinkControllerV1.java' \
     -f line=42 -f side='RIGHT'
   ```

   - Veredicto gh:
     - 0 CRITICAL/HIGH → `gh pr review <NUMBER> --approve --body "LGTM. H3 F1–F4, error contract y tests OK."`
     - ≥1 CRITICAL/HIGH → `gh pr review <NUMBER> --request-changes --body "Resumen: ver comentarios inline."`
     - Solo preguntas → `gh pr review <NUMBER> --comment`

6. **Verificación**:
   - `gh pr view <NUMBER> --json reviews,reviewDecision,comments`
   - Confirmar review visible y `reviewDecision` alineado al veredicto.

7. **Salida** (`08-pr-review-output-format`):
   - RESUMEN con veredicto, conteo CRITICAL/HIGH/MEDIUM/LOW
   - ISSUES TABLE con `posted_to_pr: true` para CRITICAL/HIGH
   - JSON mínimo con `success`, `pr_url`, `verdict`, `gh_review_event`, `inline_comments_posted`

**Entregables**:
- Review publicado en GitHub (comentarios inline + review body)
- Veredicto: `APPROVED` | `NEEDS_CHANGES` | `COMMENT`
- Salida markdown + JSON según `08-pr-review-output-format`
- FIX_ROUTING si `NEEDS_CHANGES` (target_step: `build`, `test`, `docs`, `pr`)

**Criterios de aceptacion**:
- [ ] Diff completo revisado (prod + test + pom + config)
- [ ] Cada CRITICAL/HIGH tiene comentario inline en la línea exacta del PR
- [ ] No se aprueba con CRITICAL/HIGH abiertos
- [ ] Review solo en chat sin `gh pr review` → **BLOCKED**
- [ ] Agente no modificó código ni ejecutó merge
- [ ] Hallazgos de OTP MVP / sin JWT clasificados según contexto piloto (documentado en handoff/dod), no bloqueantes si están documentados y acotados
- [ ] `reviewDecision` en GitHub refleja el veredicto emitido

**Reglas aplicables**: `01-output-format`, `01-pr-review-scope`, `02-change-policy`, `02-gh-pr-review-workflow`, `03-java-security-owasp`, `04-java-architecture-patterns`, `05-java-code-quality`, `06-java-spring-api`, `07-pr-review-verdict`, `08-pr-review-output-format`

**Definition of Done**: Review visible en GitHub con veredicto coherente. Si `APPROVED`, PR listo para merge humano/CI. Si `NEEDS_CHANGES`, issues CRITICAL/HIGH publicados inline con `target_step` para re-ejecución del orquestador.
<!-- SECTION:DESCRIPTION:END -->
