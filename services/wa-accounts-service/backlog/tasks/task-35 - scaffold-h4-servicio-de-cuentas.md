---
id: TASK-35
title: "[scaffold] H4 — Servicio de Cuentas"
status: Done
priority: medium
created: "2026-06-23T21:36:03.255Z"
parent: TASK-33
---


# [scaffold] H4 — Servicio de Cuentas

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
### PASO: scaffold

**Objetivo**: Generar el esqueleto completo de `wa-accounts-service` — proyecto Maven Spring Boot compilable con MongoDB, Actuator, Docker, `.gitignore` y estructura MVC — preparado para implementar F1 piloto.

**Contexto**: Greenfield. El planner define paquetes y endpoints; scaffold materializa infraestructura sin lógica de negocio completa.

**Descripcion detallada**:
1. Crear `pom.xml` con parent Spring Boot 3.3.x, Java 21, dependencias: web, data-mongodb, validation, actuator, lombok, mapstruct, springdoc-openapi, test + testcontainers-mongodb.
2. Configurar JaCoCo en POM (`jacocoArgLine`, prepare-agent, check en verify) — umbral según plan.
3. Estructura de paquetes `com.wa.banking.accounts`:
   - `AccountsApplication.java`
   - `api/` (controllers vacíos o stub)
   - `api/dto/`, `api/error/` (`GlobalExceptionHandler`, `ErrorResponse`)
   - `entity/`, `repository/`, `service/`, `mapper/`
   - `config/` (SecurityFilterConfig, WebClient/RestClient para H2)
4. `application.yml` + `application-local.yml`: Mongo URI, puerto servicio (ej. 8083), `integration.session.base-url`, perfiles.
5. `docker-compose.yml` con mongo:7 en puerto 27017.
6. `.gitignore` según rule 05 (incluir exclusiones `.cursor/`, `delivery/`, etc.).
7. Dockerfile nativo desde template `.cursor/templates/Dockerfile.native.md` si aplica.
8. Actuator health en `/actuator/health`.
9. Maven wrapper (`./mvnw`) funcional.
10. Seed mínimo opcional: datos demo en `data-local/` o script de init para cuentas de prueba (bankUserId de test).

**Entregables**:
- Proyecto compilable `./mvnw -DskipTests package`
- Estructura MVC completa
- Docker Compose + Dockerfile
- GlobalExceptionHandler con error contract
- Config de integración H2 stubeable

**Criterios de aceptacion**:
- [ ] `./mvnw -DskipTests compile` pasa sin errores.
- [ ] `/actuator/health` responde UP con Mongo levantado.
- [ ] `.gitignore` incluye todas las exclusiones obligatorias.
- [ ] MapStruct + Lombok configurados en annotationProcessorPaths.
- [ ] OpenAPI/Springdoc presente (endpoint `/v3/api-docs` accesible tras build).

**Reglas aplicables**: 01-output-format, 02-change-policy, 03-stack, 04-architecture-mvc, 05-gitignore-protection, 06-error-contract, 07-docker-support, 08-actuator-health, 09-api-versioning, 12-maven-dependency-hygiene

**Definition of Done**: Scaffold compila, levanta con Mongo local, expone health y estructura lista para feature-executor.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

scaffold completado — Spring Boot 3.3.5, puerto 8083, Mongo, H2 stub, JaCoCo 70%, compile OK

scaffold completado — Spring Boot compilable
