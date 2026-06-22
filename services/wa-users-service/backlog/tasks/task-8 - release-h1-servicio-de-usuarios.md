---
id: TASK-8
title: "[release] H1 — Servicio de Usuarios"
status: Done
priority: medium
created: "2026-06-22T21:41:05.071Z"
parent: TASK-1
---

# [release] H1 — Servicio de Usuarios

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: release

**Objetivo**: Generar entregables de cierre del feature H1 en `delivery/features/[NN]-[slug]/`: DoD, handoff y PR description.

**Contexto**: Post-review. Feature: Servicio de Usuarios H1. Métricas desde steps anteriores (archivos, tests, coverage, duración). No modificar código fuente.

**Descripcion detallada**:
1. Leer `.cursor/agents/doc-generator.md` y recopilar del repo: endpoints implementados, config Mongo, comandos verify, coverage final.
2. Crear **`delivery/features/01-h1-servicio-usuarios/`** (o slug acordado):
   - **`dod.md`**: checklist quality gates §13 — compila, tests, docker-compose, error contract, Swagger, Postman, JaCoCo, README.
   - **`handoff.md`**: resumen H1, endpoints `/api/v1/users/**`, cómo probar (Plan A jar / Plan B spring-boot:run), limitaciones (sin JWT, sin sync downstream, campos Compliance asumidos).
   - **`pr.md`**: summary, tipo `feature`, lista de cambios, test plan, checklist merge.
3. Incluir referencia a spec Notion `36875bb08ca78197a853e85505bf49a5` como trazabilidad.
4. Emitir resumen con formato `01-output-format` (RESUMEN, CHANGES, RULES_APPLIED, MÉTRICAS, HANDOFF).

**Entregables**:
- `delivery/features/01-h1-servicio-usuarios/dod.md`
- `delivery/features/01-h1-servicio-usuarios/handoff.md`
- `delivery/features/01-h1-servicio-usuarios/pr.md`

**Criterios de aceptacion**:
- [ ] Los 3 documentos existen y son consistentes con el código
- [ ] DoD refleja criterios §7 de la spec marcados cumplidos
- [ ] Handoff permite levantar y probar U1–U4 sin contexto adicional
- [ ] PR description lista endpoints y comandos de verificación

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Entregables en `delivery/features/` completos; feature H1 cerrable para integración con H3 (Canal WhatsApp) downstream.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

release completado — bundle backlog/exports/h1-servicio-usuarios/ (5 artefactos)
