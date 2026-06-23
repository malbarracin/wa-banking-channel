---
id: TASK-34
title: "[plan] H4 — Servicio de Cuentas"
status: Done
priority: medium
created: "2026-06-23T21:36:03.255Z"
parent: TASK-33
---


# [plan] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: plan

**Objetivo**: Definir el plan de implementación de `wa-accounts-service` (H4) con foco piloto en F1 (listado de cuentas + consulta de saldo), dejando F2–F4 planificados pero fuera del MVP inmediato.

**Contexto**: Backend REST Spring Boot 3.3 + MongoDB para el canal WhatsApp bancario. Consumidor principal: H3 (canal). Autenticación obligatoria vía validación de credencial H2 (`POST /api/v1/sessions/credentials/validate`). Sin features previas en este repo.

**Descripcion detallada**:
1. Leer artefactos de integración:
   - `backlog/exports/h2-servicio-acceso-sesion/openapi.yaml` → contrato `validate` (credentialId + token → bankUserId, channelLinkId).
   - `backlog/exports/h2-servicio-acceso-sesion/handoff.md` → URL base, propiedades de integración.
   - `backlog/exports/h3-servicio-canal-whatsapp/manifest.yaml` → contexto de consumidor.
2. Definir **fases**:
   - **Fase piloto (MVP)**: F1 — `GET /api/v1/accounts` (listado titular) y `GET /api/v1/accounts/{id}/balance` (saldo disponible/contable).
   - **Fase 2**: F2 — movimientos paginados con filtros fecha/tipo (ventana 90 días, tope registros).
   - **Fase 3**: F3/F4 — transferencias propias y a terceros agendados, límites, horarios, doble confirmación.
3. Diseñar contrato API v1 (OpenAPI draft) con prefijo `/api/v1/accounts`, error contract estándar (`code`, `message`, `details`, `timestamp`).
4. Definir modelo de dominio MongoDB:
   - `AccountEntity`: id, bankUserId, alias, type, currency, availableBalance, ledgerBalance, status.
   - `MovementEntity` (fase 2): accountId, date, type, amount, description, reference.
   - `ScheduledRecipientEntity` (fase 3): bankUserId, alias, accountRef.
   - `TransferEntity` (fase 3): origen, destino, amount, status, reference.
5. Definir estrategia de seguridad:
   - Header `Authorization: Bearer {token}` + header/param `X-Credential-Id` (o convención acordada con H3).
   - Filtro/interceptor que llame a H2 validate; si `valid=false` → 401 sin consultar productos.
   - Scope por `bankUserId` retornado por H2.
6. Definir configuración (`application.yml`): `integration.session.base-url` (default `http://localhost:8082`), `integration.session.stub-enabled` para tests locales.
7. Documentar decisiones de arquitectura MVC por capas (`api/`, `service/`, `repository/`, `entity/`, `mapper/`).
8. Entregar plan con orden de implementación, riesgos (JWT robado, monedas) y supuestos del core bancario.

**Entregables**:
- Plan escrito en chat/output del planner (sin crear archivos de código).
- Borrador de endpoints MVP y modelo de datos.
- Mapa de integración H2/H3 con propiedades y puertos.

**Criterios de aceptacion**:
- [ ] F1 piloto acotado explícitamente (listado + saldo); F2–F4 diferidos con criterios de entrada.
- [ ] Contrato de auth alineado con H2 `ValidateCredentialRequest/Response`.
- [ ] Paquetes y naming MVC definidos bajo `com.wa.banking.accounts` (o base acordada).
- [ ] Error contract y versionado `/api/v1` documentados en el plan.

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Plan aprobado por el orquestador con fases, endpoints MVP, modelo de datos e integración H2 listos para scaffold.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

plan completado — MVP F1 acotado, contrato API v1, modelo MongoDB, integración H2/H3, arquitectura com.wa.banking.accounts

plan completado — MVP F1 acotado, integración H2/H3
