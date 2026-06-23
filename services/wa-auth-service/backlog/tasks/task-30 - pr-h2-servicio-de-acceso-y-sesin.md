---
id: TASK-30
title: "[pr] H2 — Servicio de Acceso y Sesión"
status: In Progress
priority: medium
created: "2026-06-23T21:10:00.000Z"
parent: TASK-22
---


# [pr] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: pr

**Objetivo**: Publicar en GitHub un Pull Request con todo el trabajo de H2 (`wa-auth-service`) hacia la rama base del equipo, listo para review y merge.

**Contexto**: Feature H2 (EPIC TASK-22) completada localmente. Pipeline interno cerrado en release TASK-29 con bundle exportado. El servicio gestiona credenciales opacas de sesión del canal WhatsApp: emisión (A1), renovación (A2), revocación (A3), validación para H4–H6, y auditoría paginada. Consumidores documentados: H3 (`SessionClient`), H4–H6 (`POST /validate`).

**Descripcion detallada**:

1. **Preparación git** (paralelo):
   - `git status`, `git diff`, `git log --oneline -10`, `git branch -vv`
   - `gh auth status`
   - Resolver `BASE=develop` (rule `04-pr-base-branch`)
   - `git fetch origin develop` y verificar `origin/develop` existe

2. **Branch y commits**:
   - Confirmar branch de feature (ej. `feat/h2-servicio-acceso-sesion` o la rama activa con el trabajo H2)
   - Verificar que el working tree incluye: 43 clases main, 6 test, `docker-compose.yml`, `application.yml`, `openapi.yaml`, `README.md`, `postman/wa-auth-service.postman_collection.json`
   - Excluir del commit: `.cursor/`, `delivery/`, `target/`, `.env`, secretos
   - Si hay cambios sin commitear del pipeline H2 → commit con mensaje alineado al estilo del repo (ej. `feat(auth): H2 servicio de acceso y sesión — 8 endpoints, RN1-RN6`)

3. **Push**:
   - `git push -u origin HEAD` (solo si hay commits locales no pusheados)

4. **Verificar PR existente**:
   - `gh pr list --head "$(git branch --show-current)" --base develop --state open --json url,number,title`
   - Si ya existe → capturar URL y marcar step OK

5. **Crear PR** (si no existe) con `--base develop` explícito:
   - **Título sugerido**: `feat(auth): H2 — Servicio de Acceso y Sesión (wa-auth-service)`
   - **Summary** (desde `handoff.md` / `dod.md` / `manifest.yaml`):
     - Microservicio REST Java 21 / Spring Boot 3.3.5 / MongoDB 7
     - 8 endpoints: issue, validate, renew, revoke (DELETE/POST/revoke-by-user), get status, audit
     - RN1–RN6: emisión con `identityVerified=true`, reemplazo credencial ACTIVE, TTL configurable, revocación inmediata, token opaco BCrypt
     - Compatibilidad H3 SessionClient (`channelLinkId`/`linkId`, DELETE revoke en block/unlink)
     - Error contract uniforme, Actuator health, Swagger/OpenAPI, Postman collection
     - 40 tests, JaCoCo ~85% LINE (umbral 70%)
     - Review interno APPROVED iter 1 (TASK-28)
   - **Test plan**:
     - [ ] `./mvnw clean verify` pasa en CI/local
     - [ ] `docker compose up -d mongo` + health `GET /actuator/health` en `:8082`
     - [ ] Flujo A1: POST issue con `identityVerified=true` → 201 + token
     - [ ] Flujo A1 negativo: POST sin verificación → 400 `BAD_REQUEST`
     - [ ] Flujo validate: POST `/validate` válido → `valid=true`; revocado → `valid=false` (HTTP 200)
     - [ ] Flujo A3: DELETE revoke → 204; validate posterior `valid=false`
     - [ ] Swagger UI accesible en `/swagger-ui/index.html`
     - [ ] Bundle release: `backlog/exports/h2-servicio-acceso-sesion/`

6. **Verificación final**:
   - `gh pr view --json url,number,state,baseRefName,headRefName`
   - Confirmar `baseRefName=develop`
   - Entregar `pr_url` al orquestador para step `pr-review`

**Entregables**:
- Branch pusheado en origin
- PR abierto con URL verificada
- Body con Summary + Test plan
- Output formato rule `03-pr-output-format` (RESUMEN, CHANGES, RULES_APPLIED, HANDOFF con link PR)

**Criterios de aceptacion**:
- [ ] `gh auth status` OK
- [ ] PR existe con `--base develop` explícito
- [ ] `baseRefName=develop` verificado en `gh pr view`
- [ ] No hay archivos sensibles ni artefactos `.cursor/`/`target/` en el diff del PR
- [ ] Test plan referencia flujos A1/A2/A3 y validate de la spec H2
- [ ] Push sin PR = **BLOCKED** (incompleto)

**Reglas aplicables**: `01-output-format`, `01-pr-safety`, `02-change-policy`, `02-gh-pr-workflow`, `03-pr-output-format`, `04-pr-base-branch`

**Definition of Done**: PR URL verificada en GitHub, head → develop, listo para step `pr-review`. Skill `01-pr-publish` cumplida.
<!-- SECTION:DESCRIPTION:END -->
