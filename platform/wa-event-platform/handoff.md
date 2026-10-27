# Handoff — Plataforma de eventos H0.1

Implementación de referencia del transporte Kafka del canal. Java 21, Spring Boot 3,
Maven Wrapper, `com.bcra.events`, puerto 8092. No ejecuta listeners de negocio:
H0 y H0.2 deben integrar estas capacidades con sus efectos y persistencia.
Fuente: [especificación](../../specs/plataforma-eventos/spec.md) y
[arquitectura](../../specs/plataforma-eventos/arquitectura.md).

## Módulos y responsabilidades

| Paquete | Entrega |
| --- | --- |
| `topic`, `messaging/config` | Nombres, KafkaAdmin/NewTopic, retención y DefaultErrorHandler autoconfigurado en la factory de listeners |
| `messaging/schema` | EventSchemaValidator draft-07, formatos y rechazo de tokens JSON posteriores; InvalidEventException sin payload |
| `messaging/producer` | KafkaEventProducer valida, usa clave conversationId y agrega headers; el llamador debe observar el futuro de envío |
| `messaging/consumer` | KafkaEventConsumer valida valor, clave y correlationId, ejecuta efecto con MDC y confirma tras éxito |
| `dlq` | DeadLetterPublisher construye y valida DeadLetter y exige confirmación del envío |
| `observability` | CorrelationIdFilter HTTP, MDC y KafkaDeliveryMetrics |

La base de consumidor no persiste deduplicación. Cada componente debe registrar `messageId`
y efecto en la misma transacción con restricción única; un duplicado se confirma sin repetir
el efecto. H0 deduplica también el MessageSid de ingreso y el messageId de salida.
H0.2 necesita outbox para publicar junto con sus cambios de estado. El contrato exige los
tres headers; actualmente la base verifica explícitamente correlationId y clave, además del
schema del valor. Los listeners integradores deben asegurar eventType/eventVersion coherentes.

## Topics y contratos

| Topic | Productor | Consumidor | Retención local | eventType |
| --- | --- | --- | --- | --- |
| `wa.message.received.v1` | H0 | H0.2 | 7 días | MessageReceived |
| `wa.message.ready-to-send.v1` | H0.2 | H0 | 7 días | MessageReadyToSend |
| `wa.message.status.v1` | H0 | H0.2 / observabilidad | 30 días | MessageStatus |
| `wa.message.dlq.v1` | Infraestructura | Soporte | 90 días | DeadLetter |

Todos tienen 3 particiones y réplica 1 en local, política `delete`. TASK-71 y el
[catálogo](../../shared/contracts/events/topics.md) prevalecen sobre la propuesta inicial
de una partición DLQ en arquitectura. KafkaAdmin crea topics y modifica retención; no reduce
particiones ni cambia réplicas existentes. No aumentar particiones con conversaciones activas
sin migración: cambia la asignación por clave. El orden es por partición y topic, sin orden global
entre topics ni garantía de recuperar la posición temporal de un evento reprocesado.

[Contratos v1](../../shared/contracts/events/README.md): los archivos
`shared/contracts/events/wa.message.*.v1.schema.json` son autocontenidos y Maven los empaqueta
en `contracts/events`. Sobre: eventId UUID, eventType, eventVersion=1, occurredAt RFC 3339,
correlationId UUID, conversationId, messageId y payload. Sin propiedades desconocidas.
Clave String UTF-8 = conversationId; headers UTF-8 correlationId/eventType/eventVersion
iguales al sobre. Cambio incompatible: nueva versión y topic con migración coordinada;
incluso campos opcionales requieren desplegar schemas en consumidores estrictos.

## Configuración y arranque local

Requisitos: JDK 21, Python para el verificador y Docker Compose para el broker local.
Desde `platform/wa-event-platform`:

```powershell
docker compose up -d --wait
.\mvnw.cmd -q -B clean verify
java -jar target/wa-event-platform-0.1.0-SNAPSHOT.jar
```

