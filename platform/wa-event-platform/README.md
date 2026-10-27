# wa-event-platform

Base H0.1 — Plataforma de eventos Kafka del canal WhatsApp Banking. Java 21, Spring Boot 3, groupId `com.bcra`, paquete `com.bcra.events`, puerto `8092`.

## Límites (qué hace / qué no hace)

**Hace:**
- Topics, convenciones, esquemas versionados y configuración Kafka KRaft local.
- Productor/consumidor con validación de esquemas, reintentos con backoff y DLQ.
- Idempotencia a nivel infraestructura (entrega at-least-once). Cada componente debe deduplicar.
- Trazabilidad con `correlationId` (propagado en headers/MDC).
- Métricas de entrega, DLQ y lag nativo con Micrometer/Actuator.

**No hace:**
- Lógica de negocio o conversación.
- Persistencia de negocio (Postgres no aplica a H0.1).
- Listeners de negocio activos (base para integración por H0/H0.2).
- Transformación a lenguaje natural o coordinación de flujos.

## Documentación relacionada

- [Handoff de operación e integración](handoff.md)
- [Catálogo de topics](../../shared/contracts/events/topics.md)
- [Catálogo de contratos de eventos](../../shared/contracts/events/README.md)
- ADRs: [Kafka backbone](../../docs/adr/ADR-001-kafka-backbone.md), [at-least-once e idempotencia](../../docs/adr/ADR-002-at-least-once-idempotency.md), [partición por conversación](../../docs/adr/ADR-003-partition-by-conversation.md)

## Requisitos

- JDK 21
- Docker Compose
- Python 3 (para `scripts/verify_integracion.py`)
- Maven Wrapper (incluido)

## Variables de entorno

Copiar `platform/wa-event-platform/.env.example` a las variables del entorno. **No** commitear valores reales.

| Variable | Default | Descripción |
| --- | --- | --- |
| `SERVER_PORT` | `8092` | Puerto de la aplicación |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Bootstrap servers de Kafka |
| `KAFKA_ADMIN_AUTO_CREATE` | `true` | Crear/actualizar topics al arrancar (false solo para tests sin broker) |
| `KAFKA_ADMIN_FAIL_FAST` | `true` | Fallar si Kafka no responde al arrancar |
| `KAFKA_TOPIC_PARTITIONS` | `3` | Particiones por topic (mínimo 3) |
| `KAFKA_TOPIC_REPLICAS` | `1` | Réplicas por topic (local) |
| `KAFKA_RECEIVED_RETENTION` | `7d` | Retención `wa.message.received.v1` |
| `KAFKA_READY_TO_SEND_RETENTION` | `7d` | Retención `wa.message.ready-to-send.v1` |
| `KAFKA_STATUS_RETENTION` | `30d` | Retención `wa.message.status.v1` |
| `KAFKA_DLQ_RETENTION` | `90d` | Retención `wa.message.dlq.v1` |
| `KAFKA_PRODUCER_RETRIES` | `10` | Reintentos del productor (acks=all, idempotencia habilitada) |
| `KAFKA_CONSUMER_GROUP_ID` | `wa-event-platform` | Group ID (cada componente debe usar el propio) |
| `KAFKA_CONSUMER_MAX_RETRIES` | `3` | Reintentos de consumo tras intento inicial |
| `KAFKA_CONSUMER_RETRY_INITIAL_INTERVAL` | `1000` | Intervalo inicial backoff (ms) |
| `KAFKA_CONSUMER_RETRY_MAX_INTERVAL` | `10000` | Intervalo máximo backoff (ms) |

