---
name: backend-java-architect
description: >
  Define estructura de paquetes OBLIGATORIA por subpaquetes (controller/service/repository/mapper/entity/dto),
  endpoints y tests. Sin código. Usar en fase plan con agente planner.
license: MIT
compatibility: opencode
metadata:
  audience: tech-leads
  stack: spring-boot
---

# Backend Java — Arquitectura

Usar con **`planner`**. **Primera tool call** de la sesión del planner = esta skill.

## Objetivo

- Resolver `<base_package>` (`java-project-identity.md`)
- Fijar **árbol de paquetes por capa** (no plano)
- Contratos de endpoints, DTOs y errores por endpoint (skill `error-contract`)
- Versionado de la API si aplica (skill `api-versioning`)
- Estrategia de tests
- **No escribís código**: el plan lo implementan los devs, sin esperar aprobación humana

## Estructura OBLIGATORIA

La de `architecture.md`: por módulo de dominio, con subpaquetes por capa
(`<module>/{controller,service,repository,mapper,entity,dto}`), más `config/` y `exception/`.

En el plan, listá **paths FQCN o rutas de archivo** con esos subpaquetes. Si el repo está plano hoy → el plan
incluye **migración a subpaquetes** (no seguir el anti-patrón).

## Entregable del plan

- Archivos con paths bajo `…/<module>/{controller,service,repository,mapper,entity,dto}/`
- Endpoints (método, path, request, response y errores: HTTP + `code`)
- Mappers MapStruct
- Tests (JaCoCo ≥ 80%)
- Riesgos / deps POM

## Rules

- `.opensoma/library/rules/architecture.md` (paquetes anidados)
- `.opensoma/library/rules/stack.md`
- `.opensoma/library/rules/java-project-identity.md`
