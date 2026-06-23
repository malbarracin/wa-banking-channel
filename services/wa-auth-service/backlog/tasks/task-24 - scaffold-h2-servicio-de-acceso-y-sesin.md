---
id: TASK-24
title: "[scaffold] H2 — Servicio de Acceso y Sesión"
status: Done
priority: medium
created: "2026-06-23T20:39:15.163Z"
parent: TASK-22
---

# [scaffold] H2 — Servicio de Acceso y Sesión

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: scaffold

**Objetivo**: Generar el esqueleto Spring Boot 3.3.x + MongoDB de `wa-auth-service` con infraestructura base, error contract, health, Docker, API v1 vacía y JaCoCo preparado.

**Contexto**: Greenfield tras `plan`. Stack Java 21, MVC, Mongo 7, Lombok, MapStruct, springdoc. Template `.cursor/templates/Dockerfile.native.md`. Sin lógica de sesión aún.

**Descripcion detallada**:

1. Crear proyecto Maven con **`./mvnw`**, `pom.xml` mínimo: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-test`, Testcontainers Mongo, Lombok, MapStruct, springdoc-openapi.
2. Annotation processors Lombok + MapStruct + binding (`03-stack`, `12-maven-dependency-hygiene`).
3. Estructura bajo `com.wa.banking.auth` (`04-architecture-mvc`):
   - `api/v1/controller/` — `SessionCredentialControllerV1` esqueleto `@RequestMapping("/api/v1/sessions/credentials")`
   - `api/v1/dto/`, `api/v1/mapper/`, `entity/`, `repository/`, `service/`, `api/error/`
4. Clase principal `WaAuthServiceApplication`.
5. **`application.yml`**: perfil `local` default, `mongodb://localhost:27017/wa-auth`, `server.port: 8082`, actuator expuesto (`08-actuator-health`), propiedades TTL sesión placeholder.
6. **`GlobalExceptionHandler`** + DTO error `{ code, message, details, timestamp }` (`06-error-contract`).
7. **`docker-compose.yml`**: Mongo 7 (`07-docker-support`).
8. **`.gitignore`** con exclusiones SDD (`05-gitignore-protection`).
9. Swagger `GroupedOpenApi` para `/api/v1/**` (`09-api-versioning`).
10. JaCoCo con `propertyName=jacocoArgLine` + surefire `${jacocoArgLine}` (`14-jacoco` prep).
11. Dockerfile nativo desde `.cursor/templates/Dockerfile.native.md`.
12. JavaDoc cabecera: `@author licius-it`, `@since 2026-06-23`, email pipeline-context.

**Entregables**:
- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`
- Estructura MVC v1 vacía
- `application.yml`, `docker-compose.yml`, `.gitignore`
- `GlobalExceptionHandler` + clases error
- Dockerfile nativo
- `README.md` mínimo con Plan A/Plan B runbook (`15-runtime-safe-execution` preview)

**Criterios de aceptacion**:
- [ ] `./mvnw -DskipTests compile` exitoso
- [ ] Actuator health configurado
- [ ] Mongo levantable con docker-compose
- [ ] Error contract base implementado
- [ ] JaCoCo no modifica `argLine` global (solo `jacocoArgLine`)
- [ ] `.gitignore` incluye exclusiones agente SDD

**Reglas aplicables**: 01-output-format, 02-change-policy, 03-stack, 04-architecture-mvc, 05-gitignore-protection, 06-error-contract, 07-docker-support, 08-actuator-health, 09-api-versioning, 12-maven-dependency-hygiene

**Definition of Done**: Proyecto compila; infra lista para implementar credenciales en `build`.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

scaffold completado — 22 archivos, compile OK, JaCoCo aislado, controller esqueleto v1