> Fuera de local: configurar TLS + SASL y ACL por topic/componente. Ver [Seguridad](#seguridad).

## Cómo levantarlo

### 1) Broker KRaft + topics

Desde `platform/wa-event-platform`:

```powershell
docker compose up -d --wait
```

- Crea broker KRaft (sin ZooKeeper) en `localhost:9092` (host) / `kafka:29092` (red Docker).
- `kafka-init` crea los 4 topics con 3 particiones/1 réplica y retenciones configuradas.
- Volumen persistente `kafka-data`. `docker compose stop` / `docker compose down` (conserva volumen).

### 2) Aplicación en host (desarrollo)

```powershell
# Windows
.\mvnw.cmd -q -B clean verify
# Exportar variables de .env.example; bootstrap = localhost:9092
java -jar target/wa-event-platform-0.1.0-SNAPSHOT.jar
```

```bash
# Linux/macOS
./mvnw -q -B clean verify
export $(grep -v '^#' .env.example | xargs)
java -jar target/wa-event-platform-0.1.0-SNAPSHOT.jar
```

Spring Boot **no** carga `.env` automáticamente; exportar variables o cargar desde shell.

### 3) Aplicación en contenedor (opcional)

```powershell
# Desde platform/wa-event-platform
docker compose --profile app up -d --wait
# O desde raíz del repo
docker build -f platform/wa-event-platform/Dockerfile -t wa-event-platform:local .
```

## Cómo probarlo

### Tests unitarios/integración (sin Docker requerido para embebido)

```powershell
# Windows
.\mvnw.cmd -q -B clean verify
```

```bash
# Linux/macOS
./mvnw -q -B clean verify
```

Cobertura JaCoCo mínima **80% líneas**. Reportes: `target/site/jacoco/index.html`, `target/surefire-reports/`.

Tests relevantes:
- `KafkaPlatformIntegrationTest` — topics, retención, orden por `conversationId` (Kafka KRaft embebido)
- `KafkaMessagingTest` — validación, reintentos, ACK
- `KafkaMessagingIntegrationTest` — envío real y publicación a DLQ
- `EventSchemaValidatorTest` — contratos JSON Schema
- Tests de observabilidad (MDC/correlationId, métricas, endpoints)

### Verificación del equipo (desde raíz)

```powershell
python scripts/verify_integracion.py
```

### Health/Métricas

- Health: `http://localhost:8092/actuator/health`
- Info: `http://localhost:8092/actuator/info`
- Prometheus: `http://localhost:8092/actuator/prometheus`

## Contratos

- **Topics**: ver [shared/contracts/events/topics.md](../../shared/contracts/events/topics.md)
- **Eventos/Schemas**: ver [shared/contracts/events/README.md](../../shared/contracts/events/README.md)
- **Schemas JSON**: `shared/contracts/events/*.schema.json` (empaquetados por Maven en `contracts/events`)
- Clave obligatoria: `conversationId`. Headers: `correlationId`, `eventType`, `eventVersion` (iguales al sobre).
- Validación con NetworkNT; `additionalProperties: false` en sobre y payloads.

## Retry, DLQ y reproceso

- **No reintentable**: `InvalidEventException` → DLQ inmediato (sin reintentos).
- **Reintentable**: backoff exponencial con `KAFKA_CONSUMER_*` hasta agotar reintentos → DLQ.
- **DLQ**: `wa.message.dlq.v1` con evento `DeadLetter`. Conserva `originalValueBase64`, coordenadas originales. Solo headers `correlationId`, `eventType`, `eventVersion`, `errorCode`. Sin stack traces.
- **Reproceso**: ver procedimiento en [shared/contracts/events/README.md#reproceso-de-dlq-guía-para-soporte](../../shared/contracts/events/README.md#reproceso-de-dlq-guía-para-soporte) y [handoff.md](handoff.md#retry-reproducción-y-reproceso-dlq).
- **Nota**: publicar JSON inválido por consola **no** activa DLQ automáticamente (H0.1 no tiene listeners de negocio activos). Usar tests integrados o consumidor integrador.

## Trazabilidad (correlationId)

- Filtro HTTP acepta/propaga `correlationId` UUID; lo devuelve en header de respuesta y lo pone en MDC.
- `KafkaEventProducer` copia `correlationId` a headers Kafka.
- `KafkaEventConsumer` verifica `correlationId` válido y lo coloca en MDC (se restaura incluso ante errores).
- No copiar cuerpos/credenciales a MDC. No usar IDs como etiquetas de métricas.

## Observabilidad

| Métrica | Descripción |
| --- | --- |
| `platform_kafka_delivery_errors_total` | Intentos de consumo fallidos (incluye reintentos) |
| `platform_kafka_dlq_published_total` | Recuperaciones a DLQ confirmadas (acumulativo, no tamaño actual) |
| `platform_kafka_dlq_errors_total` | Fallos al publicar en DLQ |
| `kafka_consumer_fetch_manager_records_lag_max` | Lag nativo (solo con consumidores activos) |
| `kafka_consumer_fetch_manager_records_consumed_total` | Volumen consumido (solo con consumidores activos) |

Logs en formato **ECS JSON** con `correlationId` desde MDC. Clientes Kafka instrumentados por Spring Boot/Micrometer.

## Seguridad

- **Local**: PLAINTEXT exclusivamente.
- **Fuera de local**: configurar TLS + SASL, ACL por topic/componente, réplica 3, `min.insync.replicas=2`. Inyectar credenciales por entorno (`SPRING_KAFKA_PROPERTIES_*`), **nunca** commitearlas.
- Actuator debe quedar en red interna.
- No enviar secretos, PII innecesaria, documentos ni credenciales en eventos o logs.
- DLQ requiere ACL restringida (Base64 **no** cifra).

## Troubleshooting

| Problema | Causa/Solución |
| --- | --- |
| App no arranca (Kafka no disponible) | Verificar `docker compose ps`. Usar `KAFKA_ADMIN_AUTO_CREATE=false` solo para tests sin broker. |
| Topics no creados | Revisar `kafka-init`. Inspeccionar con `kafka-topics.sh --describe`. KafkaAdmin actualiza retención al arrancar. |
| DLQ no recibe eventos inválidos con consola | H0.1 no tiene listeners activos; usar `KafkaMessagingIntegrationTest` o conectar consumidor integrador. |
| Lag en 0 o no aparece | `kafka_consumer_fetch_manager_*` aparece solo con consumidores activos. H0.1 no inventa lag cero. |
| Errores de validación se reintentan | Verificar `addNotRetryableExceptions(InvalidEventException.class)`. `InvalidEventException` es no reintentable. |
| Fallo al publicar a DLQ | `DeadLetterPublisher` propaga error y **no** confirma el registro original; revisar ACL/permisos Kafka. |

## Integración con otros componentes

- **H0 (Gateway Twilio)**: publica `MessageReceived`/`MessageStatus`, consume `MessageReadyToSend`. Debe propagar `correlationId` y deduplicar por `messageId`.
- **H0.2 (Orquestador)**: consume `MessageReceived`/`MessageStatus`, publica `MessageReadyToSend`. Debe usar outbox, deduplicar por `messageId` en transacción, respetar orden por `conversationId`.
- Ver [handoff.md](handoff.md) para detalles de responsabilidades y contrato con integradores.
