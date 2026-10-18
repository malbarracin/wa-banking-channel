---
name: backend-java-review
description: Revisa código Java/Spring contra quality gates y spec de la entrega. Solo lectura — reporta issues con severidad.
license: MIT
compatibility: opencode
metadata:
  audience: reviewers
  stack: spring-boot
---

# Backend Java — Code Review

Usar con `@code-reviewer` o `/review` tras una entrega.

## Rol

- **Solo lectura** — no modificar código
- Clasificar: CRITICAL / HIGH / MEDIUM / LOW
- Veredicto: `APPROVED` o `NEEDS_CHANGES`

## Qué revisar

1. **Spec compliance** — ¿cumple escenarios del spec de la entrega?
2. **Arquitectura** — capas MVC, MapStruct `*Mapper`, sin lógica en controller
3. **Stack** — Lombok / MapStruct / JaCoCo 80% (`.opensoma/library/rules/stack.md`)
4. **Seguridad** — PII en logs/toString, consolas de BD en prod, validación input
5. **Error contract** — formato único, sin stacktrace, sin `try/catch` en controllers (skill `error-contract`)
6. **Tests** — happy path + error + `./mvnw clean verify` OK
7. **Estilo** — `.opensoma/library/rules/code-quality.md`

La skill `java-code-review` es una lista de chequeo extra; el **formato de salida y las severidades son los de
esta skill** (CRITICAL/HIGH/MEDIUM/LOW + veredicto).

## Issues frecuentes (stack Spring Boot)

| Issue | Severidad típica |
|-------|------------------|
| Consola de BD embebida en perfil default | HIGH |
| Strings en vez de enums para estados cerrados | HIGH |
| PII en `toString()` o logs | HIGH |
| Spec no cumplido | HIGH |
| Coverage < 80% / JaCoCo check fallido | HIGH |
| Mapeo manual sistemático sin MapStruct | HIGH |
| Getters/setters manuales con Lombok en stack | MEDIUM |
| `@Data` en entidad JPA | MEDIUM / HIGH |
| API pública nueva sin Javadoc (Controller/Service/DTO) | MEDIUM |
| Deprecation warnings | LOW |
| Falta test de error | MEDIUM |
| Error con formato distinto al contrato o con stacktrace | HIGH |

## Post-review (informe al orquestador)

| Acción | ¿Hacer? |
|--------|---------|
| Fix Major/High | Sí → listar en `fix-now` (orquestador re-delega; **no** preguntes al humano) |
| Fix MEDIUM rápido | Sí → `fix-now` (env vars, profile, unused, warnings) |
| Follow-up / otra TASK | Solo si cambia alcance → sección `follow-up` |
| Debilitar spec | **No** |
| Marcar Done | **No** (lo hace el orquestador tras fixes + AC) |

Cerrá siempre con **Review verdict** + tabla Findings + `Suggested orchestrator action` (`fix-now` / `follow-up`).
