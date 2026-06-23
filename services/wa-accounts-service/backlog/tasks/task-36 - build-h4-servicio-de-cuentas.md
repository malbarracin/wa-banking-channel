---
id: TASK-36
title: "[build] H4 — Servicio de Cuentas"
status: Done
priority: medium
created: "2026-06-23T21:36:03.255Z"
parent: TASK-33
---


# [build] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: build

**Objetivo**: Implementar la feature piloto F1 — listado de cuentas del titular autenticado y consulta de saldo — con validación JWT vía H2 y filtrado estricto por `bankUserId`.

**Contexto**: Sobre scaffold existente. Toda operación exige credencial válida (RN1). Sin JWT → 401, sin datos expuestos (CA spec §7).

**Descripcion detallada**:
1. **Seguridad (prioridad)**:
   - Implementar `SessionCredentialValidator` que consuma H2 `POST /api/v1/sessions/credentials/validate` (leer `backlog/exports/h2-servicio-acceso-sesion/openapi.yaml`).
   - Modo stub cuando `integration.session.stub-enabled=true` (tests/dev).
   - `OncePerRequestFilter` o `@Component` interceptor: extraer token + credentialId, validar, poblar `SecurityContext` con bankUserId.
   - Respuesta 401 con mensaje alineado a spec: *"Por seguridad, verificá tu identidad para ver tus cuentas."* (campo `message` del error contract).
2. **Endpoints MVP (piloto)**:
   - `GET /api/v1/accounts` — lista cuentas del titular (tipo, alias, moneda, saldos resumidos).
   - `GET /api/v1/accounts/{accountId}/balance` — saldo disponible y contable según producto.
   - Opcional: `GET /api/v1/accounts/by-alias/{alias}` si el canal lo requiere para F1.
3. **Capa de dominio**:
   - `AccountEntity` + `AccountRepository` con query `findByBankUserId`.
   - `AccountService` / `AccountServiceImpl`: solo retorna cuentas del bankUserId autenticado.
   - `AccountMapper` (MapStruct): Entity ↔ DTO Response.
4. **Validaciones**:
   - accountId inexistente o de otro titular → 404 NOT_FOUND.
   - Moneda coherente con producto (RN7 — preparar campo, validación básica).
5. **Logging**: `@Slf4j`, log INFO en operaciones exitosas, WARN en rechazos auth.
6. **No implementar aún** (dejar interfaces/stubs documentados si el plan lo indica): transferencias F3/F4, movimientos F2 — solo si el plan los marca como out-of-scope piloto.
7. JavaDoc en Controller, Service, Repository, Entity, DTOs, Exception handler.

**Entregables**:
- `AccountController`, `AccountService(Impl)`, `AccountRepository`, `AccountEntity`
- DTOs: `AccountResponse`, `AccountBalanceResponse`, `AccountListResponse`
- `SessionCredentialValidator`, filtro de seguridad
- Excepciones: `AccountNotFoundException`, `UnauthorizedException` (mapeadas en GlobalExceptionHandler)

**Criterios de aceptacion**:
- [ ] Sin JWT/credencial inválida → 401, cuerpo error contract, cero datos de cuentas.
- [ ] Con credencial válida (stub o H2 real) → listado solo del titular autenticado.
- [ ] Saldo retorna available + ledger según entity.
- [ ] Cuenta ajena al titular → 404, no filtra por ID global.
- [ ] Endpoints bajo `/api/v1/accounts`.

**Reglas aplicables**: 01-output-format, 02-change-policy, 03-stack, 04-architecture-mvc, 05-gitignore-protection, 06-error-contract, 07-docker-support, 08-actuator-health, 09-api-versioning, 10-java-code-style, 11-java-best-practices, 12-maven-dependency-hygiene

**Definition of Done**: F1 piloto funcional end-to-end con auth H2; compila y responde según criterios de aceptación de la spec.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

build completado — F1 piloto: accounts + balance, auth H2, seed demo, compile OK

build completado — F1 piloto accounts + balance + auth H2
