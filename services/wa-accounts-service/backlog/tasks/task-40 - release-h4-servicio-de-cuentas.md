---
id: TASK-40
title: "[release] H4 — Servicio de Cuentas"
status: Done
priority: medium
created: "2026-06-23T21:36:03.255Z"
parent: TASK-33
---

# [release] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: release

**Objetivo**: Empaquetar entregables finales del piloto H4 F1 para handoff al ecosistema (H3 consumidor, orquestador Soma).

**Contexto**: Tras review APPROVED. Generar artefactos en `backlog/exports/h4-servicio-cuentas/`.

**Descripcion detallada**:
1. Generar **`handoff.md`**:
   - Resumen feature piloto F1.
   - Endpoints, auth headers, integración H2 validate.
   - Configuración, puertos, dependencias runtime.
   - Curls y ejemplos 200/401/404.
   - Próximos pasos: F2 movimientos, F3/F4 transferencias.
2. Generar **`dod.md`**: checklist quality gates cumplidos (verify, coverage, actuator, openapi, postman, error contract).
3. Generar **`pr.md`**: título, summary, test plan, breaking changes (ninguno en greenfield).
4. Export **`manifest.yaml`** del bundle:
   - `component: wa-accounts-service`
   - `provides`: accounts-api-v1 (operations F1 piloto)
   - `consumers`: h3-canal-whatsapp
   - `dependencies`: h2 session-credentials-api-v1
5. Snapshot OpenAPI exportado (`openapi.yaml` en exports).
6. Stats de pipeline (tests, coverage, verify command).

**Entregables**:
- `backlog/exports/h4-servicio-cuentas/handoff.md`
- `backlog/exports/h4-servicio-cuentas/dod.md`
- `backlog/exports/h4-servicio-cuentas/pr.md`
- `backlog/exports/h4-servicio-cuentas/manifest.yaml`
- `backlog/exports/h4-servicio-cuentas/openapi.yaml`
- `backlog/exports/h4-servicio-cuentas/stats.md`

**Criterios de aceptacion**:
- [ ] Handoff autosuficiente para integración H3 sin leer código fuente.
- [ ] DoD refleja gates reales del verify.
- [ ] Manifest lista `provides` F1 y dependencia H2.
- [ ] PR summary alineado con spec Notion H4.

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Bundle release completo en `backlog/exports/h4-servicio-cuentas/` listo para PR/merge y consumo por H3.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

release completado — bundle en backlog/exports/h4-servicio-cuentas/
