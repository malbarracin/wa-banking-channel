---
id: TASK-28
title: "[review] H2 — Servicio de Acceso y Sesión"
status: Done
priority: medium
created: "2026-06-23T20:39:15.163Z"
parent: TASK-22
---

# [review] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: review

**Objetivo**: Revisión de calidad del código H2 contra rules, quality gates, coverage y runtime safety. Veredicto APPROVED / NEEDS_CHANGES.

**Contexto**: Post-docs. Feature: credenciales de sesión WhatsApp. Skill `06-review`. Sin modificar código.

**Descripcion detallada**:

1. Leer `.cursor/skills/06-review/SKILL.md` y `.cursor/agents/code-reviewer.md`.
2. Revisar alcance vs spec H2 §3–§7: emisión condicionada, revocación inmediata, validate para productos, auditoría sin secretos.
3. Verificar cumplimiento: error contract, MVC layering, Lombok/MapStruct, no logs sensibles, API v1, JaCoCo ≥ 0.70.
4. Validar compatibilidad H3: endpoints y payloads esperados por `SessionClient`.
5. Runtime-safe: README incluye Plan A/B y advertencia Git Bash.
6. Clasificar hallazgos CRITICAL/HIGH/MEDIUM/LOW con `target_step` (build/test/docs).
7. Emitir veredicto JSON + resumen según agente.

**Entregables**:
- Reporte review en salida del chat
- Veredicto `APPROVED` o `NEEDS_CHANGES` con issues priorizados

**Criterios de aceptacion**:
- [ ] 0 issues CRITICAL/HIGH para APPROVED
- [ ] Reglas RN1–RN6 reflejadas en código
- [ ] Coverage y verify documentados
- [ ] Compatibilidad H3 verificada
- [ ] Cada issue CRITICAL/HIGH tiene `target_step`

**Reglas aplicables**: 01-output-format, 02-change-policy, 10-java-code-style, 11-java-best-practices, 13-quality-gates, 14-jacoco, 15-runtime-safe-execution

**Definition of Done**: Veredicto emitido; si NEEDS_CHANGES, issues accionables para loop orquestador.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

review APPROVED iter 1 — 4 fixes aplicados, RN1 corregida
