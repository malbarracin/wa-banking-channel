---
id: TASK-27
title: "[docs] H2 — Servicio de Acceso y Sesión"
status: Done
priority: medium
created: "2026-06-23T20:39:15.163Z"
parent: TASK-22
---

# [docs] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: docs

**Objetivo**: Documentar API H2 — OpenAPI/Swagger con ejemplos, README runbook, colección Postman y JavaDoc en clases principales.

**Contexto**: Implementación y tests verdes. Consumidores: H3 (issue/revoke), H4–H6 (validate).

**Descripcion detallada**:

1. Anotar controllers y DTOs: `@Tag`, `@Operation`, `@ApiResponses`, `@Schema` con ejemplos realistas (sin tokens reales en ejemplos estáticos — usar placeholders `cred_xxx`).
2. Verificar `/v3/api-docs` y `/swagger-ui/index.html` responden.
3. Generar/actualizar **`openapi.yaml`** en repo o export desde springdoc.
4. **`README.md`**: descripción H2, prerequisitos Mongo, Plan A/Plan B, curls issue/validate/revoke, ejemplos error contract, puerto 8082.
5. **`postman/wa-auth-service.postman_collection.json`**: variables `baseUrl=http://localhost:8082`, requests encadenados issue → validate → revoke.
6. JavaDoc en Controller, Service, Repository, Entity, DTOs, GlobalExceptionHandler (`@author`, `@since`, contacto).

**Entregables**:
- Anotaciones Swagger completas
- `README.md` con runbook + curls + errores
- `postman/wa-auth-service.postman_collection.json`
- `openapi.yaml` (si aplica)

**Criterios de aceptacion**:
- [ ] `/v3/api-docs` responde
- [ ] Swagger UI accesible
- [ ] Endpoints documentados con ejemplos request/response
- [ ] Postman collection con flujo A1→validate→A3
- [ ] JavaDoc en clases principales

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: API autodocumentada; README y Postman permiten probar H2 sin leer código.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

docs completado — Swagger, README, Postman, openapi.yaml
