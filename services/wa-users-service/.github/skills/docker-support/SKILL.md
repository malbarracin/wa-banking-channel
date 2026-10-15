---
name: docker-support
description: >
  Levantar el microservicio con Docker: Dockerfile multi-stage (JDK alpine → JRE alpine, usuario sin privilegios,
  healthcheck), docker-compose.yml con la app y su base de datos (según service.database) con healthchecks y
  depends_on, .dockerignore, perfil Spring docker y variables en .env (sin secretos commiteados). Usa las
  plantillas Dockerfile y docker-compose.yml.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: spring-boot-3, docker
  applies-to: "Dockerfile, docker-compose*.yml, .dockerignore, application-docker.yml"
---

# Soporte Docker

## ¿Aplica?

Siempre que el servicio se arma con este pack, **salvo** que `project.yaml` diga `docker.enabled: false`
(en ese caso no hay Dockerfile ni compose y el tablero no lleva esa tarea de DevOps). Lo arma **DevOps**: no es
parte del scaffold del dev (`backend-java-scaffold`), que solo deja el servicio compilando.

El detalle de la JVM en contenedores (flags, imagen base, distroless) está en la skill `java-docker`; CI y Helm
en la regla `devops.md`. Esta skill es el **checklist de lo que tiene que quedar** para correrlo local.

## Archivos (en la carpeta del servicio, junto al `pom.xml`)

```
Dockerfile                                # plantilla Dockerfile (o Dockerfile.native si optimization: native)
.dockerignore
docker-compose.yml                        # plantilla docker-compose.yml: app + base de datos
.env.example                              # variables documentadas, sin valores reales (se commitea)
src/main/resources/application-docker.yml # perfil "docker": la app corriendo DENTRO del compose
```

`.env` (con los valores reales) **no** se commitea: va en `.gitignore` y en `.dockerignore`.

## Checklist obligatorio

- [ ] **Dockerfile** multi-stage desde la plantilla `Dockerfile`: `eclipse-temurin:<java.version>-jdk-alpine`
      (build con `./mvnw`) → `eclipse-temurin:<java.version>-jre-alpine` (runtime). Con
      `service.optimization: native`, la plantilla `Dockerfile.native`.
- [ ] Imagen final con **usuario no-root** (`USER app`).
- [ ] **`HEALTHCHECK`** contra `/actuator/health` (actuator con `health` expuesto en `application.yml`).
- [ ] **docker-compose.yml** desde la plantilla: servicio `app` + servicio de base de datos según
      `service.database`, los dos con `healthcheck`, y `app` con
      `depends_on: { <db>: { condition: service_healthy } }`.
- [ ] **`.dockerignore`** (abajo).
- [ ] **Perfil `docker`** (`application-docker.yml`): conexión a la base por el **nombre del servicio** del
      compose (`postgres`, `mysql`, `mongo`), puerto del servicio y actuator expuesto. El compose lo activa con
      `SPRING_PROFILES_ACTIVE: docker`.
- [ ] Variables en **`.env`** (y documentadas en `.env.example`): `APP_NAME`, `APP_PORT`, `DB_NAME`,
      `DB_USERNAME`, `DB_PASSWORD`. El compose las lee con `${VAR}`.

## Base de datos según `service.database`

| `service.database` | Servicio en el compose | Healthcheck | Conexión en `application-docker.yml` |
|--------------------|------------------------|-------------|--------------------------------------|
| `postgresql` (defecto) | `postgres` (`postgres:16-alpine`) | `pg_isready -U $${POSTGRES_USER}` | `jdbc:postgresql://postgres:5432/${DB_NAME}` |
| `mysql` | `mysql` (`mysql:8`) | `mysqladmin ping -h localhost` | `jdbc:mysql://mysql:3306/${DB_NAME}` |
| `mongodb` | `mongo` (`mongo:7`) | `mongosh --quiet --eval "db.adminCommand('ping')"` | `spring.data.mongodb.uri: mongodb://${DB_USERNAME}:${DB_PASSWORD}@mongo:27017/${DB_NAME}?authSource=admin` |
| `h2` | ninguno (solo `app`) | — | la de `application.yml` |

## Perfiles: `dev` vs `docker`

- **`dev`**: la app corre en tu máquina (`./mvnw spring-boot:run`) contra la base del compose en
  `localhost` (`docker compose up -d postgres`).
- **`docker`**: la app corre **dentro** del compose; la base se llama por el nombre del servicio, no `localhost`.

`application-docker.yml` (PostgreSQL):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

server:
  port: ${APP_PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

## `.dockerignore`

```
target/
.git/
.env
*.md
delivery/
backlog/
specs/
.opensoma/
.claude/
.codex/
.cursor/
.kiro/
.opencode/
.agents/
.idea/
.vscode/
*.iml
```

No excluyas `.mvn/` ni `mvnw`: el Dockerfile compila con el wrapper.

## `.env.example`

```
APP_NAME=<artifactId>
APP_PORT=8080
DB_NAME=<artifactId sin guiones>
DB_USERNAME=app
DB_PASSWORD=cambiar
```

## Comandos mínimos (van en `delivery/devops-local.md`)

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
docker compose logs -f app
curl http://localhost:8080/actuator/health
docker compose down
```

**Verificá** que `docker compose up -d --build` levanta y que `docker compose ps` muestra los servicios
`healthy`. Si en la máquina no hay Docker o no hay acceso al daemon, validá la sintaxis con `docker compose config`
si está disponible, anotalo en "Pendiente de verificar" de `delivery/devops-local.md` y seguí: **no es un bloqueo**
(no lo des por probado, pero terminá con `ok`).

## Prohibido

- Imagen final con el JDK completo (runtime siempre JRE, o nativa).
- Secretos escritos en el compose, en `application-docker.yml` o en `.env.example`: siempre `${VAR}` y `.env`
  fuera de Git.
- App o base sin `healthcheck`, o `depends_on` sin `condition: service_healthy`.
- `localhost` como host de la base en el perfil `docker`.
- Correr como root en la imagen final.
