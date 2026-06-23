---
id: TASK-38
title: "[docs] H4 — Servicio de Cuentas"
status: Done
priority: medium
created: "2026-06-23T21:36:03.255Z"
parent: TASK-33
---


# [docs] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: docs

**Objetivo**: Documentar API, runbook de ejecución y colección Postman para el piloto F1 de cuentas/saldos.

**Contexto**: Consumidor H3 y operadores necesitan contrato claro, curls de ejemplo y mensajes de error esperados.

**Descripcion detallada**:
1. **OpenAPI/Springdoc**: anotar controllers con `@Operation`, `@ApiResponse`, ejemplos de request/response y errores 401/404.
2. **README.md** (actualizar o crear):
   - Descripción H4 piloto F1.
   - Prerrequisitos: Java 21, Docker, Mongo.
   - **Plan A (Windows)**: `./mvnw -DskipTests package` + `java -jar target/*.jar`.
   - **Plan B**: `./mvnw spring-boot:run` (PowerShell).
   - Advertencia Git Bash.
   - Variables: `integration.session.base-url`, Mongo URI.
   - Curls:
     - Listar cuentas (con headers auth).
     - Consultar saldo.
     - Ejemplo 401 sin token.
3. **Postman collection** en repo (ej. `postman/wa-accounts-service.json`):
   - Variables: `baseUrl`, `credentialId`, `token`.
   - Requests para F1 piloto + ejemplos de error.
4. JavaDoc ya en código (verificar completitud en clases principales).
5. Mensajes al cliente (spec §8): documentar mapping HTTP → mensajes para H3 (tabla referencia en README o handoff draft).

**Entregables**:
- README con runbook runtime-safe
- OpenAPI enriquecido con ejemplos
- Postman collection exportada
- Tabla de integración H2 (validate) y headers requeridos

**Criterios de aceptacion**:
- [ ] `/v3/api-docs` y `/swagger-ui/index.html` responden con endpoints F1 documentados.
- [ ] README incluye Plan A y Plan B + curls auth/no-auth.
- [ ] Postman collection importable con variables y 401/200 examples.
- [ ] Error contract documentado por código HTTP.

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Documentación suficiente para que H3 integre listado y saldo sin ambigüedad.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

docs completado — README, OpenAPI ejemplos, Postman collection

docs completado — README, OpenAPI, Postman
