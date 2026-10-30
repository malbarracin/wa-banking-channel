# DevOps local — wa-users-service, wa-channel-service, wa-credentials-service, wa-accounts-service, wa-insurance-service y wa-cards-service

Runbook copy/paste para los servicios en `services/`.

## Prerrequisitos

- Java 21 (Temurin)
- Docker Desktop (o daemon Docker) + Compose v2
- Helm 3 (opcional, para lint/template del chart)
- Maven Wrapper de cada servicio (`./mvnw`)

## Puertos host (pueden correr a la vez)

| Servicio | App (host) | Postgres (host) | Base |
|----------|------------|-----------------|------|
| wa-users-service | 8080 | 5432 | `wa_users` |
| wa-channel-service | 8081 | 5433 | `wa_channel` |
| wa-credentials-service | 8082 | 5434 | `wa_credentials` |
| wa-accounts-service | 8084 | 5435 | `wa_accounts` |
| wa-insurance-service | 8085 | 5436 | `wa_insurance` |
| wa-cards-service | 8086 | 5437 | `wa_cards` |

## wa-users-service

### Variables de entorno

```powershell
cd services/wa-users-service
copy .env.example .env
# Editar .env con valores locales (no commitear .env)
```

### Docker Compose (app + Postgres)

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

### Build y tests (JaCoCo ≥ 80%)

```powershell
cd services/wa-users-service
./mvnw -B clean verify
```

### Helm (sin cluster)

```powershell
helm lint services/wa-users-service/helm/wa-users-service --set-string env.DB_PASSWORD=lint-only-placeholder
helm template wa-users services/wa-users-service/helm/wa-users-service -f services/wa-users-service/helm/wa-users-service/values-dev.yaml --set-string env.DB_PASSWORD=render-only-placeholder
```

Con cluster local (kind / minikube / Docker Desktop):

```powershell
helm upgrade --install wa-users services/wa-users-service/helm/wa-users-service `
  -f services/wa-users-service/helm/wa-users-service/values-dev.yaml `
  --set-string "env.DB_PASSWORD=$env:DB_PASSWORD" `
  -n dev --create-namespace
```

## wa-channel-service

### Variables de entorno

```powershell
cd services/wa-channel-service
copy .env.example .env
# Editar .env con valores locales (no commitear .env)
```

Defaults del `.env.example`: app host `8081`, Postgres host `5433`, base `wa_channel` (no chocan con wa-users-service).

### Docker Compose (app + Postgres)

```powershell
cd services/wa-channel-service
docker compose up -d --build
docker compose ps
curl http://localhost:8081/actuator/health
docker compose logs -f app
docker compose down
```

Perfil Spring activo en compose: `docker` (`application-docker.yml` → host JDBC `postgres`).

El volume de Postgres monta `src/main/resources/db/postgresql/V001__vinculo.sql` en `docker-entrypoint-initdb.d` (solo en el primer arranque del volume; `ddl-auto=none`).

Si regenerás el esquema desde cero: `docker compose down -v` y volvé a levantar.

Para correr la app en la máquina contra Postgres del compose:

```powershell
docker compose up -d postgres
# DB_URL p.ej. jdbc:postgresql://localhost:5433/wa_channel (DB_HOST_PORT por defecto 5433)
$env:SPRING_PROFILES_ACTIVE="dev"
./mvnw spring-boot:run
```

Nota: el perfil `dev` de channel usa H2 en memoria; para Postgres local preferí perfil default/`docker` con `DB_URL` apuntando a `localhost:5433`.

### Build y tests (JaCoCo ≥ 80%)

```powershell
cd services/wa-channel-service
./mvnw -B clean verify
```

### Helm (sin cluster)

```powershell
helm lint services/wa-channel-service/helm/wa-channel-service --set-string env.DB_PASSWORD=lint-only-placeholder
helm template wa-channel services/wa-channel-service/helm/wa-channel-service -f services/wa-channel-service/helm/wa-channel-service/values-dev.yaml --set-string env.DB_PASSWORD=render-only-placeholder
```

Con cluster local:

```powershell
helm upgrade --install wa-channel services/wa-channel-service/helm/wa-channel-service `
  -f services/wa-channel-service/helm/wa-channel-service/values-dev.yaml `
  --set-string "env.DB_PASSWORD=$env:DB_PASSWORD" `
  -n dev --create-namespace
```

