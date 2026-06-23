---
id: TASK-37
title: "[test] H4 — Servicio de Cuentas"
status: Done
priority: medium
created: "2026-06-23T21:36:03.255Z"
parent: TASK-33
---

# [test] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: test

**Objetivo**: Cubrir con tests automatizados el piloto F1 — auth, listado y saldo — cumpliendo quality gates y umbral JaCoCo.

**Contexto**: Tests con Testcontainers Mongo. Auth H2 mockeado/stub en integración. Windows: ejecución runtime-safe (Plan A jar, no depender de spring-boot:run en Git Bash).

**Descripcion detallada**:
1. **Tests unitarios**:
   - `AccountServiceTest`: listByBankUserId, balanceById, not found, cross-user isolation.
   - `SessionCredentialValidatorTest`: valid=true/false, timeout, stub mode.
2. **Tests de integración** (`@SpringBootTest` + Testcontainers Mongo):
   - `AccountControllerIT`:
     - Sin header auth → 401.
     - Credencial inválida (stub) → 401.
     - Credencial válida → 200 + lista coherente con seed.
     - Balance de cuenta propia → 200 con campos correctos.
     - Balance cuenta inexistente → 404.
3. **GlobalExceptionHandlerIT**: validar formato error contract en 400/404/401/500.
4. Configurar JaCoCo check en verify con umbral de líneas del plan.
5. Asegurar aislamiento JaCoCo (`jacocoArgLine` en surefire, no en runtime).
6. Documentar en README/comentarios comando: `./mvnw clean verify`.

**Entregables**:
- Suite en `src/test/java/**` con mínimo 1 OK + 1 error típico por endpoint piloto
- `application-test.yml` con Mongo Testcontainers
- Reporte JaCoCo en `target/site/jacoco/index.html`

**Criterios de aceptacion**:
- [ ] `./mvnw clean verify` pasa (tests + JaCoCo check).
- [ ] Caso OK: listado con auth válida.
- [ ] Caso error: 401 sin auth, 404 cuenta ajena/inexistente.
- [ ] Coverage líneas ≥ umbral configurado.
- [ ] Testcontainers Mongo levanta y destruye correctamente.

**Reglas aplicables**: 01-output-format, 02-change-policy, 13-quality-gates, 14-jacoco, 15-runtime-safe-execution

**Definition of Done**: Verify verde, reporte JaCoCo generado, casos auth + F1 cubiertos.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

test completado — 26 tests, verify OK, JaCoCo 86% líneas
