# DevOps local — wa-users-service

Runbook copy/paste para el servicio en `services/wa-users-service`.

## Prerrequisitos

- Java 21 (Temurin)
- Docker Desktop (o daemon Docker) + Compose v2
- Helm 3 (opcional, para lint/template del chart)
- Maven Wrapper del servicio (`./mvnw`)

## Variables de entorno

```powershell
cd services/wa-users-service
copy .env.example .env
# Editar .env con valores locales (no commitear .env)
```

## Docker Compose (app + Postgres)

```powershell
cd services/wa-users-service
docker compose up -d --build
docker compose ps
curl http://localhost:8080/actuator/health
docker compose logs -f app
docker compose down
```

Perfil Spring activo en compose: `docker` (`application-docker.yml` → host JDBC `postgres`).

Si el puerto host `5432` ya está ocupado, en `.env` poné `DB_HOST_PORT=5433` (u otro libre). Dentro de la red Compose el servicio sigue en `postgres:5432`.

Para correr la app en la máquina contra Postgres del compose:

```powershell
docker compose up -d postgres
# Ajustar DB_URL si DB_HOST_PORT ≠ 5432, p.ej. jdbc:postgresql://localhost:5433/wa_users
$env:SPRING_PROFILES_ACTIVE="dev"
./mvnw spring-boot:run
```

## Build y tests (JaCoCo ≥ 80%)

```powershell
cd services/wa-users-service
./mvnw -B clean verify
```

## Helm (sin cluster)

```powershell
helm lint services/wa-users-service/helm/wa-users-service
helm template wa-users services/wa-users-service/helm/wa-users-service -f services/wa-users-service/helm/wa-users-service/values-dev.yaml
```

Con cluster local (kind / minikube / Docker Desktop):

```powershell
helm upgrade --install wa-users services/wa-users-service/helm/wa-users-service `
  -f services/wa-users-service/helm/wa-users-service/values-dev.yaml `
  -n dev --create-namespace
```

## CI / CD

- CI: `.github/workflows/ci.yml` — `./mvnw -B clean verify` en push/PR; build de imagen en `main`; `helm lint`.
- CD: `.github/workflows/cd.yml` — template con `workflow_dispatch` (deploy comentado hasta tener cluster/credenciales).

## Decisiones

- Registry de imagen por defecto: `ghcr.io/example/wa-users-service` (configurable en `helm/.../values.yaml`).
- Ingress deshabilitado por defecto; host de ejemplo `wa-users.example.local`.
- Credenciales DB solo por `.env` / K8s Secret / `--set`; nunca en YAML commiteado.

## Pendiente de verificar

| Qué | Comando | Estado |
|-----|---------|--------|
| Compose healthy + health | `cd services/wa-users-service && docker compose up -d --build && docker compose ps` / `curl http://localhost:8080/actuator/health` | Verificado (app + postgres `healthy`, `{"status":"UP"}`) |
| `./mvnw clean verify` | `cd services/wa-users-service && ./mvnw -B clean verify` | Verificado (123 tests, JaCoCo OK) |
| Helm lint / template | `helm lint services/wa-users-service/helm/wa-users-service` y `helm template wa-users ... -f values-dev.yaml` | Pendiente: Helm no instalado en el entorno de entrega |
