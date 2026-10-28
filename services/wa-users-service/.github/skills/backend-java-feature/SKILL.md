---
name: backend-java-feature
description: Implementa features Java/Spring Boot siguiendo MVC, persistencia del POM, error contract y reglas en .opensoma/library/rules/.
license: MIT
compatibility: opencode
metadata:
  audience: developers
  stack: spring-boot
---

# Backend Java — Implementación de features

Usar con agente `build` o `@feature-executor` al implementar una unidad de trabajo.

## Antes de empezar

1. Leer workflow del proyecto en `AGENTS.md` (raíz del repo)
2. Leer tarea/change asignada y spec de requisitos referenciado
3. Inferir del repo (ver **`.opensoma/library/rules/java-project-identity.md`**):
   - `<base_package>` — `project.yaml` → `java.base_package`, o `pom.xml` / `*Application.java`
   - Si la TASK trae paquete sin fuente verificable o contradice yaml/pom → **`question`** → STOP
   - Persistencia — JPA, MongoDB, etc. según dependencias del POM
   - Stack obligatorio: **Lombok**, **MapStruct**, **JaCoCo ≥ 80%** (`.opensoma/library/rules/stack.md`)
   - Convenciones de naming ya usadas en el módulo

## Flujo

1. Leer la tarea que te asigna el paso (y su spec). No cambies su estado ni tomes otra: lo maneja el
   orquestador (protocolo de equipo).
2. Implementar con cambios mínimos (Lombok + MapStruct `*Mapper`), sin esperar aprobación: el plan ya está en la
   tarea. Si falta algo que no se puede decidir con la spec, la arquitectura y las reglas → nota `BLOQUEADO:`.
3. `./mvnw test` y, antes de terminar, `./mvnw clean verify` (JaCoCo 80%).
4. Marcar con `task_edit` (MCP `opensoma`, `check: [n]`) los criterios de aceptación cumplidos y dejar la nota de qué hiciste y cómo se
   verifica.

## Capas (MVC) — paquetes OBLIGATORIOS

`src/main/java/<base_package>/<module>/{controller|service|repository|mapper|entity|dto}/`, según
`architecture.md` (con versionado de API: skill `api-versioning`). **Prohibido** el paquete plano; si el módulo
ya está plano → **mover** a subpaquetes en esta tarea (o en la primera que toque el módulo).

Reglas: `.opensoma/library/rules/architecture.md`, `stack.md`, `code-quality.md`, `quality-gates.md`.

## Documentación mínima (obligatoria en código nuevo)

Al crear/modificar API pública, dejá **Javadoc** usable (el `@documenter` del orquestador completa README/Swagger/`docs/`):

| Superficie | Mínimo |
|------------|--------|
| `*Controller` | class + cada endpoint público (`@param` / `@return` o descripción del contrato) |
| `*Service` público | class + métodos públicos de caso de uso |
| DTOs públicos (`*Request` / `*Response`) | class + campos no obvios |
| Enums de dominio expuestos | class + constantes no triviales |

Detalle: `.opensoma/library/rules/documentation.md`. No inventes endpoints; documentá lo que implementás.

## Checklist antes de Done

- [ ] Paquetes anidados (`controller`/`service`/`repository`/`mapper`/`entity`/`dto`) — no plano
- [ ] Cumple escenarios del spec de la entrega
- [ ] Errores con el contrato único: excepciones de dominio lanzadas desde el service (skill `error-contract`)
- [ ] Lombok / MapStruct según stack (sin boilerplate ni mapeo manual masivo)
- [ ] **Javadoc mínimo** en Controllers / Services públicos / DTOs tocados
- [ ] Consola de BD embebida solo en perfil `dev` (si aplica)
- [ ] `toString()` sin PII
- [ ] Tests: happy path + error típico
- [ ] `./mvnw clean verify` — JaCoCo ≥ **80%**
- [ ] Criterios de aceptación marcados (`task_edit`, MCP `opensoma`) y nota de cierre

## Prohibiciones

- No paquete plano por módulo
- No reescribir specs de requisitos
- No tomar otra tarea ni cambiar el estado de la tarea (lo hace el orquestador)
- No refactor fuera de alcance (salvo migrar a subpaquetes del módulo tocado)
- No bajar el umbral JaCoCo ni omitir MapStruct/Lombok del stack sin OK
