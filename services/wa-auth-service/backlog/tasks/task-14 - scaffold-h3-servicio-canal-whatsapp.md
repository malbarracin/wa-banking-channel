---
id: TASK-14
title: "[scaffold] H3 Servicio Canal WhatsApp"
status: Done
priority: medium
created: "2026-06-23T19:42:44.277Z"
parent: TASK-12
---

# [scaffold] H3 Servicio Canal WhatsApp

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: scaffold

**Objetivo**: Generar estructura base Spring Boot para `wa-channel-service` — sin lógica de negocio H3.

**Contexto**: Proyecto greenfield. Plan aprobado define `com.wa.banking.channel`. Stack: Java 21, Spring Boot 3.3.x, MongoDB 7, Actuator, springdoc, Lombok, MapStruct, JaCoCo aislado.

**Descripcion detallada**:

1. **`pom.xml`**: parent Spring Boot 3.3.x; starters web, data-mongodb, validation, actuator, test; springdoc-openapi; lombok (optional); mapstruct; testcontainers mongodb (test scope); JaCoCo con `jacocoArgLine` + surefire `${jacocoArgLine}` (`14-jacoco`); maven-compiler-plugin con annotation processors Lombok+MapStruct.

2. **`WaChannelServiceApplication.java`** en `com.wa.banking.channel`.

3. **`application.yml`** (perfil `local` default): `server.port: 8081`, Mongo `wa-channel`, URLs H1/H2 stub, actuator, springdoc.

4. **`docker-compose.yml`**: servicio `mongo:7`, puerto 27017.

5. **Maven Wrapper** (`./mvnw`, `.mvn/wrapper/`).

6. **`.gitignore`** según `05-gitignore-protection`.

7. **Estructura carpetas vacía** bajo `com.wa.banking.channel` (config, api/v1, entity, repository, service, integration).

8. **`GlobalExceptionHandler`** + `ErrorResponse` record según `06-error-contract` (esqueleto).

9. **`SwaggerConfig`** / `GroupedOpenApi` para `/api/v1/**`.

10. **Dockerfile** desde template `.cursor/templates/Dockerfile.native.md`.

11. **NO** crear entities, services, controllers de negocio H3.

**Entregables**: pom.xml, wrapper, application.yml, docker-compose.yml, .gitignore, Application class, config OpenAPI, error handler base, estructura paquetes vacía, Dockerfile.

**Criterios de aceptacion**:
- [ ] `./mvnw -DskipTests compile` OK
- [ ] JaCoCo no modifica `argLine` global (solo `jacocoArgLine`)
- [ ] Mongo levanta con `docker compose up -d`
- [ ] `GET /actuator/health` responde tras arranque (Plan A JAR)
- [ ] `.gitignore` protege `.cursor/` y artefactos pipeline
- [ ] Sin código de negocio H3

**Reglas aplicables**: 01-output-format, 02-change-policy, 03-stack, 04-architecture-mvc, 05-gitignore-protection, 06-error-contract, 07-docker-support, 08-actuator-health, 09-api-versioning, 12-maven-dependency-hygiene

**Definition of Done**: Proyecto compila y arranca con health OK; estructura MVC lista para feature-executor.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

scaffold completado — Spring Boot 3.3.5, compile OK, estructura MVC vacía, JaCoCo aislado