## wa-credentials-service

### Variables de entorno

```powershell
cd services/wa-credentials-service
copy .env.example .env
# Editar .env: DB_PASSWORD y CREDENTIAL_JWT_SECRET (≥32 bytes UTF-8). No commitear .env
```

Defaults del `.env.example`: app host `8082`, Postgres host `5434`, base `wa_credentials` (no chocan con H1/H3).

### Docker Compose (app + Postgres)

```powershell
cd services/wa-credentials-service
docker compose up -d --build
docker compose ps
curl http://localhost:8082/actuator/health
docker compose logs -f app
docker compose down
```

Perfil Spring activo en compose: `docker` (`application-docker.yml` → host JDBC `postgres`). Flyway aplica `db/migration` al arrancar (`ddl-auto=none`).

Para correr la app en la máquina contra Postgres del compose:

```powershell
docker compose up -d postgres
# DB_URL p.ej. jdbc:postgresql://localhost:5434/wa_credentials (DB_HOST_PORT por defecto 5434)
$env:DB_URL="jdbc:postgresql://localhost:5434/wa_credentials"
$env:DB_USERNAME="app"
$env:DB_PASSWORD="cambiar"
$env:CREDENTIAL_JWT_SECRET="cambiar-por-clave-de-al-menos-32-bytes"
$env:WA_USERS_SERVICE_BASE_URL="http://localhost:8080"
./mvnw spring-boot:run
```

### Build y tests (JaCoCo ≥ 80%)

```powershell
cd services/wa-credentials-service
./mvnw -B clean verify
```

En sandbox con `.m2` restringido:

```powershell
./mvnw.cmd -q -B "-Dmaven.repo.local=../../.maven-cache" clean verify
```

### Helm (sin cluster)

```powershell
helm lint services/wa-credentials-service/helm/wa-credentials-service `
  --set-string env.DB_PASSWORD=lint-only-placeholder `
  --set-string env.CREDENTIAL_JWT_SECRET=lint-only-jwt-secret-at-least-32b
helm template wa-credentials services/wa-credentials-service/helm/wa-credentials-service `
  -f services/wa-credentials-service/helm/wa-credentials-service/values-dev.yaml `
  --set-string env.DB_PASSWORD=render-only-placeholder `
  --set-string env.CREDENTIAL_JWT_SECRET=render-only-jwt-secret-at-least-32b
```

Con cluster local:

```powershell
helm upgrade --install wa-credentials services/wa-credentials-service/helm/wa-credentials-service `
  -f services/wa-credentials-service/helm/wa-credentials-service/values-dev.yaml `
  --set-string "env.DB_PASSWORD=$env:DB_PASSWORD" `
  --set-string "env.CREDENTIAL_JWT_SECRET=$env:CREDENTIAL_JWT_SECRET" `
  -n dev --create-namespace
