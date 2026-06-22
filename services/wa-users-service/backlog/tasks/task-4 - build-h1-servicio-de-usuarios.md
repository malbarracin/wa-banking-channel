---
id: TASK-4
title: "[build] H1 — Servicio de Usuarios"
status: Done
priority: medium
created: "2026-06-22T21:41:03.271Z"
parent: TASK-1
---

# [build] H1 — Servicio de Usuarios

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: build

**Objetivo**: Implementar la funcionalidad completa H1 — flujos U1–U4, reglas de unicidad, estados operativos, auditoría y respuestas acotadas — sobre el scaffold MVC.

**Contexto**: Scaffold listo. Spec: personas clientes del banco; operaciones de negocio/operaciones y consulta por otros servicios; sin autenticación JWT en H1.

**Descripcion detallada**:
1. **Entity + Repository**:
   - `BankUserEntity` con índice único `{ documentType, documentNumber }`.
   - `UserAuditRepository` / persistencia de auditoría.
   - Queries: `findById`, `findByDocumentTypeAndDocumentNumber`, `existsByDocumentTypeAndDocumentNumber`.
2. **DTOs v1** (records o Lombok inmutable): `CreateUserRequestV1`, `UpdateUserRequestV1`, `ChangeUserStatusRequestV1`, `UserResponseV1`, `UserAuditResponseV1` (opcional para soporte).
3. **MapStruct** `UserMapperV1`: Entity ↔ DTO; nunca exponer entity en API.
4. **`UserService` / `UserServiceImpl`** — lógica de negocio:
   - **U1 create**: validar datos mínimos (`@NotBlank`, `@Email`, etc.); rechazar duplicado → `DuplicateDocumentException` (400 BAD_REQUEST); persistir `ACTIVE`; auditar `CREATED`.
   - **U2 findById / findByDocument**: `UserNotFoundException` (404); response solo campos autorizados + `status` + `canLinkChannel`.
   - **U3 update**: mutar solo campos permitidos; rechazar campos legales sensibles; auditar `UPDATED` con diff de campos.
   - **U4 changeStatus**: validar transiciones según política definida en plan; auditar `STATUS_CHANGED` con previous/new; `canLinkChannel=false` para SUSPENDED/SOFT_DELETED.
5. **`UserControllerV1`**: endpoints del plan; `@Valid` en requests; códigos HTTP correctos (201 create, 200 consulta/update).
6. **Excepciones de dominio** mapeadas en `GlobalExceptionHandler` al contrato unificado.
7. Opcional: `GET /api/v1/users/{id}/audit` paginado para actor soporte (historial resumido — sin datos de canal).
8. Logging SLF4J en operaciones principales; sin datos sensibles en logs (`11-java-best-practices`).
9. Estilo Java 4 espacios, naming conventions (`10-java-code-style`).
10. No agregar JWT, WhatsApp, ni integraciones H2/H3/H4.

**Entregables**:
- Entity, repositories, DTOs v1, mapper, service, controller
- Excepciones de dominio + handlers
- Índices Mongo declarados en entity o `@Indexed`

**Criterios de aceptacion**:
- [ ] Alta con datos válidos → usuario `ACTIVE` con ID asignado
- [ ] Alta duplicada por documento → error 400, sin persistir segundo registro
- [ ] Consulta devuelve solo datos autorizados + estado + `canLinkChannel`
- [ ] Cambio de estado persiste y genera entrada de auditoría
- [ ] Usuario SUSPENDED/SOFT_DELETED tiene `canLinkChannel=false`
- [ ] `./mvnw -DskipTests compile` OK

**Reglas aplicables**: 01-output-format, 02-change-policy, 03-stack, 04-architecture-mvc, 05-gitignore-protection, 06-error-contract, 07-docker-support, 08-actuator-health, 09-api-versioning, 10-java-code-style, 11-java-best-practices, 12-maven-dependency-hygiene

**Definition of Done**: Endpoints H1 implementados y compilando; reglas de negocio 1–6 de la spec cubiertas en código; listo para tests de integración.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

build completado — U1-U4 implementados, 6 endpoints, auditoría, compile OK
