---
id: TASK-29
title: "[release] H2 — Servicio de Acceso y Sesión"
status: Done
priority: medium
created: "2026-06-23T20:39:15.163Z"
parent: TASK-22
---

# [release] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: release

**Objetivo**: Generar bundle de entrega en `delivery/features/` o `backlog/exports/h2-servicio-acceso-sesion/`: DoD, handoff, PR, stats, manifest OpenAPI.

**Contexto**: Review APPROVED. Proyecto `wa-auth-service` H2. No modificar código fuente ni tests.

**Descripcion detallada**:

1. Leer `.cursor/agents/doc-generator.md` y recopilar del repo: endpoints, config Mongo, comandos verify, coverage final, duración pipeline.
2. Generar **`dod.md`**: checklist quality gates (compila, tests, docker-compose, actuator, swagger, postman, jacoco, error contract).
3. Generar **`handoff.md`**: resumen H2, tabla endpoints `/api/v1/sessions/**`, flujos A1–A3, config puerto 8082 / DB `wa-auth`, integración H3 (desactivar stub), consumo H4–H6 via validate, limitaciones, curls Plan A/B.
4. Generar **`pr.md`**: summary, test plan, checklist merge.
5. Generar **`stats.md`**: métricas archivos, endpoints, tests, coverage.
6. Generar **`manifest.yaml`**: `provides` con operaciones REST, puerto, upstream H1/H3, métricas.
7. Copiar/adjuntar **`openapi.yaml`** al bundle export.
8. Documentar cómo H3 conecta: `integration.session.base-url=http://localhost:8082`, `stub-enabled=false`.

**Entregables**:
- `dod.md`, `handoff.md`, `pr.md`, `stats.md`, `manifest.yaml`
- `openapi.yaml` en bundle export
- Paths bajo `backlog/exports/h2-servicio-acceso-sesion/` (o convención delivery del proyecto)

**Criterios de aceptacion**:
- [ ] DoD refleja quality gates cumplidos
- [ ] Handoff permite levantar y probar H2 sin contexto previo
- [ ] Manifest lista operaciones para consumidores downstream
- [ ] PR listo para crear remotamente
- [ ] Sin modificación de código fuente

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Bundle release completo; H2 cerrado para integración con H3 (stub off) y H4–H6 (validate).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

release completado — bundle en backlog/exports/h2-servicio-acceso-sesion/