```

## wa-accounts-service

### Variables de entorno

```powershell
cd services/wa-accounts-service
copy .env.example .env
# Editar .env con valores locales (no commitear .env)
```

Defaults del `.env.example`: app host `8084`, Postgres host `5435`, base `wa_accounts` (no chocan con H1/H2/H3).

### Docker Compose (app + Postgres)

```powershell
cd services/wa-accounts-service
docker compose up -d --build
docker compose ps
curl http://localhost:8084/actuator/health
docker compose logs -f app
docker compose down
```

Perfil Spring activo en compose: `docker` (`application-docker.yml` → host JDBC `postgres`, `ddl-auto=update` para levantar sin migraciones Flyway).

Para correr la app en la máquina contra Postgres del compose:

```powershell
docker compose up -d postgres
# DB_URL p.ej. jdbc:postgresql://localhost:5435/wa_accounts (DB_HOST_PORT por defecto 5435)
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:postgresql://localhost:5435/wa_accounts"
$env:DB_USERNAME="app"
$env:DB_PASSWORD="cambiar"
$env:CREDENTIALS_SERVICE_BASE_URL="http://localhost:8082"
./mvnw spring-boot:run
```

Sobre esquemas existentes de H4, aplicar `delivery/sql/002-transferencia-preparacion.sql` antes de desplegar con `ddl-auto=validate` (perfil prod).

### Build y tests (JaCoCo ≥ 80%)

```powershell
cd services/wa-accounts-service
./mvnw -B clean verify
```

En PowerShell con sandbox `.m2` restringido:

```powershell
./mvnw.cmd -q -B "-Dmaven.repo.local=../../.maven-cache" clean verify
```

### Helm (sin cluster)

```powershell
helm lint services/wa-accounts-service/helm/wa-accounts-service --set-string env.DB_PASSWORD=lint-only-placeholder
helm template wa-accounts services/wa-accounts-service/helm/wa-accounts-service `
  -f services/wa-accounts-service/helm/wa-accounts-service/values-dev.yaml `
  --set-string env.DB_PASSWORD=render-only-placeholder
```

Con cluster local:

```powershell
helm upgrade --install wa-accounts services/wa-accounts-service/helm/wa-accounts-service `
  -f services/wa-accounts-service/helm/wa-accounts-service/values-dev.yaml `
  --set-string "env.DB_PASSWORD=$env:DB_PASSWORD" `
  -n dev --create-namespace
```

## wa-insurance-service

### Variables de entorno

```powershell
cd services/wa-insurance-service
copy .env.example .env
# Editar .env con valores locales (no commitear .env)
```

Defaults del `.env.example`: app host `8085`, Postgres host `5436`, base `wa_insurance` (no chocan con H1/H2/H3/H4).

### Docker Compose (app + Postgres)

```powershell
cd services/wa-insurance-service
docker compose up -d --build
docker compose ps
curl http://localhost:8085/actuator/health
docker compose logs -f app
docker compose down
```

Perfil Spring activo en compose: `docker` (`application-docker.yml` → host JDBC `postgres`, `ddl-auto=update` para levantar sin migraciones Flyway).

Para correr la app en la máquina contra Postgres del compose:

```powershell
docker compose up -d postgres
# DB_URL p.ej. jdbc:postgresql://localhost:5436/wa_insurance (DB_HOST_PORT por defecto 5436)
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:postgresql://localhost:5436/wa_insurance"
$env:DB_USERNAME="app"
$env:DB_PASSWORD="cambiar"
$env:CREDENTIALS_SERVICE_BASE_URL="http://localhost:8082"
./mvnw spring-boot:run
```

### Build y tests (JaCoCo ≥ 80%)

```powershell
cd services/wa-insurance-service
./mvnw -B clean verify
```

En PowerShell con sandbox `.m2` restringido:

```powershell
./mvnw.cmd -q -B "-Dmaven.repo.local=../../.maven-cache" clean verify
```

### Helm (sin cluster)

```powershell
helm lint services/wa-insurance-service/helm/wa-insurance-service --set-string env.DB_PASSWORD=lint-only-placeholder
helm template wa-insurance services/wa-insurance-service/helm/wa-insurance-service `
  -f services/wa-insurance-service/helm/wa-insurance-service/values-dev.yaml `
  --set-string env.DB_PASSWORD=render-only-placeholder
```

Con cluster local:

```powershell
helm upgrade --install wa-insurance services/wa-insurance-service/helm/wa-insurance-service `
  -f services/wa-insurance-service/helm/wa-insurance-service/values-dev.yaml `
  --set-string "env.DB_PASSWORD=$env:DB_PASSWORD" `
  -n dev --create-namespace
