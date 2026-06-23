---
id: TASK-13
title: "[plan] H3 Servicio Canal WhatsApp"
status: Done
priority: medium
created: "2026-06-23T19:42:43.834Z"
parent: TASK-12
---

# [plan] H3 Servicio Canal WhatsApp

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: plan

**Objetivo**: Traducir la spec H3 en plan técnico ejecutable: modelo de dominio del vínculo WhatsApp↔cliente, contrato REST `/api/v1`, integraciones H1/H2, auditoría e historial de soporte.

**Contexto**: Proyecto `wa-channel-service`, primer feature H3. Sin código previo. La spec gestiona relación número WhatsApp ↔ cliente bancario existente (H1): onboarding (F1), perfil/preferencias (F2), bloqueo/desvinculación (F3), re-vinculación (F4). H2 emite/revoca credencial; H9 conversacional queda fuera de alcance REST inicial.

**Descripcion detallada**:

1. Definir **`base_package`** `com.wa.banking.channel` y estructura MVC (`04-architecture-mvc`): `api/v1/controller`, `api/v1/dto`, `api/v1/mapper`, `entity`, `repository`, `service`, `api/error`, `integration` (clients H1/H2).

2. **Leer artefactos H1** (integración obligatoria spec §9):
   - `backlog/exports/h1-servicio-usuarios/manifest.yaml` → operaciones `provides`
   - `backlog/exports/h1-servicio-usuarios/openapi.yaml` → `GET /api/v1/users/by-document`, `GET /api/v1/users/{id}`, flag `canLinkChannel`
   - `backlog/exports/h1-servicio-usuarios/handoff.md` → URLs, estados `ACTIVE`/`SUSPENDED`/`SOFT_DELETED`

3. Modelar entidades MongoDB:
   - **`WhatsAppLinkEntity`** (`whatsapp_links`): `id`, `phoneNumber` (índice único parcial cuando `status=ACTIVE`), `bankUserId`, `status` (`NO_LINK`, `PENDING_VERIFICATION`, `VERIFICATION_FAILED`, `ACTIVE`, `BLOCKED`, `UNLINKED`), `termsAcceptedAt`, `verificationAttempts`, `verificationBlockedUntil`, `documentType`, `documentNumber` (solo durante verificación), timestamps.
   - **`ChannelPreferences`** (embebido o colección): `language`, `notificationsEnabled`, `quietHoursStart`, `quietHoursEnd`.
   - **`LinkAuditEntryEntity`** (`link_audit_log`): acciones (`TERMS_ACCEPTED`, `VERIFICATION_ATTEMPT`, `LINKED`, `BLOCKED`, `UNLINKED`, `RELINKED`, `CREDENTIAL_REQUESTED`, `CREDENTIAL_REVOKED`), actor, timestamps.
   - **`InteractionHistoryEntity`** (`link_interactions`): resumen para soporte (tipo, timestamp, resultado — sin datos legales sensibles).

4. Mapear flujos a endpoints **`/api/v1/channel-links`** (`09-api-versioning`):

   | Flujo | Método | Path | Descripción |
   |-------|--------|------|-------------|
   | F1 | `GET` | `/api/v1/channel-links/by-phone/{phone}` | Consulta estado vínculo / anti-duplicado |
   | F1 | `POST` | `/api/v1/channel-links` | Iniciar vínculo (número no vinculado) |
   | F1 | `POST` | `/api/v1/channel-links/{id}/accept-terms` | Aceptación términos → `PENDING_VERIFICATION` |
   | F1 | `POST` | `/api/v1/channel-links/{id}/verify` | Verificación identidad + consulta H1 |
   | F1 | `POST` | `/api/v1/channel-links/{id}/complete-onboarding` | Vinculación `ACTIVE` + solicitud credencial H2 |
   | F2 | `GET` | `/api/v1/channel-links/{id}/profile` | Perfil canal (sin datos legales sensibles) |
   | F2 | `GET`/`PATCH` | `/api/v1/channel-links/{id}/preferences` | Preferencias idioma/notificaciones/horarios |
   | F3 | `POST` | `/api/v1/channel-links/{id}/block` | Bloqueo + revocación H2 |
   | F3 | `POST` | `/api/v1/channel-links/{id}/unlink` | Desvinculación + revocación H2 |
   | F4 | `POST` | `/api/v1/channel-links/{id}/relink` | Re-vinculación con verificación completa |
   | Soporte | `GET` | `/api/v1/channel-links/{id}/history` | Historial resumido paginado |
   | Soporte | `GET` | `/api/v1/channel-links/{id}` | Estado del vínculo |

5. Reglas de negocio → capa Service: RN1–RN8 según spec §5.
6. Definir **integración H1**: `UsersClient` → `GET /api/v1/users/by-document`.
7. Definir **integración H2 stub**: `SessionClient` con `issueCredential` / `revokeCredential`.
8. Excepciones dominio → `06-error-contract`.
9. Fijar `coverage.min_line: 0.70`, puerto `8081`, DB `wa-channel`.
10. Excluir: alta cliente H1, productos H4–H6, mensajes conversacionales §8, MCP H9 Fase 2.

**Entregables**:
- Plan en salida del chat (formato planner)
- Tabla endpoint ↔ flujo ↔ regla de negocio
- Lista clases/archivos por capa
- Matriz validaciones Bean Validation por DTO
- Contrato stub H2 documentado
- Decisión política verificación MVP

**Criterios de aceptacion**:
- [ ] F1–F4 tienen endpoint, DTO y regla asignados
- [ ] Estados del vínculo y transiciones definidos sin ambigüedad
- [ ] Integración H1 referenciada con operaciones concretas del OpenAPI exportado
- [ ] H2 stub con métodos issue/revoke documentados
- [ ] Estrategia auditoría + historial soporte definida
- [ ] Alcance excluido explícito (H9 mensajes, MCP, productos)

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Plan técnico completo, sin ambigüedades bloqueantes para scaffold; `coverage.min_line` acordado; handoff claro a scaffold-generator.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

plan completado — 13 endpoints, modelo MongoDB 3 colecciones, H1+H2 stub, coverage 70%
