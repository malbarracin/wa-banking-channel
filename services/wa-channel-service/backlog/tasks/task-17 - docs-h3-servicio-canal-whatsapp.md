---
id: TASK-17
title: "[docs] H3 Servicio Canal WhatsApp"
status: Done
priority: medium
created: "2026-06-23T19:42:45.653Z"
parent: TASK-12
---

# [docs] H3 Servicio Canal WhatsApp

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: docs

**Objetivo**: Documentar API H3 — OpenAPI enriquecido, README con runbook/curls, colección Postman, JavaDoc en clases principales.

**Contexto**: Endpoints implementados y testeados. Sin JWT en H3. Error contract unificado.

**Descripcion detallada**:

1. Anotaciones springdoc completas en controller y DTOs.
2. Verificar `/v3/api-docs` y `/swagger-ui/index.html`.
3. **README.md**: descripción H3, prerequisitos, runbook Plan A/Plan B, docker compose, curls F1–F3, ejemplos error contract.
4. **postman/wa-channel-service.postman_collection.json**.
5. **JavaDoc** en clases principales.

**Entregables**: Anotaciones OpenAPI, README, Postman, JavaDoc.

**Criterios de aceptacion**:
- [ ] `/v3/api-docs` responde con paths `/api/v1/channel-links/**`
- [ ] Swagger UI accesible
- [ ] README incluye Plan A JAR y advertencia Git Bash
- [ ] Postman con ≥1 ejemplo por flujo F1–F3
- [ ] JavaDoc en clases principales

**Reglas aplicables**: 01-output-format, 02-change-policy

**Definition of Done**: API autodocumentada; README y Postman permiten probar flujos sin leer código.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

docs completado — OpenAPI, README, Postman, swagger-ui verificado
