---
id: TASK-41
title: "[pr] H4 — Servicio de Cuentas"
status: In Progress
priority: medium
created: "2026-06-23T23:52:43.332Z"
parent: TASK-33
---


# [pr] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: pr

**Objetivo**: Publicar en GitHub el trabajo completado de H4 F1 (`wa-accounts-service`): asegurar branch de feature pusheada y abrir (o reutilizar) Pull Request hacia `develop` con título, summary y test plan alineados al bundle release.

**Contexto**: Pipeline completado hasta `release` (TASK-40). Código, tests (26), JaCoCo ~86%, docs y bundle en `backlog/exports/h4-servicio-cuentas/`. Epic TASK-33. El step `pr` no modifica código — solo git + `gh`. Material de referencia listo en `pr.md`, `handoff.md`, `dod.md`.

**Descripcion detallada**:

1. Leer la tarea asignada y, en paralelo, ejecutar contexto git/gh:
   - `git status`, `git branch --show-current`, `git remote -v`, `gh auth status`
   - Leer `backlog/exports/h4-servicio-cuentas/pr.md` (título, summary, test plan, checklist)
   - Leer `handoff.md` y `dod.md` para enriquecer el body del PR si hace falta

2. Resolver rama base del PR (`BASE_BRANCH`) según rule `04-pr-base-branch`:
   - Prioridad: tarea (`branch.base` / `pr.base`) → rule default **`develop`** → `SOMA_PR_BASE` → default del repo
   - Validar: `git fetch origin $BASE_BRANCH` y que exista `origin/$BASE_BRANCH`

3. Verificar branch de feature:
   - Nombre sugerido: `feature/h4-servicio-cuentas` (o el que indique la tarea)
   - Si hay cambios sin commitear relevantes al feature, commitear **solo** archivos del servicio (excluir `.cursor/`, `delivery/`, secretos, `target/`)
   - **No** force push a `main`, `master` ni `develop`

4. Push obligatorio si hay commits locales no en remoto:
   - `git push -u origin HEAD`

5. Pull Request (obligatorio — push solo no cierra el step):
   - Buscar PR existente: `gh pr list --head $(git branch --show-current) --base $BASE_BRANCH --json url,number,state`
   - Si no hay PR abierto, crear con **`--base` explícito**:
     - **Título sugerido** (desde `pr.md`): `feat(h4): servicio de cuentas F1 — listado y saldo con auth H2`
     - **Body** — incluir secciones Summary y Test plan de `pr.md`, mencionando:
       - Endpoints: `GET /api/v1/accounts`, `GET /api/v1/accounts/{accountId}/balance`
       - Auth H2: headers `Authorization` + `X-Credential-Id`, validate en `:8082`
       - Stack: Java 21, Spring Boot 3.3.5, MongoDB 7, JaCoCo ~86%
       - Epic TASK-33; bundle `backlog/exports/h4-servicio-cuentas/`
       - Test plan con checks de verify, health `:8083`, curls 200/401/404, Swagger, Postman
   - Verificar: `gh pr view --json url,number,baseRefName,headRefName` — confirmar `baseRefName == develop` (o base configurada)

6. Emitir salida según `03-pr-output-format` + JSON con `success: true` **solo** si hay `pr_url` válida.

**Entregables**:
- PR abierto en GitHub (`pr_url`, `pr_number`)
- Branch head pusheada hacia `origin`
- Resumen markdown del step (RESUMEN, CHANGES, RULES_APPLIED, HANDOFF)

**Criterios de aceptacion**:
- [ ] `gh auth status` OK y remoto `origin` accesible
- [ ] `origin/develop` (o base resuelta) existe tras fetch
- [ ] Branch feature con commits del servicio H4 F1 pusheada
- [ ] PR abierto con `--base develop` (o base configurada) — no duplicar si ya existe uno open head→base
- [ ] Body del PR incluye Summary + Test plan coherentes con `pr.md` (endpoints F1, auth H2, 26 tests, JaCoCo)
- [ ] JSON de cierre con `success: true` y `pr_url` verificada

**Reglas aplicables**: `01-output-format`, `01-pr-safety`, `02-change-policy`, `02-gh-pr-workflow`, `03-pr-output-format`, `04-pr-base-branch`

**Definition of Done**: Existe PR visible en GitHub apuntando de la feature branch a `develop` (o base del equipo), con URL capturada. Push sin PR = **BLOCKED**.
<!-- SECTION:DESCRIPTION:END -->
