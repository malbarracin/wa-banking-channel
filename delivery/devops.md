# DevOps — plataforma de integración (H0.1 / H0 / H0.2) y stack E2E local

Cierre DevOps del runtime local Compose (run `20261031-f8ae`, TASK-189).
Describe Dockerfiles, Kafka KRaft y topics, PostgreSQL del orquestador y CI.
Operación del smoke E2E: [docs/local-devops.md](../docs/local-devops.md).
Servicios H1–H6 por separado: [delivery/devops-local.md](devops-local.md).

## Prerrequisitos

- Docker Engine + Compose v2
- Java 21 (Temurin) y Maven Wrapper de cada componente (`./mvnw`)
- Python 3 (scripts de seed/smoke y `scripts/verify_integracion.py`)
- Archivo `.env` en la raíz (copiar desde `.env.example`; no commitear)

## Dockerfiles (integración)

Multi-stage, build desde la **raíz** del repo (copian `shared/contracts/events`), usuario no root, `JAVA_OPTS` de contenedor y `HEALTHCHECK` a `/actuator/health`.

| Componente | Dockerfile | Puerto | Build |
| --- | --- | --- | --- |
| H0.1 event-platform | `platform/wa-event-platform/Dockerfile` | 8092 | `docker build -f platform/wa-event-platform/Dockerfile -t wa-event-platform:local .` |
| H0 gateway | `gateway/wa-twilio-gateway/Dockerfile` | 8090 | `docker build -f gateway/wa-twilio-gateway/Dockerfile -t wa-twilio-gateway:local .` |
| H0.2 orchestrator | `platform/wa-channel-orchestrator/Dockerfile` | 8091 | `docker build -f platform/wa-channel-orchestrator/Dockerfile -t wa-channel-orchestrator:local .` |

Patrón común:

1. Stage `builder`: `eclipse-temurin:21-jdk-alpine` + `./mvnw package -DskipTests` + extracción de capas Spring Boot.
2. Stage runtime: `eclipse-temurin:21-jre-alpine`, `adduser`/`addgroup` `app`, `USER app`.
3. `HEALTHCHECK` con `wget` al actuator del puerto del servicio.
4. `ENTRYPOINT` con `JarLauncher` y `$JAVA_OPTS` (`UseContainerSupport`, `MaxRAMPercentage=75.0`, G1, exit on OOM).

El compose raíz construye estas imágenes con `build.context: .` y el `dockerfile` de cada ruta.

## Kafka KRaft y topics

Broker único en el compose raíz (`docker-compose.yml`):

