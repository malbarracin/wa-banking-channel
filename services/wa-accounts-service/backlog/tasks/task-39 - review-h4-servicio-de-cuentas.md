---
id: TASK-39
title: "[review] H4 — Servicio de Cuentas"
status: Done
priority: medium
created: "2026-06-23T21:36:03.255Z"
parent: TASK-33
---

# [review] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: review

**Objetivo**: Revisión de calidad del piloto H4 F1 contra rules, best practices y quality gates antes de release.

**Contexto**: Usar skill `06-review`. Verificar que el código cumple stack, error contract, auth H2, estilo Java y gates de verify.

**Descripcion detallada**:
1. Ejecutar checklist del skill `06-review` y rules 10, 11, 13, 14, 15.
2. Verificar:
   - Cambios mínimos (02-change-policy): sin refactors no solicitados.
   - No exposición de Entity en API.
   - No `System.out`, no catch genérico fuera de handler.
   - MapStruct/Lombok usados correctamente.
   - Auth: ningún endpoint F1 accesible sin validación H2.
   - JaCoCo aislado de runtime.
3. Revisar naming, paquetes MVC, JavaDoc en clases principales.
4. Validar `./mvnw clean verify` (referencia, no re-ejecutar si el agente es read-only — reportar resultado esperado del step test).
5. Emitir veredicto: `APPROVED` | `NEEDS_CHANGES` con lista concreta de issues.

**Entregables**:
- Informe de review con veredicto y findings priorizados (blocker/major/minor).
- Referencia a archivos y líneas cuando aplique.

**Criterios de aceptacion**:
- [ ] Cero blockers en auth, error contract y data isolation.
- [ ] Quality gates 13 cumplidos (compila, tests, actuator, openapi).
- [ ] Estilo 10/11 sin violaciones críticas.
- [ ] Veredicto explícito para el orquestador.

**Reglas aplicables**: 01-output-format, 02-change-policy, 10-java-code-style, 11-java-best-practices, 13-quality-gates, 14-jacoco, 15-runtime-safe-execution

**Skills**: 06-review

**Definition of Done**: Review `APPROVED` o lista cerrada de fixes obligatorios antes de release.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

review completado — Veredicto APPROVED, 0 CRITICAL/HIGH