En Linux usar `./mvnw -q -B clean verify`. El JAR corre en el host; el listener anunciado
al host es localhost:9092 (interno Docker: kafka:29092). Compose usa `apache/kafka:3.9.1`
KRaft de un nodo, sin ZooKeeper, `kafka-init` según topics.md, puerto en loopback,
creación implícita desactivada y volumen persistente. App en contenedor:
`docker compose --profile app up -d --wait` (Dockerfile multi-stage, contexto raíz del repo).
Detener con `docker compose stop`; `docker compose down` conserva el volumen.

Exportar variables de [.env.example](.env.example) al entorno del proceso; Spring no carga
ese archivo automáticamente. Valores y controles:

| Variables | Default / efecto |
| --- | --- |
| SERVER_PORT, KAFKA_BOOTSTRAP_SERVERS | 8092, localhost:9092 |
| KAFKA_ADMIN_AUTO_CREATE, KAFKA_ADMIN_FAIL_FAST | true; arranque falla si Kafka no responde. Desactivar auto-create solo para pruebas sin broker |
| KAFKA_TOPIC_PARTITIONS, KAFKA_TOPIC_REPLICAS | 3 (mínimo 3), 1 |
| KAFKA_RECEIVED_RETENTION, KAFKA_READY_TO_SEND_RETENTION | 7d, 7d |
| KAFKA_STATUS_RETENTION, KAFKA_DLQ_RETENTION | 30d, 90d |
| KAFKA_PRODUCER_RETRIES | 10; acks=all, enable.idempotence=true, snappy, max.in.flight=5 |
| KAFKA_CONSUMER_GROUP_ID | wa-event-platform; cada componente debe definir su propio grupo |
| KAFKA_CONSUMER_MAX_RETRIES | 3 reintentos de consumo, además del intento inicial |
| KAFKA_CONSUMER_RETRY_INITIAL_INTERVAL, KAFKA_CONSUMER_RETRY_MAX_INTERVAL | 1000 y 10000 ms, multiplicador 2: esperas iniciales 1s, 2s, 4s |

Consumidores: auto commit desactivado, ack MANUAL, read_committed, max.poll.records=100,
concurrencia 3. Procesar cada partición secuencialmente y confirmar después del commit del
efecto; no lanzar trabajo paralelo que retorne antes de terminar.

Inspeccionar el broker, con la aplicación ya iniciada para crear los topics:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe
docker compose exec kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name wa.message.dlq.v1 --describe
Invoke-RestMethod http://localhost:8092/actuator/health
```

## Retry, reproducción y reproceso DLQ

InvalidEventException es no reintentable: recuperación inmediata. Otros fallos reintentables
agotan el backoff acotado. La DLQ contiene un nuevo sobre DeadLetter con originalTopic,
originalPartition, originalOffset, originalValueBase64 y errorCode (`SCHEMA_VALIDATION_FAILED`
o `RETRIES_EXHAUSTED`). messageId es `topic:partition:offset`; correlationId conserva el
header UUID válido o se deriva de esas coordenadas. conversationId conserva la clave válida
o usa coordenadas. Solo salen headers correlationId/eventType/eventVersion/errorCode,
sin excepción ni stack trace. Si falla publicar, se propaga el error y no se recupera el registro.

Reproducción automatizada, sin Docker ni listeners de negocio, desde el componente:

```powershell
.\mvnw.cmd -q -B '-Dtest=KafkaMessagingIntegrationTest' test
.\mvnw.cmd -q -B '-Dtest=KafkaMessagingTest,KafkaDeliveryMetricsTest' test
```

La primera prueba levanta KRaft embebido y entrega un ConsumerRecord sintético inválido `{}`
al error handler real; comprueba publicación en Kafka, schema DeadLetter y headers permitidos.
Las otras cubren backoff, errores y métricas. Publicar JSON inválido con la consola en un
topic local no dispara DLQ por sí solo: H0.1 no tiene un listener activo.
Con un consumidor integrador conectado, inspeccionar la DLQ en un entorno sintético:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic wa.message.dlq.v1 --from-beginning --timeout-ms 10000 --property print.key=true --property print.headers=true
```

Reproceso manual controlado por soporte (no existe endpoint ni replay automático):

1. Registrar autorización operativa, coordenadas originales y correlationId en el incidente,
   sin copiar payloads a tickets o logs. Corregir la causa y coordinar el orden con el dueño del consumidor.
