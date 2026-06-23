---
id: TASK-6
title: "[docs] H1 — Servicio de Usuarios"
status: Done
priority: medium
created: "2026-06-22T21:41:04.165Z"
parent: TASK-1
---

# [docs] H1 — Servicio de Usuarios

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: docs

**Objetivo**: Documentar API REST del servicio de usuarios: OpenAPI/Swagger con ejemplos, README operativo y colección Postman alineados a los flujos U1–U4.

**Contexto**: Implementación y tests verdes. Quality gates exigen Swagger funcional, README con curls, Postman collection, JavaDoc en clases principales (verificado/completado si faltara).

**Descripcion detallada**:
1. Anotar controllers/DTOs con `@Operation`, `@ApiResponse`, `@Schema` y **ejemplos** por endpoint:
   - Alta usuario activo
   - Error duplicado documento
   - Consulta por id/documento
   - Update campos permitidos
   - Cambio de estado suspendido/baja lógica
2. Verificar `/v3/api-docs` y `/swagger-ui/index.html` responden.
3. **`README.md`**: descripción H1, prerequisitos (Java 21, Docker Mongo), runbook Plan A/B, curls por flujo, ejemplos de respuesta error (400/404/500).
4. **`postman/wa-users-service.postman_collection.json`**: variables `baseUrl`, ejemplos encadenados (create → get → patch → change status).
5. Completar JavaDoc faltante en Controller/Service/Repository/Entity/DTO/Error handler con author/email del proyecto.

**Entregables**:
- Anotaciones OpenAPI en código
- `README.md` actualizado
- Colección Postman con variables y ejemplos

**Criterios de aceptacion**:
- [ ] Swagger UI accesible con endpoints `/api/v1/users/**` documentados
- [ ] Cada endpoint tiene al menos un ejemplo request/response
- [ ] README incluye curls + ejemplos de error contract
- [ ] Postman collection importable con flujo completo U1→U4

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: Documentación API y operativa lista para demo del piloto H1.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

docs completado — OpenAPI examples, README, Postman collection
