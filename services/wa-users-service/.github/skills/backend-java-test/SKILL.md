---
name: backend-java-test
description: Crea tests JUnit 5 para features Spring Boot sin modificar código de producción.
license: MIT
compatibility: opencode
metadata:
  audience: developers
  stack: spring-boot
---

# Backend Java — Tests

Usar con `@test-generator` o durante implementación cuando la entrega requiere tests.

## Prohibiciones

- NO modificar `src/main/java/**`
- NO eliminar tests que pasan
- NO agregar dependencias sin permiso

## Tipos de test

| Tipo | Anotación | Cuándo |
|------|-----------|--------|
| Repository | `@DataJpaTest` | JPA / persistencia |
| Controller | `@WebMvcTest` | HTTP, validación |
| Integración | `@SpringBootTest` | Flujo completo (moderación) |

Adaptar según stack del `pom.xml` (ej. `@DataMongoTest` si usa MongoDB).

## Mínimo por feature

- 1 happy path
- 1 error típico (400, 404, 409, etc.) que verifique status **y** cuerpo (`$.code`, `$.details`) — skill `error-contract`
- Suficiente para mantener **JaCoCo ≥ 80%** en el módulo (gate de build)

## Convenciones

- JUnit 5 + AssertJ
- `@DisplayName` y nombres según `java-code-style`
- Mocks de beans en tests de Spring: `@MockitoBean` (Spring Boot 3.4+; `@MockBean` está deprecado)
- Seguir estilo de tests ya existentes en el repo
- Cubrir ramas de Service/Mapper/Controller tocadas por la TASK

## JaCoCo (obligatorio — ver `stack.md` / `quality-gates.md`)

- Umbral: **80%** line coverage
- Plugin `jacoco-maven-plugin` con `check` en `verify`
- Si el POM aún no tiene JaCoCo (greenfield): reportarlo; foundation/bootstrap debe agregarlo — no bajar el umbral

## Verificación

```bash
./mvnw test
./mvnw clean verify   # debe pasar JaCoCo check (≥ 80%)
```