2. Leer el DeadLetter con acceso restringido, decodificar originalValueBase64 y validar el JSON
   contra el schema de originalTopic. Un original inválido requiere corrección revisada; no reenviar `{}`.
3. Reconstruir clave conversationId y headers correlationId/eventType/eventVersion desde el
   sobre original validado. Conservar messageId de negocio para la deduplicación; el messageId
   técnico del DeadLetter no lo reemplaza. Mantener correlationId original válido y registrar
   su relación con la recuperación si fue necesario reparar el sobre.
4. Publicar mediante KafkaEventProducer al topic original y esperar confirmación del futuro.
   No publicar el sobre DeadLetter en el topic de negocio ni reenviar a la propia DLQ.
5. Confirmar el efecto o duplicado en el consumidor, registrar resultado y coordenadas del envío.
   Un replay puede quedar detrás de mensajes recientes: no asumir que restaura el orden histórico.
   No borrar registros ni mover offsets del grupo de negocio como mecanismo de replay.

Base64 no cifra. La DLQ conserva contenido potencialmente sensible del evento original;
aplicar ACL restringida, retención y protección equivalentes a los topics de origen.

## Observabilidad y seguridad

Actuator: `/actuator/health`, `/actuator/info`, `/actuator/prometheus` en 8092.
Logs ECS JSON con correlationId MDC. El filtro HTTP conserva UUID válido o genera uno y
lo devuelve en respuesta; productores y consumidores deben propagar el mismo valor.
No registrar cuerpos ni credenciales ni usar IDs como tags de métricas.

| Serie Prometheus | Interpretación |
| --- | --- |
| platform_kafka_delivery_errors_total | Intentos de consumo fallidos, incluidos reintentos |
| platform_kafka_dlq_published_total | Recuperaciones DLQ confirmadas, acumulativas; no tamaño actual de cola |
| platform_kafka_dlq_errors_total | Fallos de recuperación |
| kafka_consumer_fetch_manager_records_lag_max | Lag nativo solo con consumidor activo |
| kafka_consumer_fetch_manager_records_consumed_total | Volumen consumido del cliente activo |

Spring Boot instrumenta clientes de sus factories; clientes manuales requieren KafkaClientMetrics
y cierre. H0.1 no inventa lag cero sin consumidores. Observaciones de template/listeners habilitadas;
la trazabilidad completa H0/H0.2/H9/Twilio requiere validación posterior del canal.

PLAINTEXT solo local. Fuera de local: TLS + SASL, ACL por topic y componente, réplica 3 y
min.insync.replicas=2; estas medidas requieren configuración de despliegue, no están activas
en Compose. Inyectar secretos por entorno (SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL,
SPRING_KAFKA_PROPERTIES_SASL_MECHANISM, SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG).
Restringir Actuator a red interna. No enviar documentos, saldos ni credenciales en eventos.

## Verificación y decisiones

Desde la raíz: `python scripts/verify_integracion.py`. Detecta `platform/*/pom.xml`, incluido
este módulo, y `gateway/*/pom.xml`; ejecuta wrapper propio con `-q -B verify` y falla si falla alguno.
No requiere cambios para incluir H0.1. En el módulo, `clean verify` exige JaCoCo de líneas ≥80%;
reporte en `target/site/jacoco/index.html` y resultados en `target/surefire-reports`.
KafkaPlatformIntegrationTest cubre topics, retención y orden; KafkaMessagingTest cubre validación,
reentrega tras fallo transitorio y ACK; KafkaMessagingIntegrationTest cubre envío y DLQ real;
EventSchemaValidatorTest cubre contratos; pruebas de observabilidad cubren MDC, endpoints y métricas.
Kafka embebido no verifica el despliegue Docker ni TLS/SASL; deduplicación persistente de H0/H0.2
y pruebas completas del canal corresponden a sus componentes.

ADRs aceptados: [Kafka backbone](../../docs/adr/ADR-001-kafka-backbone.md),
[at-least-once e idempotencia](../../docs/adr/ADR-002-at-least-once-idempotency.md),
[partición por conversación](../../docs/adr/ADR-003-partition-by-conversation.md).
