---
id: TASK-26
title: "[test] H2 — Servicio de Acceso y Sesión"
status: Done
priority: medium
created: "2026-06-23T20:39:15.163Z"
parent: TASK-22
---


# [test] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: test

**Objetivo**: Tests unitarios e integración que cubran flujos A1–A3, validación para productos, error contract y umbral JaCoCo `coverage.min_line: 0.70`.

**Contexto**: Build completado. JaCoCo en POM desde scaffold. Windows: validar runbook Plan A en verificación manual.

**Descripcion detallada**:

1. Ejecutar `./mvnw clean test` y revisar `target/site/jacoco/index.html`.
2. **Unit tests** `SessionCredentialServiceTest`: emisión OK, emisión sin verificación falla, reemplazo credencial activa, renew OK/expirada, revoke inmediato, validate OK/revoked/expired/wrong-token, revoke-by-user masivo.
3. **Controller tests** `SessionCredentialControllerV1Test`: MockMvc para POST issue (201), DELETE revoke (204), POST validate (200/401 según caso), error contract 400/404.
4. **Integration tests** `@SpringBootTest` + Testcontainers Mongo: flujo completo issue → validate → revoke → validate false; persistencia audit log.
5. Configurar **`jacoco:check`** umbral LINE = **0.70** en verify.
6. Ejecutar `./mvnw clean verify` — debe pasar.
7. Incluir runbook runtime-safe: Plan A JAR + Plan B spring-boot:run PowerShell; advertencia Git Bash.

**Entregables**:
- Tests en `src/test/java/com/wa/banking/auth/`
- JaCoCo check activo en verify
- Reporte `target/site/jacoco/index.html`

**Criterios de aceptacion**:
- [ ] ≥1 caso OK y ≥1 caso error por flujo principal (A1, A3, validate)
- [ ] `./mvnw clean verify` pasa con coverage ≥ 0.70
- [ ] Testcontainers Mongo en integración
- [ ] Criterios spec §7 verificados por tests
- [ ] JaCoCo aislado (`jacocoArgLine`, no runtime pollution)

**Reglas aplicables**: 01-output-format, 02-change-policy, 13-quality-gates, 14-jacoco, 15-runtime-safe-execution

**Definition of Done**: Verify verde; coverage cumple umbral; tests cubren criterios de aceptación §7.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

test completado — 39 tests, verify OK, coverage 85% line

test completado — 40 tests, verify OK, coverage 85% line
