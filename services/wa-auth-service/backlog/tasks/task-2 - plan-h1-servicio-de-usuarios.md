---
id: TASK-2
title: "[plan] H1 — Servicio de Usuarios"
status: Done
priority: medium
created: "2026-06-22T21:41:02.399Z"
parent: TASK-1
---

# [plan] H1 — Servicio de Usuarios

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: plan

**Objetivo**: Traducir la spec de negocio H1 en un plan técnico ejecutable: modelo de dominio, contrato REST v1, reglas de negocio mapeadas a capas MVC y estrategia de auditoría.

**Contexto**: Proyecto `wa-users-service`, primer hito del piloto bancario. Sin código previo ni dependencias upstream. Autor: licius-it. La spec exige alta, consulta, actualización de campos permitidos, gestión de estados (activo/suspendido/baja lógica), unicidad y auditoría — sin JWT ni canal WhatsApp.

**Descripcion detallada**:
1. Definir **`base_package`** y estructura MVC según rule `04-architecture-mvc` (`api/dto`, `api/error`, `entity`, `repository`, `service`, `mapper`).
2. Modelar entidad **`BankUserEntity`** (MongoDB, colección `bank_users`) con campos mínimos derivados de la spec:
   - Identificador interno (`id`), `documentType`, `documentNumber` (índice único compuesto), `displayName` (nombre para tratamiento), `email`, `phone`, `preferences` (objeto/map genérico no sensible), `status` (`ACTIVE`, `SUSPENDED`, `SOFT_DELETED`), timestamps.
3. Modelar **`UserAuditEntryEntity`** (colección `user_audit_log`) o subdocumento embebido: `userId`, `action` (`CREATED`, `UPDATED`, `STATUS_CHANGED`), `previousStatus`, `newStatus`, `changedFields`, `performedAt`, `result` — cubre reglas 5 y flujos U1/U3/U4.
4. Mapear flujos a endpoints **`/api/v1/users`** (rule `09-api-versioning`):
   - **U1** `POST /api/v1/users` — alta; validar unicidad documento; estado inicial `ACTIVE`; auditar.
   - **U2** `GET /api/v1/users/{id}` y `GET /api/v1/users/by-document?documentType=&documentNumber=` — consulta con DTO de respuesta acotado (sin datos de canal/verificación).
   - **U3** `PATCH /api/v1/users/{id}` — solo `displayName`, `email`, `phone`, `preferences`; auditar cambios relevantes.
   - **U4** `PATCH /api/v1/users/{id}/status` — body `{ "status": "SUSPENDED"|"ACTIVE"|"SOFT_DELETED" }`; validar transiciones; auditar; documentar que notificación a servicios dependientes queda fuera de alcance H1 (stub/evento opcional, no bloqueante).
5. Exponer en respuesta un flag derivado **`canLinkChannel: boolean`** (`false` si `SUSPENDED` o `SOFT_DELETED`) para consumo futuro de H3 — cumple criterio "no puede usarse para nuevo vínculo de canal".
6. Definir excepciones de dominio: `UserNotFoundException`, `DuplicateDocumentException`, `InvalidStatusTransitionException` → mapeo al error contract (`06-error-contract`).
7. Listar archivos a crear en scaffold/build, tests mínimos esperados, y **`coverage.min_line`** acordado para el step `test`.
8. Documentar decisiones: campos Compliance asumidos, enum de estados, política de transiciones (p. ej. `SOFT_DELETED` no reactivable o solo vía soporte).

**Entregables**:
- Plan estructurado en salida del chat (sin archivos en repo)
- Tabla endpoint ↔ flujo ↔ regla de negocio
- Lista de clases/archivos por capa
- Matriz de validaciones Bean Validation por DTO
- Decisión explícita de `base_package` y `coverage.min_line`

**Criterios de aceptacion**:
- [ ] Los 4 flujos U1–U4 tienen endpoint, DTO y regla de negocio asignados
- [ ] Unicidad por documento y estados operativos modelados
- [ ] Estrategia de auditoría definida para altas, cambios de estado y updates relevantes
- [ ] Alcance fuera de spec explícitamente excluido (WhatsApp, JWT, productos, notificación sync)

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Plan técnico completo, sin ambigüedades bloqueantes para scaffold; handoff claro al agente scaffold-generator.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

plan completado — base_package com.wa.banking.users, coverage 0.70, U1-U4 mapeados a MVC v1