```

## wa-cards-service

### Variables de entorno

```powershell
cd services/wa-cards-service
copy .env.example .env
# Editar .env con valores locales (no commitear .env)
```

Defaults del `.env.example`: app host `8086`, Postgres host `5437`, base `wa_cards` (no chocan con H1/H2/H3/H4/H6). `DB_USERNAME`/`DB_PASSWORD` son obligatorios en el YAML base (sin default); en compose y perfil `dev` van por `.env` / env.

### Docker Compose (app + Postgres)

```powershell
cd services/wa-cards-service
docker compose up -d --build
docker compose ps
curl http://localhost:8086/actuator/health
docker compose logs -f app
docker compose down
```

Perfil Spring activo en compose: `docker` (`application-docker.yml` → host JDBC `postgres`, `ddl-auto=update` para levantar sin migraciones Flyway).

Para correr la app en la máquina contra Postgres del compose:

```powershell
docker compose up -d postgres
# DB_URL p.ej. jdbc:postgresql://localhost:5437/wa_cards (DB_HOST_PORT por defecto 5437)
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:postgresql://localhost:5437/wa_cards"
$env:DB_USERNAME="app"
$env:DB_PASSWORD="cambiar"
$env:CREDENTIALS_SERVICE_BASE_URL="http://localhost:8082"
./mvnw spring-boot:run
```

### Build y tests (JaCoCo ≥ 80%)

```powershell
cd services/wa-cards-service
./mvnw -B clean verify
```

En PowerShell con sandbox `.m2` restringido:

```powershell
./mvnw.cmd -q -B "-Dmaven.repo.local=../../.maven-cache" clean verify
```

### Helm (sin cluster)

```powershell
helm lint services/wa-cards-service/helm/wa-cards-service --set-string env.DB_PASSWORD=lint-only-placeholder
helm template wa-cards services/wa-cards-service/helm/wa-cards-service `
  -f services/wa-cards-service/helm/wa-cards-service/values-dev.yaml `
  --set-string env.DB_PASSWORD=render-only-placeholder
```

Con cluster local:

```powershell
helm upgrade --install wa-cards services/wa-cards-service/helm/wa-cards-service `
  -f services/wa-cards-service/helm/wa-cards-service/values-dev.yaml `
  --set-string "env.DB_PASSWORD=$env:DB_PASSWORD" `
  -n dev --create-namespace
```

## CI / CD

Los charts exigen `env.DB_PASSWORD` explícito; wa-credentials-service también exige `env.CREDENTIAL_JWT_SECRET`. No tienen contraseña/JWT por defecto.
Los valores `lint-only-placeholder` / `render-only-*` son solo para validación sin despliegue.

- CI: `.github/workflows/ci.yml` — matriz `wa-users-service` + `wa-channel-service` + `wa-credentials-service` + `wa-accounts-service` + `wa-insurance-service` + `wa-cards-service` con `./mvnw -B clean verify`; build de imagen en `main`; `helm lint` de los seis charts.
- CD: `.github/workflows/cd.yml` — template con `workflow_dispatch` (elige servicio + entorno; deploy comentado hasta tener cluster/credenciales).

## Decisiones

- Registry por defecto: `ghcr.io/example/<servicio>` (configurable en cada `values.yaml`).
- Ingress deshabilitado por defecto; hosts de ejemplo `wa-users.example.local` / `wa-channel.example.local` / `wa-credentials.example.local` / `wa-accounts.example.local` / `wa-insurance.example.local` / `wa-cards.example.local`.
- Credenciales DB y JWT solo por `.env` / K8s Secret / `--set`; nunca en YAML commiteado.
- Puertos host: H1 `8080`/`5432`, H3 `8081`/`5433`, H2 `8082`/`5434`, H4 `8084`/`5435`, H6 `8085`/`5436`, H5 `8086`/`5437` para convivir en local.
- H2 en Docker llama a H1 vía `WA_USERS_SERVICE_BASE_URL` (default `host.docker.internal:8080`).
- H4, H5 y H6 en Docker llaman a H2 vía `CREDENTIALS_SERVICE_BASE_URL` (default `host.docker.internal:8082`).
- Perfil `docker` de H4/H5/H6 usa `ddl-auto=update` (sin Flyway); prod mantiene `validate`.
- Postgres host de H5 es `5437` (no `5436`) para no chocar con H6 ni con `ORCHESTRATOR_DB_HOST_PORT` del orquestador.

