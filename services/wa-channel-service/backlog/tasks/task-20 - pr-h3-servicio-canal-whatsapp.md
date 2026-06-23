---
id: TASK-20
title: "[pr] H3 — Servicio Canal WhatsApp"
status: In Progress
priority: medium
created: "2026-06-23T20:18:31.098Z"
parent: TASK-12
---


# [pr] H3 — Servicio Canal WhatsApp

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: pr

**Objetivo**: Publicar la feature H3 — Servicio Canal WhatsApp (`wa-channel-service`) en GitHub: crear/usar branch feature, commitear el trabajo del pipeline, push y abrir PR hacia `develop` con Summary y Test plan derivados del bundle release.

**Contexto**: Microservicio REST con 13 endpoints `/api/v1/channel-links/**`, MongoDB (3 colecciones), integración H1/H2 (stub), 49 tests y coverage ~82%. Épica TASK-12. Release bundle en `backlog/exports/h3-servicio-canal-whatsapp/`. Review local APPROVED; falta el PR remoto.

**Descripcion detallada**:

1. **Preparación (paralelo)**:
   - `gh auth status`
   - `git status`, `git diff`, `git log --oneline -10`, `git branch -vv`, `git remote -v`
   - Leer `backlog/exports/h3-servicio-canal-whatsapp/handoff.md`, `dod.md`, `manifest.yaml` para redactar el PR.

2. **Resolver rama base** (rule `04-pr-base-branch`):
   - `BASE=develop`
   - `git fetch origin develop` (o `git fetch origin`)
   - Verificar `origin/develop` existe.

3. **Feature branch**:
   - Crear o usar branch, p. ej. `feature/h3-servicio-canal-whatsapp` o `feature/TASK-12-h3-canal-whatsapp`.
   - Si hay cambios sin commitear del pipeline, stage solo archivos relevantes.
   - **Excluir** según `.gitignore`: `.cursor/`, `delivery/`, `examples/`, `inputs/`, `target/`, `.env`, `jacoco.exec`.

4. **Commit**:
   - Mensaje alineado al estilo del repo (ej. `feat(h3): servicio canal WhatsApp — vinculación, verificación, perfil y preferencias`).
   - Incluir: `src/main/**`, `src/test/**`, `pom.xml`, `docker-compose.yml`, `README.md`, `postman/`, `application*.yml`, `backlog/exports/h3-servicio-canal-whatsapp/` (si se versiona el bundle).
   - `git status` post-commit para confirmar limpieza.

5. **Push**:
   - `git push -u origin HEAD`

6. **Pull Request (obligatorio)**:
   - Verificar PR existente: `gh pr list --head "$(git branch --show-current)" --base develop --json url,number,state`
   - Si no existe, crear con base explícita:

   ```bash
   gh pr create --base develop --title "feat(h3): Servicio Canal WhatsApp — wa-channel-service" --body "$(cat <<'EOF'
   ## Summary
   - Implementa H3: vínculo WhatsApp ↔ cliente bancario (flujos F1–F4)
   - 13 endpoints REST en `/api/v1/channel-links/**` con validación Bean Validation y error contract uniforme
   - MongoDB 3 colecciones; integración H1 (usuarios) y H2 (sesión, stub configurable)
   - 49 tests, JaCoCo LINE 82.2% (umbral 70%); OpenAPI + Postman + runbook Windows-safe

   ## Test plan
   - [ ] `./mvnw clean verify` pasa en CI/local
   - [ ] `docker compose up -d mongo` + Plan A: `./mvnw -DskipTests package && java -jar target/wa-channel-service-0.0.1-SNAPSHOT.jar`
   - [ ] `GET /actuator/health` → 200
   - [ ] Flujo F1: POST initiate → accept-terms → verify (OTP 123456) → complete-onboarding
   - [ ] F3 block/unlink revoca credencial H2 stub
   - [ ] Swagger UI en `/swagger-ui/index.html`
   - [ ] Revisar bundle: `backlog/exports/h3-servicio-canal-whatsapp/handoff.md`

   EOF
   )"
   ```

7. **Verificación final**:
   - `gh pr view --json url,number,state,baseRefName,headRefName`
   - Confirmar `baseRefName=develop`.
   - Entregar URL del PR en formato rule `03-pr-output-format`.

**Entregables**:
- Branch feature pusheado a `origin`
- Commit(s) con implementación H3 completa
- PR abierto hacia `develop` con URL verificada
- Salida markdown según `03-pr-output-format` (RESUMEN, CHANGES, RULES_APPLIED, HANDOFF con `pr_url`)

**Criterios de aceptacion**:
- [ ] `gh auth status` OK y push exitoso sin force push a `main`/`master`/`develop`
- [ ] Ningún secreto (`.env`, credenciales) en el commit
- [ ] PR existe en GitHub con `--base develop` explícito
- [ ] PR body incluye Summary (13 endpoints, F1–F4, H1/H2) y Test plan con `./mvnw clean verify`
- [ ] `gh pr view` confirma `baseRefName=develop` y `state=OPEN`
- [ ] Push sin PR → step **BLOCKED** (incompleto)

**Reglas aplicables**: `01-output-format`, `01-pr-safety`, `02-change-policy`, `02-gh-pr-workflow`, `03-pr-output-format`, `04-pr-base-branch`

**Definition of Done**: PR abierto y verificado en GitHub (`pr_url` capturada). El orquestador puede encadenar `pr-review` con `pr_number`/`pr_url`. Push solo no cierra el step.
<!-- SECTION:DESCRIPTION:END -->
