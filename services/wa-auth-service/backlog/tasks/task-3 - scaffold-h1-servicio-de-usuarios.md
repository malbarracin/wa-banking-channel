---
id: TASK-3
title: "[scaffold] H1 — Servicio de Usuarios"
status: Done
priority: medium
created: "2026-06-22T21:41:02.838Z"
parent: TASK-1
---

# [scaffold] H1 — Servicio de Usuarios

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: scaffold

**Objetivo**: Generar el esqueleto Spring Boot 3.3.x + MongoDB del servicio `wa-users-service` con infraestructura base, error contract, health, Docker y estructura MVC v1 vacía lista para implementar H1.

**Contexto**: Greenfield tras `plan`. Stack fijo Java 21, MVC, Mongo, Lombok, MapStruct. Template `.cursor/templates/Dockerfile.native.md` para imagen nativa. Sin JWT (spec: `Requiere credencial JWT: NO`).

**Descripcion detallada**:
1. Crear proyecto Maven con **`./mvnw`**, `pom.xml` mínimo: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-test`, Testcontainers Mongo, Lombok, MapStruct, springdoc-openapi (para quality gate docs posterior).
2. Configurar annotation processors Lombok + MapStruct + binding (`03-stack`, `12-maven-dependency-hygiene`).
3. Estructura de paquetes según `base_package` del plan bajo capas MVC (`04-architecture-mvc`):
   - `api/v1/` (controller, dto placeholders)
   - `entity/`, `repository/`, `service/`, `mapper/`, `api/error/`
4. Clase principal `@SpringBootApplication`.
5. **`application.yml`**: perfil `local` por defecto, URI Mongo `mongodb://localhost:27017/wa-users`, puerto servicio, config actuator expuesta (`08-actuator-health`).
6. **`GlobalExceptionHandler`** + DTO error unificado `{ code, message, details, timestamp }` (`06-error-contract`).
7. **`docker-compose.yml`**: Mongo 7 puerto estándar (`07-docker-support`).
8. **`.gitignore`** con exclusiones SDD/agente (`05-gitignore-protection`).
9. Controllers con `@RequestMapping("/api/v1/users")` esqueleto sin lógica de negocio aún (`09-api-versioning`).
10. JaCoCo plugin en `pom.xml` con `jacocoArgLine` + surefire `argLine` (`14-jacoco` — preparación; umbral en verify lo activa `test`).
11. Dockerfile nativo desde template si aplica al pack.
12. JavaDoc cabecera en clases principales con `@author licius-it`, `@since 2026-06-22`, contacto del pipeline-context.

**Entregables**:
- `pom.xml`, `mvnw`, `mvnw.cmd`
- `src/main/java/...` estructura MVC v1
- `src/main/resources/application.yml`
- `docker-compose.yml`
- `GlobalExceptionHandler` + clases error
- `.gitignore` actualizado
- Dockerfile (si template lo exige)

**Criterios de aceptacion**:
- [ ] `./mvnw -DskipTests compile` exitoso
- [ ] Actuator health accesible en diseño (endpoint configurado)
- [ ] Mongo levantable con docker-compose
- [ ] Error contract base implementado
- [ ] API bajo `/api/v1/...`
- [ ] JaCoCo configurado sin afectar runtime (`propertyName=jacocoArgLine`)

**Reglas aplicables**: 01-output-format, 02-change-policy, 03-stack, 04-architecture-mvc, 05-gitignore-protection, 06-error-contract, 07-docker-support, 08-actuator-health, 09-api-versioning, 12-maven-dependency-hygiene

**Definition of Done**: Proyecto compila; infraestructura lista; capas vacías o stub; handoff al feature-executor para implementar dominio H1.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

scaffold completado — Spring Boot 3.3.5, Mongo, error contract, JaCoCo, docker-compose, compile OK