| Ítem | Valor |
| --- | --- |
| Imagen | `apache/kafka:3.9.1` |
| Modo | KRaft (`KAFKA_PROCESS_ROLES=broker,controller`), sin ZooKeeper |
| Interno | `kafka:29092` (red `wa-channel`) |
| Host | `127.0.0.1:9092` |
| Auto-create | deshabilitado (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`) |
| Healthcheck | `kafka-topics.sh --bootstrap-server localhost:9092 --list` |

Contenedor one-shot `kafka-init` (misma imagen) espera `kafka` healthy y crea los topics del contrato [shared/contracts/events/topics.md](../shared/contracts/events/topics.md):

| Topic | Particiones | Réplica local | Retención (`retention.ms`) |
| --- | --- | --- | --- |
| `wa.message.received.v1` | 3 | 1 | 7d (`604800000`) |
| `wa.message.ready-to-send.v1` | 3 | 1 | 7d (`604800000`) |
| `wa.message.status.v1` | 3 | 1 | 30d (`2592000000`) |
| `wa.message.dlq.v1` | 3 | 1 | 90d (`7776000000`) |

Política `cleanup.policy=delete`. Clave Kafka: `conversationId`. Headers: `correlationId`, `eventType`, `eventVersion`.

`wa-event-platform` también puede reconciliar topics vía KafkaAdmin (`KAFKA_TOPIC_PARTITIONS`, `KAFKA_TOPIC_REPLICAS`, variables de retención). Gateway y orquestador dependen de `kafka` healthy y `kafka-init` completed successfully.

Fuera de local: réplica 3, `min.insync.replicas=2`, TLS + SASL y ACL por componente (contrato de topics).

## PostgreSQL del orquestador

Servicio `orchestrator-db` en el compose raíz:

| Ítem | Valor |
| --- | --- |
| Imagen | `postgres:17` |
| Base | `${ORCHESTRATOR_DB_NAME:-wa_orchestrator}` |
| Usuario | `${ORCHESTRATOR_DB_USER:-orchestrator}` |
| Password | `${ORCHESTRATOR_DB_PASSWORD}` (obligatorio en `.env`) |
| Volumen | `orchestrator-db-data` |
| Healthcheck | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` |

`wa-channel-orchestrator` usa perfil `docker`, JDBC a `jdbc:postgresql://orchestrator-db:5432/...`, Flyway en el componente, y `depends_on: orchestrator-db` con `condition: service_healthy` (además de Kafka / kafka-init).

Secretos solo por `.env` / `.env.example` (nombres y placeholders locales). No hay passwords en Dockerfiles ni en workflows.

## Compose raíz, puertos y arranque

- Red bridge `wa-channel`.
- Integración: gateway `8090`, orquestador `8091`, event-platform `8092` (mapeo loopback).
- IA (`project.yaml`): MCP `8101`, RAG `8102`, agente `8103`.
- H1–H6: `8080`–`8086` con sus Postgres.
- Dependencias con `service_healthy` / `service_completed_successfully` donde aplica.

Arranque operativo (detalle y smoke): [docs/local-devops.md](../docs/local-devops.md).

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Completar secretos locales en .env (nunca commitear)
docker compose --env-file .env.example config --quiet
./scripts/start.ps1
```

`--env-file .env.example` no sustituye los `env_file: .env` de varios servicios: hace falta un `.env` real en la raíz.

### Túnel Twilio (webhooks reales)

Para webhooks reales hacia el gateway local: túnel HTTPS (ngrok u similar) a `:8090` y URL pública exacta en `TWILIO_WEBHOOK_PUBLIC_URL` / `TWILIO_STATUS_CALLBACK_PUBLIC_URL`. Procedimiento: [gateway/wa-twilio-gateway/TWILIO-CONFIG.md](../gateway/wa-twilio-gateway/TWILIO-CONFIG.md). El smoke local usa simulador (`scripts/compose.smoke.yml` + `scripts/smoke_twilio.py`) sin cuenta real.

## CI / CD

### `.github/workflows/ci.yml`

| Job | Qué hace |
| --- | --- |
| `build-test` | Matriz H1–H6: `./mvnw -B clean verify` (JaCoCo ≥ 80%) |
| `build-test-platform` | Matriz `wa-event-platform`, `wa-channel-orchestrator`: verify |
| `build-test-gateway` | `wa-twilio-gateway`: verify |
| `verify-integracion-script` | `python -m unittest discover -s scripts -p "test_*.py"` |
| `docker` / `docker-platform` / `docker-gateway` | Build de imágenes en push a `main` (tras verify) |
| `helm-lint` | Lint de charts H1–H6 con placeholders (sin secretos reales) |

### `.github/workflows/ai.yml`

Calidad de componentes Python en `ai/` (uv, ruff, mypy, pytest, pip-audit) y verify del paquete IA.

### `.github/workflows/cd.yml`

Plantilla `workflow_dispatch` para Helm; deploy a cluster comentado hasta tener kubeconfig y secrets. No despliega a prod por defecto.

### Verificación local de integración

```text
python scripts/verify_integracion.py
```

En este entorno conviene verificar módulos Java **en secuencia** (`./mvnw -B clean verify` por componente) para evitar fallos temporales por carga paralela.

## Checklist int-devops

- [x] Dockerfile multi-stage, no root, healthcheck (H0 / H0.1 / H0.2)
- [x] Kafka KRaft + creación de topics desde el contrato (`kafka-init` + topics.md)
- [x] PostgreSQL del orquestador con healthcheck (`orchestrator-db`)
- [x] `depends_on: service_healthy` / completed y puertos documentados
- [x] Secretos solo por `.env` + `.env.example`; túnel documentado en TWILIO-CONFIG
- [x] CI con verify por componente y build de imágenes (plataforma + gateway + servicios)

## Pendiente de verificar en runtime (cuando Docker y secretos alcancen)

Comandos (ver [docs/local-devops.md](../docs/local-devops.md)):

```text
docker compose -f docker-compose.yml -f scripts/compose.smoke.yml up -d --build --wait --wait-timeout 600
docker compose ps --all
python scripts/seed_smoke.py seed --local-only
python scripts/smoke_twilio.py --sender "whatsapp:<phone-sintetico>" --body "Quiero consultar mi perfil" --expect QA-Smoke --timeout 60 --observe 15
```

En runs previos del mismo hito hubo entornos sin Docker accesible o sin `GOOGLE_API_KEY` / tokens exportados; eso no invalida la configuración documentada aquí. Revalidar salud y smoke con daemon accesible y secretos locales exportados antes de acreditar E2E.
