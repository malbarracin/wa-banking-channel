---
id: TASK-23
title: "[plan] H2 — Servicio de Acceso y Sesión"
status: Done
priority: medium
created: "2026-06-23T20:39:15.163Z"
parent: TASK-22
---

# [plan] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: plan

**Objetivo**: Traducir la spec H2 en plan técnico ejecutable: modelo de credencial de sesión, contrato REST v1, flujos A1–A3 mapeados a capas MVC, estrategia de auditoría y compatibilidad con el consumidor H3.

**Contexto**: Proyecto `wa-auth-service`, primer hito H2, sin código previo. Gestiona credencial opaca de sesión WhatsApp: emite tras verificación (H3), renueva con vínculo activo, revoca al instante, valida para productos (H4–H6). Fuera de alcance: alta usuario (H1), flujo conversacional (H3), operaciones de productos, formato criptográfico detallado de la credencial.

**Descripcion detallada**:

1. Definir **`base_package`** `com.wa.banking.auth` y estructura MVC (`04-architecture-mvc`): `api/v1/controller`, `api/v1/dto`, `api/v1/mapper`, `entity`, `repository`, `service`, `api/error`, `integration` (client H1 opcional).

2. **Leer contrato consumidor H3** (referencia informativa, no upstream declarado):
   - `backlog/exports/h3-servicio-canal-whatsapp/manifest.yaml` → operaciones H2 esperadas
   - `backlog/exports/h3-servicio-canal-whatsapp/handoff.md` → puerto `:8082`, flujos RN3/RN4
   - Garantizar que `POST /api/v1/sessions/credentials` y `DELETE /api/v1/sessions/credentials/{id}` sean compatibles con lo que H3 invoca vía `SessionClient`.

3. Modelar entidades MongoDB:
   - **`SessionCredentialEntity`** (`session_credentials`): `id`, `channelLinkId`, `bankUserId`, `phoneNumber`, `status` (`ACTIVE`, `REVOKED`, `EXPIRED`), `tokenHash` (nunca persistir token en claro), `issuedAt`, `expiresAt`, `revokedAt`, `revokeReason` (`BLOCK`, `UNLINK`, `FRAUD`, `FAILED_ATTEMPTS`, `POLICY`, `REPLACED`), `renewalCount`, timestamps. Índice único parcial: una credencial `ACTIVE` por `channelLinkId`.
   - **`SessionAuditEntryEntity`** (`session_audit_log`): `credentialId`, `channelLinkId`, `bankUserId`, `action` (`ISSUED`, `RENEWED`, `REVOKED`, `VALIDATED`, `VALIDATION_FAILED`, `REPLACED`), `actor` (`CHANNEL`, `PRODUCT`, `BANK`, `SYSTEM`), `reason`, `performedAt`, metadata sin secretos.

4. Mapear flujos spec §6 a endpoints **`/api/v1/sessions`** (`09-api-versioning`):

   | Flujo | Método | Path | Descripción |
   |-------|--------|------|-------------|
   | A1 | `POST` | `/api/v1/sessions/credentials` | Emisión tras verificación H3; body: `channelLinkId`, `bankUserId`, `phoneNumber`, `identityVerified=true`; retorna `credentialId` + `token` **una sola vez** (RN5: no exponer en chat — responsabilidad H3 al transportar) |
   | A2 | `POST` | `/api/v1/sessions/credentials/{id}/renew` | Renovación si `ACTIVE`, no expirada, sin señal de riesgo; extiende `expiresAt` |
   | A3 | `DELETE` | `/api/v1/sessions/credentials/{id}` | Revocación inmediata (consumo H3 block/unlink) |
   | A3 | `POST` | `/api/v1/sessions/credentials/{id}/revoke` | Revocación con body `{ "reason": "..." }` para banco/riesgo |
   | A3 | `POST` | `/api/v1/sessions/credentials/revoke-by-user` | Revocación masiva por `bankUserId` (actor banco) |
   | Validación | `POST` | `/api/v1/sessions/credentials/validate` | Para H4–H6: body `credentialId` + `token`; respuesta `{ valid, bankUserId, channelLinkId, expiresAt }` |
   | Consulta | `GET` | `/api/v1/sessions/credentials/{id}` | Estado metadata sin token (soporte/auditoría) |
   | Auditoría | `GET` | `/api/v1/sessions/credentials/{id}/audit` | Historial paginado de la credencial |

5. Reglas de negocio spec §5 → capa Service:
   - RN1: rechazar emisión si `identityVerified != true`.
   - RN2: al emitir, revocar/reemplazar credencial `ACTIVE` previa del mismo `channelLinkId`.
   - RN3: vigencia configurable (p. ej. TTL default 24h); renovación sin re-onboarding.
   - RN4: revocación marca `REVOKED` inmediato; validaciones posteriores fallan.
   - RN5: respuesta de emisión incluye token pero documentar que H3 no lo reenvía al cliente.
   - RN6: credencial `REVOKED`/`EXPIRED` → `valid=false` en validate.

6. Integración H1 (opcional MVP): `UsersClient` → `GET /api/v1/users/{id}` para verificar que `bankUserId` existe y `status=ACTIVE` antes de emitir. Referencia: `backlog/exports/h1-servicio-usuarios/openapi.yaml`.

7. Excepciones dominio → `06-error-contract`: `CredentialNotFoundException`, `CredentialAlreadyRevokedException`, `InvalidCredentialException`, `DuplicateActiveCredentialException`, `VerificationRequiredException`, `UserNotEligibleException`.

8. Fijar **`coverage.min_line: 0.70`**, puerto **`8082`**, DB Mongo **`wa-auth`**, TTL default credencial, política de hash de token (BCrypt/SHA-256 — documentar decisión).

9. Excluir explícitamente: JWT/OAuth2, mensajería WhatsApp, operaciones de productos, detalle criptográfico avanzado (JWT firmado, etc.).

**Entregables**:
- Plan estructurado en salida del chat (formato planner)
- Tabla endpoint ↔ flujo A1–A3 ↔ regla de negocio
- Lista clases/archivos por capa
- Matriz Bean Validation por DTO
- Contrato request/response alineado con H3 `SessionClient`
- Decisión `base_package`, TTL, hash strategy, `coverage.min_line`

**Criterios de aceptacion**:
- [ ] Flujos A1–A3 tienen endpoint, DTO y regla asignados
- [ ] Una credencial activa por vínculo modelada con índice
- [ ] Endpoint validate cubre criterio §7 (productos rechazan inválida/revocada)
- [ ] Compatibilidad documentada con H3 (`POST` issue + `DELETE` revoke)
- [ ] Estrategia auditoría sin almacenar secretos en logs/audit
- [ ] Alcance excluido explícito (H1 alta, H3 conversación, productos)

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Plan técnico completo, sin ambigüedades bloqueantes para scaffold; `coverage.min_line` acordado; handoff claro al scaffold-generator.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

plan completado — 8 endpoints REST, 2 entidades MongoDB, compat H3 SessionClient, coverage 0.70