## Pendiente de verificar

| Qué | Comando | Estado |
|-----|---------|--------|
| Compose users healthy + health | `cd services/wa-users-service && docker compose up -d --build && curl http://localhost:8080/actuator/health` | Verificado (entrega H1) |
| Compose channel healthy + health | `cd services/wa-channel-service && docker compose up -d --build && curl http://localhost:8081/actuator/health` | Verificado (app + postgres healthy, HTTP 200) |
| Compose credentials healthy + health | `cd services/wa-credentials-service && copy .env.example .env && docker compose up -d --build && curl http://localhost:8082/actuator/health` | Verificado (app + postgres healthy, HTTP 200 `{"status":"UP",...}`) |
| `./mvnw clean verify` credentials | `cd services/wa-credentials-service && ./mvnw -B clean verify` | Verificado (JaCoCo OK, BUILD SUCCESS) |
| `docker compose config` credentials | `cd services/wa-credentials-service && docker compose --env-file .env.example config` | Verificado |
| Helm lint / template credentials | `helm lint services/wa-credentials-service/helm/wa-credentials-service --set-string env.DB_PASSWORD=lint-only-placeholder --set-string env.CREDENTIAL_JWT_SECRET=lint-only-jwt-secret-at-least-32b` | Pendiente: Helm no instalado en el entorno de entrega (no se instala ni descarga) |
| Compose accounts healthy + health | `cd services/wa-accounts-service && copy .env.example .env && docker compose up -d --build && curl http://localhost:8084/actuator/health` | Verificado (app + postgres healthy, HTTP 200) |
| `docker compose config` accounts | `cd services/wa-accounts-service && docker compose --env-file .env.example config` | Verificado |
| `./mvnw clean verify` accounts | `cd services/wa-accounts-service && ./mvnw -B clean verify` | Verificado (JaCoCo OK, BUILD SUCCESS) |
| Helm lint / template accounts | `helm lint services/wa-accounts-service/helm/wa-accounts-service --set-string env.DB_PASSWORD=lint-only-placeholder` | Pendiente: Helm no instalado en el entorno de entrega (no se instala ni descarga) |
| Compose insurance healthy + health | `cd services/wa-insurance-service && copy .env.example .env && docker compose up -d --build && curl http://localhost:8085/actuator/health` | Verificado (app + postgres healthy) |
| `docker compose config` insurance | `cd services/wa-insurance-service && docker compose --env-file .env.example config` | Verificado |
| `./mvnw clean verify` insurance | `cd services/wa-insurance-service && ./mvnw -B clean verify` | Verificado (JaCoCo OK, BUILD SUCCESS) |
| Helm lint / template insurance | `helm lint services/wa-insurance-service/helm/wa-insurance-service --set-string env.DB_PASSWORD=lint-only-placeholder` | Pendiente: Helm no instalado en el entorno de entrega (no se instala ni descarga) |
| Compose cards healthy + health | `cd services/wa-cards-service && copy .env.example .env && docker compose up -d --build && curl http://localhost:8086/actuator/health` | Pendiente: Docker Desktop/daemon no disponible en el entorno de entrega |
| `docker compose config` cards | `cd services/wa-cards-service && docker compose --env-file .env.example config` | Verificado (compose config OK, puertos 8086/5437) |
| `./mvnw clean verify` cards | `cd services/wa-cards-service && ./mvnw -B clean verify` | Verificado (JaCoCo OK, BUILD SUCCESS) |
| Helm lint / template cards | `helm lint services/wa-cards-service/helm/wa-cards-service --set-string env.DB_PASSWORD=lint-only-placeholder` | Pendiente: Helm no instalado en el entorno de entrega (no se instala ni descarga) |
