# wa-channel-orchestrator — H0.2

Orquestación técnica con Java 21, Spring Boot 3, Kafka KRaft y PostgreSQL.
GroupId `com.bcra`, paquete `com.bcra.orchestrator`, puerto 8091.
H0 adapta Twilio, H9 interpreta mensajes y usa H7, H1–H6 conservan sus reglas.

## Arranque y variables

Stack en `docker-compose.yml` (Kafka KRaft 3.9.1, `kafka-init` con topics H0.1,
PostgreSQL 17 `orchestrator-db`, app con profile `app`). Puerto **8091**.
Copiá [.env.example](.env.example) a `.env` (fuera de git) y completá
`SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` y `H9_SERVICE_TOKEN`.
Spring Boot no carga `.env` automáticamente; `docker compose` sí lo usa para
sustituir variables del compose.

```powershell
# Desde platform/wa-channel-orchestrator
docker compose up -d --wait                    # broker + kafka-init + postgres
docker compose --profile app up -d --wait      # + wa-channel-orchestrator
# O desde la raíz del repo
docker build -f platform/wa-channel-orchestrator/Dockerfile -t wa-channel-orchestrator:local .
```

Sin contenedor: JDK 21, PostgreSQL con base `wa_orchestrator` y Kafka con los
[topics H0.1](../../shared/contracts/events/topics.md). Exportar las variables de
`.env.example` en el proceso. Desde esta carpeta:

```powershell
.\mvnw.cmd -q -B spring-boot:run
```

Linux: `./mvnw`. Flyway aplica V1–V7; Hibernate valida el esquema.
H9 en el host desde el contenedor: `H9_BASE_URL=http://host.docker.internal:8099`.
Si H9 solo es alcanzable por túnel, usá la URL HTTPS pública en `H9_BASE_URL`.
El [handoff](../../shared/contracts/wa-channel-orchestrator/handoff.md) detalla variables,
contratos y operación. Fuera de local quitar el perfil `local` (fuerza PLAINTEXT), configurar
TLS/SASL y ACL, usar HTTPS para H9 y restringir Actuator a operación.
Health/info/prometheus: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`.
Logs ECS con correlationId en MDC; no habilitar trazas de cuerpos HTTP ni DEBUG de Kafka.

## Integración y guías

| Área | Guía |
|---|---|
| Consumo, deduplicación y orden | [INBOUND.md](INBOUND.md) |
| Cliente y definición provisional de turno | [H9.md](H9.md) |
| Pausa, timeout y compensación | [SAGAS.md](SAGAS.md) |
| Publicación transaccional y despacho serial | [OUTBOUND.md](OUTBOUND.md) |
| Diagnóstico y reproceso | [DLQ.md](DLQ.md) |

Están implementados esos puertos y sus pruebas. Falta el handler de producción
`@EventListener(InboundMessage)` síncrono que una contexto, H9, saga/contingencia y outbox
dentro de la transacción deduplicada. El consumidor aislado confirma deduplicación sin generar respuesta.
También siguen pendientes el contrato compartido H9, señales H9/H7 y adaptadores SagaCompensation H3/H2.
Sin adaptador la compensación queda FAILED/SUPPORT_REQUIRED. No anunciar un recorrido completo aún.

Persistencia: conversation_state, saga_instance, saga_step_execution, message_dedupe y outbox.
messageId es globalmente único; estado/deduplicación/outbox comparten transacción.
H0 debe deduplicar salidas at-least-once. No borrar deduplicación ni cambiar particiones sin migración.

## Verificación

Desde esta carpeta (Linux: `./mvnw`):

```powershell
.\mvnw.cmd -q -B clean verify
.\mvnw.cmd -q -B verify '-Dgroups=integration'
```

JaCoCo exige ≥80% de líneas. Testcontainers Kafka/PostgreSQL requiere Docker;
los tests integration se omiten automáticamente sin él. WireMock prueba H9.
La prueba de arranque aislada no comprueba conectividad real. Para caché escribible agregar
`'-Dmaven.repo.local=../../.maven-cache'`.
Desde la raíz:

```powershell
python scripts/verify_integracion.py
python -m unittest discover -s scripts -p test_verify_integracion.py
```

El verificador descubre automáticamente este POM en `platform/*/pom.xml` y ejecuta su wrapper
con `-q -B verify`, junto con los demás componentes gateway/platform.
Decisiones: [ADR-006](../../docs/adr/ADR-006-orquestador-dedicado-ciclo-vida-canal.md),
[ADR-007](../../docs/adr/ADR-007-estado-conversacion-saga-persistente.md),
[ADR-008](../../docs/adr/ADR-008-limite-orquestacion-vs-conversacion-h9.md).
