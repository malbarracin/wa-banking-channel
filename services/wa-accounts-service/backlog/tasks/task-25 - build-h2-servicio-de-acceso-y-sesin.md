---
id: TASK-25
title: "[build] H2 — Servicio de Acceso y Sesión"
status: Done
priority: medium
created: "2026-06-23T20:39:15.163Z"
parent: TASK-22
---

# [build] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: build

**Objetivo**: Implementar la funcionalidad H2 completa — emisión, renovación, revocación, validación de credenciales, auditoría y compatibilidad con consumidor H3.

**Contexto**: Scaffold listo. Modo aislado del feature-executor: no modificar `pom.xml`/`application.yml` globales salvo reportar `configs_required`. Leer plan aprobado y contrato H3 en `backlog/exports/h3-servicio-canal-whatsapp/handoff.md`.

**Descripcion detallada**:

1. **Entities + Repositories**: `SessionCredentialEntity`, `SessionAuditEntryEntity` con índices (único parcial `ACTIVE` por `channelLinkId`; búsqueda por `bankUserId`).
2. **DTOs v1**: `IssueCredentialRequest`, `IssueCredentialResponse` (incluye `token` solo en emisión), `RenewCredentialResponse`, `ValidateCredentialRequest`, `ValidateCredentialResponse`, `RevokeCredentialRequest`, `CredentialStatusResponse`, `AuditEntryResponse`.
3. **MapStruct** mappers entity ↔ DTO.
4. **`SessionCredentialServiceImpl`**:
   - **A1** `issue`: validar `identityVerified`, opcional consulta H1, revocar activa previa (RN2), generar token opaco, persistir hash, auditar `ISSUED`.
   - **A2** `renew`: solo `ACTIVE` no expirada; extender TTL; auditar `RENEWED`.
   - **A3** `revoke`/`revokeByUser`: efecto inmediato; auditar `REVOKED`.
   - **Validate**: comparar hash token, chequear status/expiry; auditar éxito/fallo sin loguear token.
5. **`SessionCredentialControllerV1`**: todos los endpoints del plan con `@Valid`, `@Tag`, respuestas HTTP correctas (201 emisión, 200 renew/validate, 204 delete revoke).
6. **Integración H1** (si plan lo incluye): `UsersClient` con URL configurable vía `configs_required`.
7. **Excepciones dominio** + mapeo error contract.
8. **Seguridad**: nunca loguear token/credential en claro; no incluir token en audit entries ni GET status.
9. Compatibilidad H3: `POST /api/v1/sessions/credentials` y `DELETE /api/v1/sessions/credentials/{id}` deben funcionar con el payload que H3 envía al desactivar stub.

**Entregables**:
- Entities, repositories, DTOs, mappers, service, controller
- Servicio token/hash interno
- Excepciones dominio
- Client H1 (si aplica) + `configs_required` / `dependencies_required`
- Tests unitarios mínimos del service (si el agente los incluye en módulo)

**Criterios de aceptacion**:
- [ ] Sin `identityVerified=true` → no emite (criterio §7)
- [ ] Segunda emisión mismo `channelLinkId` reemplaza/revoca la anterior (RN2)
- [ ] Revocación efectiva de inmediato en validate (criterio §7)
- [ ] Validate retorna `valid=false` para revocada/expirada
- [ ] Token no aparece en audit ni en GET status
- [ ] Revocación masiva por `bankUserId` revoca todas activas
- [ ] `./mvnw -DskipTests compile` OK
- [ ] Endpoints H3-compatibles operativos

**Reglas aplicables**: 01-output-format, 02-change-policy, 03-stack, 04-architecture-mvc, 05-gitignore-protection, 06-error-contract, 07-docker-support, 08-actuator-health, 09-api-versioning, 10-java-code-style, 11-java-best-practices, 12-maven-dependency-hygiene

**Definition of Done**: Endpoints H2 implementados y compilando; reglas RN1–RN6 cubiertas; listo para test step con merger de configs si aplica.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

build completado — 8 endpoints, 31 archivos creados, RN1-RN6, compile OK
