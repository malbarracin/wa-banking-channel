---
id: TASK-15
title: "[build] H3 Servicio Canal WhatsApp"
status: Done
priority: medium
created: "2026-06-23T19:42:44.759Z"
parent: TASK-12
---

# [build] H3 Servicio Canal WhatsApp

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: build

**Objetivo**: Implementar funcionalidad H3 completa — vínculo WhatsApp, verificación, perfil/preferencias, bloqueo/desvinculación, re-vinculación, historial soporte, integraciones H1/H2.

**Contexto**: Scaffold listo. Leer H1: `backlog/exports/h1-servicio-usuarios/openapi.yaml` y `handoff.md`. H2 como stub configurable.

**Descripcion detallada**:

1. **Entities + Repositories**: `WhatsAppLinkEntity`, `LinkAuditEntryEntity`, `InteractionHistoryEntity` con índices.
2. **DTOs v1**: InitiateLink, AcceptTerms, VerifyIdentity, LinkResponse, Profile, Preferences, Block, Unlink, Relink, InteractionHistory.
3. **MapStruct** mappers.
4. **`WhatsAppLinkServiceImpl`**: F1–F4 completo con reglas RN1–RN8, consulta H1, anti-fraude, OTP MVP, credencial H2 stub, auditoría.
5. **Integraciones**: `UsersClient` (H1 OpenAPI), `SessionClient` stub (issue/revoke).
6. **`WhatsAppLinkControllerV1`**: todos los endpoints del plan con `@Valid`.
7. **Excepciones dominio** + `GlobalExceptionHandler`.
8. Logging SLF4J sin PII sensible.

**Entregables**: Entities, repositories, DTOs, mappers, service, controller, clients H1 + stub H2, excepciones, índices Mongo.

**Criterios de aceptacion**:
- [ ] Cliente inexistente en H1 → error sin vincular
- [ ] Cliente suspendido/baja → no vincula
- [ ] Verificación exitosa → vínculo ACTIVE + credencial H2
- [ ] Segundo vínculo mismo número rechazado
- [ ] Intentos agotados → bloqueo temporal
- [ ] Bloqueo/desvinculación revoca H2
- [ ] Re-vinculación exige nueva verificación
- [ ] Perfil sin datos legales sensibles
- [ ] Secuencia auditada
- [ ] `./mvnw -DskipTests compile` OK

**Reglas aplicables**: 01-output-format, 02-change-policy, 03-stack, 04-architecture-mvc, 05-gitignore-protection, 06-error-contract, 07-docker-support, 08-actuator-health, 09-api-versioning, 10-java-code-style, 11-java-best-practices, 12-maven-dependency-hygiene

**Definition of Done**: Endpoints H3 implementados y compilando; RN1–RN8 cubiertas; H1 operativa; H2 stubbed; listo para tests.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

build completado — 13 endpoints, 47 archivos, RN1-RN8, H1+H2 stub, compile OK
