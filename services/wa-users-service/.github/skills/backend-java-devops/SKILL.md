---
name: backend-java-devops
description: >
  DevOps para Spring Boot: GitHub Actions CI, Postgres en Docker Compose (dev),
  perfiles dev/test/prod, Dockerfile, Helm/Kubernetes. Usar con @devops-engineer.
license: MIT
compatibility: opencode
metadata:
  audience: devops
  stack: spring-boot
  ci: github-actions
  local-db: postgres-docker
  deploy: kubernetes-helm
---

# Backend Java — DevOps

Usar con **`@devops-engineer`** en TASK tipo `chore` / milestone infra.

**No** implementar features Java. **No** tocar `src/main/java/**` salvo config estrictamente necesaria acordada en la TASK.

## Context pack (OBLIGATORIO)

**Primera tool call** = esta skill (`backend-java-devops`). Sin eso, STOP.

| # | Qué | Cómo |
|---|-----|------|
| 1 | Skill | `backend-java-devops` |
| 2 | Alcance | La tarea del paso si hay; en el cierre, la especificación y lo integrado |
| 3 | Rules | `.opensoma/library/rules/devops.md`, `stack.md`, `quality-gates.md` |
| 4 | Identidad | `project.yaml` (`project.name`), `pom.xml` `<artifactId>` |
| 5 | App | `application*.yml`, puerto/actuator |
| 6 | Repo | qué existe ya (Dockerfile, workflows, helm) |

Si falta una decisión (registry, namespace K8s, Flyway): elegí lo más simple que cumpla `devops.md`, dejala
anotada en `delivery/devops-local.md` y seguí. `BLOQUEADO:` solo si de verdad no se puede avanzar.

Resolver chart: `helm/<artifactId>/` donde `<artifactId>` viene del POM o de `project.name`.

## Entregables canónicos

### 1. Perfiles Spring

- `application-dev.yml` — Postgres del compose en `localhost` (app corriendo en tu máquina), credenciales por env
- `application-docker.yml` — app dentro del compose (host `postgres`); ver skill `docker-support`
- `application-test.yml` — H2 o Testcontainers-friendly para CI
- `application-prod.yml` — Postgres vía env; sin H2 console

Quitar H2 como default de dev si la TASK pide Postgres en Docker.

### 2. Docker local

Seguí la skill **`docker-support`** (checklist, base según `service.database`, `.dockerignore`, `.env`) y las
plantillas `Dockerfile` y `docker-compose.yml`. Si `project.yaml` tiene `docker.enabled: false`, salteá esta parte.

```
Dockerfile
.dockerignore
docker-compose.yml    # postgres + app (perfil docker)
.env.example
```

Verificar:

```powershell
docker compose up --build -d
curl http://localhost:8080/actuator/health
```

### 3. GitHub Actions

`.github/workflows/ci.yml`:

- Java 21 (`actions/setup-java`, distribution `temurin`; la misma versión del `pom.xml`), cache Maven
- `./mvnw clean verify` (JaCoCo 80%)
- En `main`: job docker build (push opcional a GHCR)

`.github/workflows/cd.yml` (template):

- `workflow_dispatch` + deploy Helm (comentado o gated hasta tener cluster)

### 4. Helm

```
helm/<artifactId>/
  Chart.yaml
  values.yaml
  values-dev.yaml
  values-prod.yaml
  templates/deployment.yaml
  templates/service.yaml
  templates/configmap.yaml
  templates/secret.yaml   # placeholders
  templates/_helpers.tpl
```

Verificar (sustituir `<artifactId>` y `<release>`):

```powershell
helm lint helm/<artifactId>
helm template <release> helm/<artifactId> -f helm/<artifactId>/values-dev.yaml
```

### 5. Runbook

`delivery/devops-local.md` — copy/paste:

- Prerrequisitos (Docker, Helm, Java)
- `docker compose up`
- `./mvnw clean verify`
- `helm template` / install en kind/minikube

## Checklist antes de Done

- [ ] Skill cargada
- [ ] Compose + Postgres dev OK (o en "Pendiente de verificar" si el entorno no tiene Docker)
- [ ] `./mvnw clean verify` OK
- [ ] Workflows YAML válidos (sintaxis)
- [ ] `helm lint` OK (o en "Pendiente de verificar" si no hay Helm: no lo instales ni lo descargues)
- [ ] Sin secrets commiteados
- [ ] `delivery/devops-local.md` actualizado
- [ ] Criterios de aceptación cumplidos (si el paso tiene tarea)

## Dependencia Maven (Postgres dev)

Si el POM solo tiene H2 runtime, agregar el driver Postgres (`org.postgresql:postgresql`, scope runtime, sin versión):
es el único cambio de `pom.xml` que hace DevOps (`maven-dependency-hygiene`).

## Prohibiciones

- No bajar JaCoCo 80%
- No commitear `.env` con passwords
- No deploy prod real sin credenciales/cluster acordados
- No refactorizar código de negocio
