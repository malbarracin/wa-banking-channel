---
id: TASK-16
title: "[test] H3 Servicio Canal WhatsApp"
status: Done
priority: medium
created: "2026-06-23T19:42:45.201Z"
parent: TASK-12
---

# [test] H3 Servicio Canal WhatsApp

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: test

**Objetivo**: Tests unitarios e integración que cubran flujos F1–F4, reglas de negocio y error contract; alcanzar umbral JaCoCo `coverage.min_line: 0.70`.

**Contexto**: Código H3 implementado. Testcontainers Mongo. No modificar código producción.

**Descripcion detallada**:

1. Ejecutar `./mvnw clean test` y revisar `target/site/jacoco/index.html`.
2. **Tests unitarios**: `WhatsAppLinkServiceImplTest`, `WhatsAppLinkControllerV1Test` (@WebMvcTest).
3. **Tests integración** (Testcontainers + MockBean H1/H2): onboarding F1 end-to-end, anti-duplicado, bloqueo revoca H2.
4. Mínimo 1 caso OK + 1 error típico por flujo (`13-quality-gates`).
5. Si coverage < 0.70: agregar tests sin modificar producción.
6. Verificar `./mvnw clean verify` pasa JaCoCo LINE ≥ 0.70.
7. Runbook Plan A/B documentado (`15-runtime-safe-execution`).

**Entregables**: Tests unitarios, integración Testcontainers, reporte JaCoCo, build verify green.

**Criterios de aceptacion**:
- [ ] `./mvnw clean verify` OK
- [ ] Coverage LINE ≥ 70%
- [ ] Criterios §7 onboarding cubiertos por tests
- [ ] Error contract validado 400/404
- [ ] JaCoCo aislado
- [ ] Runbook Plan A/Plan B documentado

**Reglas aplicables**: 01-output-format, 02-change-policy, 13-quality-gates, 14-jacoco, 15-runtime-safe-execution

**Definition of Done**: `./mvnw clean verify` pasa; reporte JaCoCo existe; tests cubren flujos críticos F1–F4.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

test completado — 49 tests, verify OK, coverage ~82%, JaCoCo check passed
