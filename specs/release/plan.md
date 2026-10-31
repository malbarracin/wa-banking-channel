---
title: Release v0.1.0 — plan de integración y publicación
status: borrador
source: Inventario de PRs abiertos con `gh pr list` + encadenamiento con `git
  merge-base --is-ancestor` (2026-10-31)
updated: 2026-10-31T18:50:34.569Z
---

# Release v0.1.0

## Resumen

Primera versión del canal WhatsApp bancario: servicios H1–H6 (`services/`), plataforma de eventos y orquestador
(`platform/`), gateway de Twilio (`gateway/`) y los tres componentes de IA (`ai/`: capa MCP H7, base de conocimiento H8
y agente H9).

Los **14 PRs abiertos** ya están todos dentro de la rama de la release (`opensoma/20261031-cb0a`): forman una cadena
lineal en la que cada rama salió de la anterior. Lo único que falta integrar es **`main`**, que quedó atrás y trae una
implementación **anterior y paralela** de `services/wa-users-service` que hay que resolver (ver «Riesgos y decisiones»).

## Estado del inventario

- 14 PRs abiertos (#2, #3, #4, #6, #7, #8, #9, #10, #11, #12, #13, #14, #15, #16). Ninguno es borrador.
- Ningún PR tiene `reviewDecision` (el repo no exige aprobación formal); las revisiones son `COMMENTED`.
- Hilos de revisión sin resolver en GitHub: #11 (1 MEDIUM), #13 (4: 3 HIGH + 1 MEDIUM) y #14 (4: 3 HIGH + 1 MEDIUM).
  Los 8 de #13/#14 **ya están corregidos en la rama de la release** (commit `9c5f91c`, sesión anterior); los hilos
  siguen abiertos en GitHub porque las ramas de esos PRs no se volvieron a publicar.
- El hilo de #11 **no** está corregido en la rama (ver riesgos).
- No hay ninguna etiqueta `v*` en el repositorio: es la primera release.
- La rama de la release no contiene `origin/main` (`merge-base --is-ancestor origin/main HEAD` ≠ 0).

## Entran (en orden de la cadena)

Cada rama contiene a la anterior (`git merge-base --is-ancestor`), así que el contenido de la release es la última
punta: `opensoma/20261031-cb0a`.

| # | PR | Rama | Absorbe | Notas / riesgos |
|---|----|------|---------|-----------------|
| 2 | H1 Servicio de Usuarios bancarios | `opensoma/20261026-5aea` | — | Tiene un commit local sin publicar (`35883b1`) cuyos arreglos ya están replicados en #15. |
| 3 | Servicio Canal WhatsApp (H3) | `opensoma/20261026-382a` | #2 | — |
| 4 | Acceso y Sesión (H2) | `opensoma/20261026-bd1c` | #2–#3 | Commit local sin publicar (`9916196`) ya replicado en #15. |
| 6 | Cuentas (H4) | `opensoma/20261027-8b42` | #2–#4 | Quedan 3 tareas `tech-lead` (TASK-55/56/57) pendientes de la corrida vieja; no bloquean la release. |
| 7 | Seguros (H6) | `opensoma/20261027-b0a3` | #2–#6 | — |
| 8 | Plataforma de Eventos (H0.1) | `opensoma/20261027-0788` | #2–#7 | — |
| 9 | Gateway WhatsApp / Twilio (H0) | `opensoma/20261027-6acc` | #2–#8 | — |
| 10 | Orquestador del Canal (H0.2) | `opensoma/20261028-9513` | #2–#9 | — |
| 11 | Tarjetas (H5) | `opensoma/20261030-5cec` | #2–#10 | **1 hilo MEDIUM abierto sin corregir**: el 201 de aviso de viaje no devuelve id ni `Location`. Quedan 3 tareas `tech-lead` pendientes (TASK-114/115/116). |
| 12 | Capa MCP (H7) | `opensoma/20261030-654a` | #2–#11 | Hereda el MEDIUM de #11 (misma base de código). |
| 13 | Base de conocimiento RAG (H8) | `opensoma/20261030-18dd` | #2–#12 | 4 hilos abiertos en GitHub, corregidos en la rama de la release (`9c5f91c`). |
| 14 | Agente conversacional (H9) | `opensoma/20261030-821a` | #2–#13 | 4 hilos abiertos en GitHub, corregidos en la rama de la release (`9c5f91c`). |
| 15 | Arreglos de revisión de #2, #3 y #4 | `opensoma/20261031-f25c` | #2–#14 | — |
| 16 | Arreglo de revisión de #9 (límite de body) | `opensoma/20261031-c2d2` | #2–#15 | — |

## Absorbidos (se cierran al lanzar, no llevan tarea)

Todos los PRs de la tabla anterior: su contenido ya está en la rama de la release (`opensoma/20261031-cb0a`), por lo
que no se integran de a uno. Se cierran cuando se lanza la versión.

| PR | Incluido en |
|----|-------------|
| #2, #3, #4, #6, #7, #8, #9, #10, #11, #12, #13, #14, #15, #16 | `opensoma/20261031-cb0a` (rama de la release) |

## Afuera

| PR | Motivo |
|----|--------|
| — | No hay PRs excluidos: no hay borradores ni PRs bloqueados por revisión HIGH/CRITICAL abierta en su propia rama. |

Fuera del alcance de los PRs, pero **decisión a tomar en la aprobación**: la implementación anterior de
`services/wa-users-service` que vive en `main` (`com.bank.users`, corrida 20261018) no entra; la reemplaza la de la
release (`com.bcra.users.ws`). Ver «Riesgos y decisiones».

## Versión

Última: **ninguna** (el repo no tiene etiquetas). Nueva: **v0.1.0**.

Motivo: es la primera versión publicada, según `rel-versionado.md` (sin etiquetas previas, la primera es `0.1.0`).
Además el contenido ya declara esa versión: los 8 `pom.xml` usan `0.1.0-SNAPSHOT` y los 3 `pyproject.toml` usan
`0.1.0`. Lo que trae la release es funcionalidad nueva completa (todo el canal), no sólo correcciones, así que el
MENOR/PARCHE no aplica: es la base inicial.

## Archivos con la versión

- **Ninguno.** La versión vive en la etiqueta `v0.1.0` y en `CHANGELOG.md`.
- Motivo: los `pom.xml` de los 8 módulos ya declaran `0.1.0-SNAPSHOT` y los `pyproject.toml` de `ai/*` declaran
  `0.1.0`, coherentes con la primera versión; el equipo de release no cambia metadata de módulos ni versiones internas
  de build. Si la persona prefiere que queden sin `-SNAPSHOT`, el redactor los alinea (8 poms) antes de publicar.

## Riesgos y decisiones

1. **Duplicación de `wa-users-service` al integrar `main` (el riesgo grande de esta release).**
   `main` y la release implementan el mismo servicio H1 por separado, y el merge es `add/add` en 31 archivos. Además
   quedarían dos árboles de paquetes en el mismo módulo: `com.bank.users` (main, corrida 20261018, con su propio
   `@SpringBootApplication`, tests y `.github/agents` + `.github/skills` dentro de la carpeta del servicio) y
   `com.bcra.users.ws` (la release). Con los dos, el módulo queda con dos aplicaciones y dos `pom.xml` en conflicto.
   **Recomendación (a aprobar junto con el plan):** queda la implementación de la release (`com.bcra.users.ws`), que es
   la que usan H2/H3/H7 y la que fija `project.yaml` (`base_package: com.bcra.users.ws`), y se descarta la de `main`
   (`com.bank.users`) junto con los archivos de configuración de herramientas que trae adentro del servicio. No hay
   pérdida funcional: la de la release cubre alta, consulta, actualización, cambio de estado, auditoría y sus tests.
2. **#11 con un MEDIUM abierto.** El 201 de `POST /api/v1/tarjetas/{tarjetaId}/aviso-viaje` devuelve
   `AvisoViajeResponse` sin identificador y sin `Location`. Está verificado en el código de la release. Entra así: es
   MEDIUM (no bloquea por regla) y el equipo de release no arregla producto. Si la persona quiere, se corrige antes de
   publicar (agregar `id` + `Location` o documentar la operación como acción sin recurso creado).
3. **Hilos abiertos de #13/#14 en GitHub.** Los 3 HIGH + 1 MEDIUM de cada uno están resueltos en la rama de la release
   (`9c5f91c`: `source_id` unificado en H8, migración `002_logical_source.sql`, `correlation_id` forzado desde
   `tool_context.state`, mensajes de límite sólo con evidencia de herramienta, timeout de turno en 4 s < `H9_TIMEOUT_MS`
   = 5000). No hace falta volver a corregirlos: al lanzar se pueden cerrar los hilos o los PRs directamente.
4. **Ramas locales con commits sin publicar** (`opensoma/20261026-5aea` y `opensoma/20261026-bd1c`, un commit cada
   una): son arreglos de revisión de una corrida anterior, ya replicados por #15 en la release. No se integran.
5. **`main` trae archivos de configuración de agentes** dentro de `services/wa-users-service/.github/` (agents + skills).
   No son parte del producto; desaparecen al descartar la versión de `main`.
6. **Sin conflictos entre PRs**: como las ramas son una cadena, el único merge con conflictos es el de `main`.
7. **Archivo sin seguimiento** en el working tree: `ai/wa-knowledge-rag/integration-check.log`. No debe entrar en la
   release.

## Verificación del proyecto (para el integrador y el publicador)

- Servicios: en cada `services/<módulo>` → `./mvnw -B clean verify` (JDK 21; JaCoCo ≥ 80%).
- Plataforma y gateway: en `platform/<componente>` y `gateway/wa-twilio-gateway` → `./mvnw -B clean verify`.
  `wa-twilio-gateway` y `wa-channel-orchestrator` usan Testcontainers: con Docker apagado, `-DskipITs`.
- IA: en cada `ai/<componente>` → `uv sync --frozen`, `uv run ruff check .`, `uv run ruff format --check .`,
  `uv run mypy src`, `uv run pytest -q -m "not evals"`; y `python ai/verify.py` en la raíz.
- Scripts: `python -m unittest discover -s scripts -p "test_*.py"`.
- Helm: `helm lint` de los charts tocados (`services/wa-users-service/helm/wa-users-service`).
- Docker Desktop puede estar apagado: `docker compose config` valida sin daemon; `docker build`/`up` y Testcontainers
  no.

## Tareas

1. `role:integrador` — Integrar `main` en la rama de la release (resolver la duplicación de `wa-users-service`).
2. `role:redactor` — CHANGELOG.md y notas de la v0.1.0.
3. `role:publicador` — Publicar `opensoma/20261031-cb0a` y abrir el PR a `main`.
4. `role:lanzador` — Merge a `main`, etiqueta `v0.1.0`, release en GitHub y cierre de los 14 PRs.
