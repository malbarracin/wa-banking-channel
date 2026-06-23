---
id: TASK-5
title: "[test] H1 — Servicio de Usuarios"
status: Done
priority: medium
created: "2026-06-22T21:41:03.700Z"
parent: TASK-1
---


# [test] H1 — Servicio de Usuarios

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: test

**Objetivo**: Asegurar quality gates: tests de integración con Testcontainers Mongo, casos OK/error por flujo, JaCoCo umbral en `verify`, y runbook runtime-safe para Windows.

**Contexto**: Build completado con endpoints U1–U4. Sin JWT. JaCoCo ya en POM desde scaffold; aplicar umbral `coverage.min_line` del plan (default **0.70** si orchestrator no indica otro).

**Descripcion detallada**:
1. Tests de integración `@SpringBootTest` + Testcontainers Mongo (preferido en `13-quality-gates`):
   - **OK U1**: POST usuario válido → 201, `status=ACTIVE`, `canLinkChannel=true`.
   - **Error U1**: POST mismo documento dos veces → 400 `BAD_REQUEST` / mensaje duplicado.
   - **OK U2**: GET por id y por documento tras alta.
   - **Error U2**: GET id inexistente → 404 `NOT_FOUND`.
   - **OK U3**: PATCH campos permitidos → persistidos.
   - **OK U4**: PATCH status a SUSPENDED → audit entry; `canLinkChannel=false`.
   - **Error validación**: POST body incompleto → 400 `VALIDATION_ERROR`.
2. Test unitarios de service para transiciones de estado inválidas si aplica.
3. Verificar error contract en al menos un test (estructura JSON `{ code, message, details, timestamp }`).
4. Configurar **`jacoco:check`** con umbral LINE = `coverage.min_line`.
5. Ejecutar `./mvnw clean verify` — debe pasar con reporte en `target/site/jacoco/index.html`.
6. Documentar en salida runbook **Plan A** (Windows): `./mvnw -DskipTests package` + `java -jar target/*.jar`; **Plan B**: `./mvnw spring-boot:run` en PowerShell; advertir Git Bash (`15-runtime-safe-execution`).

**Entregables**:
- `src/test/java/.../UserControllerV1IntegrationTest.java` (y tests service si necesarios)
- Config JaCoCo check en `pom.xml` si falta umbral
- Reporte JaCoCo generado tras verify

**Criterios de aceptacion**:
- [ ] Mínimo 1 caso OK y 1 error típico por flujo principal (U1–U4)
- [ ] Testcontainers Mongo en tests de integración
- [ ] `./mvnw clean verify` pasa con coverage ≥ umbral
- [ ] Reporte HTML JaCoCo existe
- [ ] Runbook runtime-safe incluido en handoff del step

**Reglas aplicables**: 01-output-format, 02-change-policy, 13-quality-gates, 14-jacoco, 15-runtime-safe-execution

**Definition of Done**: Verify verde; coverage cumple umbral; tests cubren criterios de aceptación §7 de la spec.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

test completado — 17 tests, verify OK, coverage ~90%, runbook incluido

test completado — 18 tests, verify OK, coverage ~90%, runbook incluido
