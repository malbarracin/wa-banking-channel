---
title: Arquitectura — Plataforma de Eventos (Kafka)
status: borrador
source: specs/plataforma-eventos/spec.md + Hito H0.1 (Notion
  3a675bb08ca781db9516c18960a48060) + Roadmap Backend (convenciones técnicas del
  programa)
updated: 2026-11-21T04:02:01.524Z
---

# Arquitectura — Plataforma de Eventos (Kafka)

Baja a diseño la especificación `specs/plataforma-eventos/spec.md` (hito H0.1). Es la referencia normativa del
canal para todo lo que viaja por Kafka: nombres de topics, sobre de los eventos, claves, headers, idempotencia,
orden, reintentos, DLQ y observabilidad. **H0 (gateway) y H0.2 (orquestador) se implementan contra este documento
y contra los contratos que publica el hito**; no dependen de código de este componente.

## 1. Alcance y límites

| Hace | No hace |
| --- | --- |
| Topics, convenciones de nombres y claves de partición | Lógica de negocio o conversación |
| Catálogo de eventos versionado y su validación | Almacenamiento de dominio (Kafka no es base de datos) |
| Política de reintentos, DLQ y reproceso | Hablar con Twilio o exponerle topics |
| Kafka KRaft local con sus topics creados | Estado de conversación ni sagas (es H0.2) |
| Productor y consumidor de referencia + observabilidad | Webhooks, firma ni normalización (es H0) |

El componente **no tiene base de datos**: no lleva JPA, ni Flyway, ni PostgreSQL. Tampoco expone endpoints de
negocio: solo Actuator y un endpoint de humo limitado al perfil `dev` (ver §3).

## 2. Datos del proyecto (resueltos, no preguntar de nuevo)

| Dato | Valor | De dónde sale |
| --- | --- | --- |
| Carpeta del componente | `platform/wa-event-platform` | orden del hito / spec |
| Grupo Maven | `com.bcra` | convenciones del programa |
| artifactId | `wa-event-platform` | convención de carpeta = artefacto |
| Paquete base | `com.bcra.events` | convenciones del programa |
| Carpeta de contratos | `shared/contracts/events/` (catálogo y esquemas) y `shared/contracts/wa-event-platform/handoff.md` | convenciones del programa |
| Prefijo y forma de topics | `wa.` · `wa.<entidad>.<hecho>.v<n>` | spec |
| Java / Spring Boot | Java 21 · Spring Boot 3.x (última estable de la línea 3.5.x al cerrar esta arquitectura) | regla de stack |
| Puerto HTTP del componente | `8092` (supuesto, ver §16) — Kafka local en `9092` | no asignado en las convenciones |
| Componentes vecinos | H0 `gateway/wa-twilio-gateway` (8090) · H0.2 `platform/wa-channel-orchestrator` (8091) | convenciones del programa |

Los componentes **no comparten código**: se hablan por los contratos de `shared/contracts/`. No se publica una
librería Java común (ver ADR-0004).

## 3. Estructura del componente

Un único módulo Maven con wrapper propio (`./mvnw`, nunca `mvn` global).

```
platform/wa-event-platform/
  pom.xml  mvnw  mvnw.cmd  .mvn/wrapper/
  .env.example  README.md
  src/main/java/com/bcra/events/
    EventPlatformApplication.java
    config/          KafkaProducerConfig · KafkaConsumerConfig · TopicsProperties · TopicsAdminConfig
                     SchemaProperties · JacksonConfig · KafkaHealthIndicator
    domain/          EventEnvelope · EventType · MessageReceivedPayload · MessageReadyToSendPayload
                     MessageStatusPayload · MessageStatus · MessageKind
    schema/          SchemaCatalog · SchemaValidator · SchemaValidationException
    messaging/       EventPublisher · EventPublicationException
      consumer/      EnvelopeReader · IdempotencyGuard · InMemoryIdempotencyStore
                     ReferenceMessageReceivedListener
      dlq/           DeadLetterConfig · DlqHeaders · DlqExceptionHeadersCreator · DlqReprocessor
    observability/   CorrelationMdc · EventMetrics · PhoneMasker
    web/             DevEventController          (@Profile("dev"), solo humo local)
  src/main/resources/application.yml
  src/test/java/…  src/test/resources/logback-test.xml   (raíz WARN)
```

Paquetes por responsabilidad, según la regla de arquitectura de integración: `web` mínimo, `messaging` para
productor/consumidor, `domain` para el modelo normalizado, `config` para la infraestructura. No hay `persistence`
ni `client` porque este componente no tiene base de datos ni habla con servicios externos.

Dependencias del `pom.xml`: starters `web`, `actuator`, `validation`, `spring-kafka`,
`com.networknt:json-schema-validator`, Lombok, MapStruct, `micrometer-registry-prometheus`; en test
`spring-boot-starter-test`, `spring-kafka-test`, Testcontainers (`kafka`, `junit-jupiter`), Awaitility. JaCoCo con
`check` en `verify` y mínimo 0.80 de líneas. Versiones de terceros en `<properties>`.

Verificación del repo: `scripts/verify_integracion.py` (salida corta; el log completo a `target/verify.log`).

### Los esquemas no se duplican

`shared/contracts/events/` es la única fuente de los esquemas. El `pom.xml` de cada componente los copia al
classpath en el build con `maven-resources-plugin`:

```
${project.basedir}/../../shared/contracts/events  →  ${project.build.outputDirectory}/contracts/events
```

La ruta relativa `../../` vale igual para `platform/…` y `gateway/…`. En runtime se cargan de
`classpath:contracts/events/*.schema.json` (`SchemaCatalog`, cacheados al arrancar; si falta un esquema de un
`eventType` declarado, la aplicación **no arranca**).

## 4. Catálogo de eventos y topics

| Topic | `eventType` | Clave | Productor | Consumidor |
| --- | --- | --- | --- | --- |
| `wa.message.received.v1` | `MessageReceived` | `conversationId` | H0 gateway | H0.2 orquestador |
| `wa.message.ready-to-send.v1` | `MessageReadyToSend` | `conversationId` | H0.2 orquestador | H0 gateway |
| `wa.message.status.v1` | `MessageStatus` | `conversationId` | H0 gateway | H0.2 orquestador / observabilidad |
| `wa.message.dlq.v1` | el del evento original | la del registro original | cualquier consumidor que agota reintentos | soporte técnico (herramienta de reproceso) |

Son los cuatro topics del piloto que fija la especificación. **No se crean topics de retry** (ver ADR-0005).

### Configuración de cada topic

| Topic | Particiones (local) | Replicación (local) | Retención | Notas |
| --- | --- | --- | --- | --- |
| `wa.message.received.v1` | 3 | 1 | 7 días | `cleanup.policy=delete` |
| `wa.message.ready-to-send.v1` | 3 | 1 | 7 días | `cleanup.policy=delete` |
| `wa.message.status.v1` | 3 | 1 | 7 días | `cleanup.policy=delete` |
| `wa.message.dlq.v1` | 1 | 1 | 30 días | una partición: el reproceso es manual y ordenado |

En ambientes compartidos: 6 particiones, `replication.factor=3`, `min.insync.replicas=2`. `max.message.bytes`
queda en el default (1 MiB): **los adjuntos no viajan por Kafka**, viaja la referencia del proveedor (§6).

Los topics se declaran como `NewTopic` (`TopicBuilder`) a partir de `TopicsProperties`, activos en los perfiles
`local`, `dev` y `test`. En ambientes compartidos los provisiona infraestructura con esos mismos valores, y la
aplicación solo verifica que existan al arrancar (`KafkaHealthIndicator`).

### Grupos de consumidores

| `group.id` | Componente | Topics que lee |
| --- | --- | --- |
| `wa-twilio-gateway` | H0 | `wa.message.ready-to-send.v1` |
| `wa-channel-orchestrator` | H0.2 | `wa.message.received.v1`, `wa.message.status.v1` |
| `wa-event-platform-reference` | H0.1 (referencia) | `wa.message.received.v1` |

El consumidor de referencia solo se registra en los perfiles `local`, `dev` y `test`: no compite con el
orquestador (grupo distinto) y nunca corre en un ambiente compartido.

## 5. Sobre, headers y clave

Todos los eventos usan el mismo sobre, con `additionalProperties: false`:

```json
{
  "eventId": "9f1c1a2e-5b7d-4c3a-8f21-0a1b2c3d4e5f",
  "eventType": "MessageReceived",
  "eventVersion": 1,
  "occurredAt": "2026-11-21T14:03:12.482Z",
  "correlationId": "3b0f8e14-7c2a-4d55-9e01-11b22c33d44e",
  "conversationId": "conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b",
  "messageId": "SM1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6",
  "payload": { }
}
```

- **Valor del registro**: JSON UTF-8 serializado como texto (`StringSerializer` / `StringDeserializer`). Se valida
  el JSON exacto que se envía y el exacto que llega. Prohibida la deserialización polimórfica
  (`JsonDeserializer` con headers de tipo, `spring.json.trusted.packages=*`): el `eventType` del sobre decide a qué
  record se mapea, con `FAIL_ON_UNKNOWN_PROPERTIES=true`.
- **Clave del registro**: `conversationId` (String, nunca nula; el productor la rechaza si falta).
- **Headers** (UTF-8, obligatorios en todo evento): `correlationId`, `eventType`, `eventVersion`. Deben coincidir
  con el sobre; si no coinciden, el consumidor lo trata como evento inválido (§9).
- `occurredAt`: ISO-8601 en UTC con `Z`, momento en que ocurrió el hecho en el componente que publica.

### Identificadores

| Campo | Qué es | Quién lo genera |
| --- | --- | --- |
| `eventId` | UUID v4 único por publicación. Dos entregas del mismo registro traen el mismo `eventId` | quien publica |
| `correlationId` | UUID v4 del turno completo; nace en el gateway al recibir el webhook y se propaga sin cambios por todo el canal (headers + MDC) | H0 gateway |
| `conversationId` | `conv-` + UUID v5 determinístico del número normalizado en E.164 (`uuidV5(namespace, "whatsapp:" + e164)`), con una semilla de entorno. Opaco, estable y sin PII: es clave de partición y va en todos los logs (ADR-0006) | H0 gateway |
| `messageId` | Identificador del mensaje dentro del canal. En `MessageReceived`: el `MessageSid` de Twilio. En `MessageReadyToSend`: UUID v4 del orquestador. En `MessageStatus`: el `messageId` del `MessageReadyToSend` cuyo estado cambió | quien publica |

`messageId` es **la clave de deduplicación de negocio** (§9). `conversationId` nunca contiene el teléfono, por eso
puede aparecer en logs y métricas sin enmascarar.

## 6. Esquemas (`shared/contracts/events/`)

Archivos del hito: `envelope.v1.schema.json`, `message-received.v1.schema.json`,
`message-ready-to-send.v1.schema.json`, `message-status.v1.schema.json`, `topics.md` y `README.md` (catálogo).
JSON Schema **draft 2020-12**. Cada esquema de evento referencia el sobre con `allOf` y solo **restringe**
propiedades que el sobre ya declara (así `additionalProperties: false` sigue siendo válido).

### `envelope.v1.schema.json`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://contracts.bcra.local/wa/events/envelope.v1.schema.json",
  "title": "Sobre de evento del canal WhatsApp",
  "type": "object",
  "additionalProperties": false,
  "required": ["eventId", "eventType", "eventVersion", "occurredAt",
               "correlationId", "conversationId", "messageId", "payload"],
  "properties": {
    "eventId":        { "type": "string", "format": "uuid" },
    "eventType":      { "type": "string", "enum": ["MessageReceived", "MessageReadyToSend", "MessageStatus"] },
    "eventVersion":   { "type": "integer", "minimum": 1 },
    "occurredAt":     { "type": "string", "format": "date-time" },
    "correlationId":  { "type": "string", "format": "uuid" },
    "conversationId": { "type": "string",
                        "pattern": "^conv-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$" },
    "messageId":      { "type": "string", "minLength": 1, "maxLength": 128,
                        "pattern": "^[A-Za-z0-9._:-]+$" },
    "payload":        { "type": "object" }
  }
}
```

### `message-received.v1.schema.json`

`payload` con `additionalProperties: false`:

| Campo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `channel` | `const "whatsapp"` | sí | el canal del piloto |
| `from` | string E.164 `^\+[1-9][0-9]{7,14}$` | sí | número del cliente. PII necesaria: enmascarada en logs (§13) |
| `to` | string E.164 | sí | número de WhatsApp del banco |
| `kind` | enum `TEXT`, `MEDIA`, `LOCATION`, `UNSUPPORTED` | sí | |
| `text` | objeto `{ body: string 1..4096 }` | si `kind=TEXT` | |
| `media` | objeto `{ providerMediaId, contentType, sizeBytes? }` | si `kind=MEDIA` | **referencia, no bytes**: el adjunto se baja del proveedor cuando se necesita |
| `location` | objeto `{ latitude: -90..90, longitude: -180..180 }` | si `kind=LOCATION` | |

Obligatoriedad condicional con `allOf` + `if/then` sobre `kind`. **No viajan** el nombre de perfil de WhatsApp, el
cuerpo crudo del webhook, ni ningún dato de identidad del cliente: la identidad la resuelve H3/H2 más adelante en el
flujo.

### `message-ready-to-send.v1.schema.json`

| Campo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `to` | string E.164 | sí | destino |
| `kind` | enum `TEXT`, `TEMPLATE` | sí | |
| `text` | objeto `{ body: string 1..4096 }` | si `kind=TEXT` | ya resuelto por el agente; la plataforma no lo edita |
| `template` | objeto `{ name, language: ^[a-z]{2}(_[A-Z]{2})?$, variables: array de string, máx. 10 }` | si `kind=TEMPLATE` | |
| `replyToMessageId` | string, mismo patrón que `messageId` | no | `messageId` del inbound que se está respondiendo |

### `message-status.v1.schema.json`

| Campo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `providerMessageId` | string 1..128 | sí | `MessageSid` del mensaje enviado; el gateway guarda el mapeo `messageId` ↔ `MessageSid` |
| `status` | enum `QUEUED`, `SENT`, `DELIVERED`, `READ`, `FAILED`, `UNDELIVERED` | sí | |
| `errorCode` | string 1..32 | no | código del proveedor (ej. `63016`) |
| `errorDetail` | string 1..200 | no | mensaje saneado; nunca stack traces ni cuerpo del request |

El `messageId` del sobre es el del `MessageReadyToSend` cuyo estado cambió: así el orquestador correlaciona sin
conocer a Twilio.

### DLQ

`wa.message.dlq.v1` **no tiene esquema de payload**: transporta el registro original tal cual (misma clave, mismos
bytes) más headers de falla. No se valida al publicar en DLQ (el evento puede ser justamente inválido).

## 7. Productor

```
acks=all
enable.idempotence=true
max.in.flight.requests.per.connection=5
retries=2147483647   delivery.timeout.ms=120000   request.timeout.ms=30000
compression.type=lz4
key/value serializer = StringSerializer
```

Secuencia de `EventPublisher.publish(topic, envelope)`:

1. Completa `eventId` y `occurredAt` si faltan; exige `correlationId`, `conversationId` y `messageId`.
2. Verifica que `eventVersion` coincida con el sufijo `.v<n>` del topic (invariante del contrato).
3. Serializa a JSON y **valida contra el esquema del `eventType`**. Inválido = bug del productor:
   `SchemaValidationException`, no se publica, métrica `wa_events_invalid_total{stage="produce"}`.
4. Envía con clave `conversationId` y los tres headers.
5. Nunca "fire and forget": o `get()` con timeout donde hay que confirmar antes de responder (el ACK del webhook en
   H0), o callback que loguea el fallo y lo cuenta en `wa_events_publish_failed_total`.

## 8. Consumidor

```
enable.auto.commit=false
auto.offset.reset=earliest
max.poll.records=10
ackMode=MANUAL_IMMEDIATE        # se confirma después del efecto
concurrency ≤ particiones del topic (3 en local)
key/value deserializer = StringDeserializer
```

Orden: una conversación cae siempre en la misma partición y una partición la procesa un solo hilo. **Prohibido**
paralelizar dentro de la partición (sin pool de hilos por registro, sin `@Async` en el listener).

`EnvelopeReader` en cada recepción: parsea, valida contra el esquema, compara headers con el sobre, pone
`correlationId`, `conversationId`, `messageId` y `eventType` en el MDC, y limpia el MDC al salir.

No se usan transacciones de Kafka en el piloto: la combinación at-least-once + idempotencia del consumidor + ack
manual después del efecto alcanza y es más simple de operar (ADR-0002).

## 9. Idempotencia

Entrega at-least-once: **todo consumidor es idempotente**. Dos duplicados distintos y los dos se cubren:

| Caso | Clave | Qué hace el consumidor |
| --- | --- | --- |
| Reentrega del mismo registro (rebalanceo, reinicio) | `eventId` | corta temprano y confirma |
| Mismo mensaje publicado dos veces (p. ej. Twilio reintenta el webhook) | `messageId` | corta y confirma sin repetir el efecto |

Contrato para los consumidores del canal: registrar `(groupId, messageId)` como **procesado en la misma transacción
que el efecto**, con restricción única; si la inserción choca, es duplicado → se confirma sin repetir.

- H0.2 (orquestador): tabla en PostgreSQL con `UNIQUE (consumer_group, message_id)`, insert-if-absent en la misma
  transacción que el cambio de estado.
- H0 (gateway): store de `MessageSid` para el webhook inbound y de `messageId` para el envío outbound, con TTL ≥ 24 h.
- Este componente: `InMemoryIdempotencyStore` (Caffeine, TTL 24 h, tope de entradas) **solo como referencia**;
  está documentado que no sobrevive a un reinicio y que no sirve para un consumidor de verdad.

## 10. Reintentos y DLQ

Reintentos **bloqueantes** en el mismo hilo de la partición, para no romper el orden por conversación (ADR-0005):

```
DefaultErrorHandler + ExponentialBackOffWithMaxRetries(4)
  intervalo inicial 500 ms · multiplicador 2.0 · intervalo máximo 8 s
  → intentos a 0,5 s · 1 s · 2 s · 4 s  (5 entregas en total, ~7,5 s)
DeadLetterPublishingRecoverer → wa.message.dlq.v1
```

No reintentables (`addNotRetryableExceptions`, van derecho a DLQ): error de parseo JSON, `SchemaValidationException`,
desajuste entre headers y sobre, `eventVersion` que no corresponde al topic, y las violaciones de contrato que
declare cada consumidor. Reintentables: todo lo transitorio (red, base de datos, 5xx de un servicio).

Headers del registro en DLQ (los estándar de Spring, más dos propios):

| Header | Contenido |
| --- | --- |
| `kafka_dlt-original-topic` / `-original-partition` / `-original-offset` / `-original-timestamp` | de dónde venía |
| `kafka_dlt-exception-fqcn` | clase de la excepción |
| `kafka_dlt-exception-message` | mensaje **truncado a 200 caracteres y saneado** |
| `x-dlq-attempts` | entregas consumidas antes de rendirse |
| `x-dlq-failed-at` | ISO-8601 UTC |
| `correlationId`, `eventType`, `eventVersion` | los del evento original, intactos |

El stack trace completo **no va a DLQ**: se excluye el header `kafka_dlt-exception-stacktrace`
(`DeadLetterPublishingRecoverer.excludeHeader(...)` + `ExceptionHeadersCreator` propio). El stack trace queda en el
log del consumidor, con `correlationId`.

### Reproceso (runbook del hito)

`DlqReprocessor` como herramienta de operación, documentada en el handoff: lee un rango de offsets de
`wa.message.dlq.v1`, y por cada registro **republica el valor original con su clave original** en el topic que indica
`kafka_dlt-original-topic`, agregando un header `x-dlq-reprocessed-from` con el offset de la DLQ. Reglas: no borra la
DLQ, es idempotente del lado del consumidor (misma `messageId`), y nunca reprocesa en masa un evento inválido por
esquema (eso se corrige en el productor o se descarta con un registro de la decisión).

## 11. Versionado de contratos

- Campo opcional nuevo → **misma versión**, se actualiza el esquema. Los consumidores deben tolerar que el campo
  no venga.
- Cambio incompatible (campo nuevo obligatorio, campo que se va, tipo que cambia, enum que se achica) → **topic
  nuevo** `wa.<entidad>.<hecho>.v<n+1>` y esquema `….v<n+1>.schema.json`. El productor publica en las dos versiones
  hasta que todos los consumidores migran; después se retira la vieja con aviso en el handoff.
- Invariante verificada en producción y en tests: `eventVersion` del sobre == sufijo `.v<n>` del topic ==
  versión del esquema. Nunca se rompe un contrato que alguien está consumiendo.

## 12. Observabilidad

- Actuator expone `health`, `info` y `prometheus`. `KafkaHealthIndicator` propio: `AdminClient.describeCluster()`
  con timeout corto y verificación de que existan los topics declarados.
- Logs JSON (`logging.structured.format.console: ecs` de Boot 3.4+) con `correlationId`, `conversationId`,
  `messageId` y `eventType` desde el MDC.
- Métricas: las de Micrometer de Spring Kafka (lag por grupo y partición, throughput, errores) más contadores
  propios `wa_events_published_total{topic,eventType}`, `wa_events_consumed_total{topic,eventType,result}`,
  `wa_events_invalid_total{stage,reason}`, `wa_events_retried_total{topic}`,
  `wa_events_dlq_total{originalTopic,reason}` y el timer `wa_event_processing_seconds{topic}`.
- Alertas sugeridas para el frente de runtime: lag creciente sostenido por grupo, cualquier incremento de
  `wa_events_dlq_total`, y `wa_events_invalid_total` > 0 (es siempre un bug de contrato).

## 13. Seguridad

| Riesgo | Control |
| --- | --- |
| Secretos en el repo | Ninguna credencial versionada. `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG`, `WA_CONVERSATION_ID_SALT` por variables de entorno; en el repo solo `.env.example` con los nombres |
| PII en eventos | Payload mínimo (§6). El teléfono viaja porque es el identificador del canal; no viajan nombre de perfil, datos de identidad, saldos, documentos ni el cuerpo crudo del webhook |
| PII en logs | `PhoneMasker` obligatorio: se conservan `+` + los 3 primeros dígitos y los 2 últimos, el resto `*` (`+5491122334455` → `+549*********55`). En logs y métricas se usa `conversationId`, que es opaco |
| Deserialización insegura | Valor como texto + JSON Schema estricto; prohibida la deserialización polimórfica y `trusted.packages=*` |
| Kafka fuera de local | TLS + SASL/SCRAM y ACL por topic y por grupo (tabla siguiente). **En local se corre en PLAINTEXT sobre `localhost`: queda documentado como pendiente del ambiente compartido, no se usa PLAINTEXT fuera de la PC** |
| Errores hacia afuera | Sin stack traces en DLQ ni en respuestas HTTP; el endpoint de humo vive solo en el perfil `dev` y no se habilita en ningún ambiente compartido |
| Dependencias | `./mvnw dependency:tree` revisado y OWASP Dependency-Check en CI; sin CVE altas conocidas |

ACL por principal para ambientes compartidos (cada componente solo lo suyo):

| Principal | WRITE | READ (grupo) |
| --- | --- | --- |
| `wa-twilio-gateway` | `wa.message.received.v1`, `wa.message.status.v1`, `wa.message.dlq.v1` | `wa.message.ready-to-send.v1` (`wa-twilio-gateway`) |
| `wa-channel-orchestrator` | `wa.message.ready-to-send.v1`, `wa.message.dlq.v1` | `wa.message.received.v1`, `wa.message.status.v1` (`wa-channel-orchestrator`) |
| `wa-support` (reproceso) | los tres topics de negocio | `wa.message.dlq.v1` |

## 14. Pruebas

Testcontainers con Kafka en **modo KRaft** y tag fijo, Awaitility para las esperas (nunca `sleep`), JaCoCo ≥ 80%.
Casos mínimos que exige esta arquitectura:

1. Publicar y consumir el flujo completo de los tres eventos de negocio.
2. **Orden**: N eventos de la misma conversación se consumen en el orden publicado; conversaciones distintas pueden
   intercalarse.
3. **Duplicado**: mismo `messageId` dos veces → un solo efecto; mismo registro reentregado → un solo efecto.
4. **Esquema inválido** al producir → excepción y nada publicado; al consumir → DLQ **sin reintentos**.
5. **Reintentos agotados** → DLQ con los headers de §10, sin header de stack trace.
6. **Reproceso** desde DLQ → el evento vuelve a su topic original con su clave.
7. Validación del sobre: falta un campo obligatorio, `additionalProperties`, desajuste header/sobre,
   `eventVersion` que no corresponde al topic, clave nula.
8. `PhoneMasker` y ausencia de teléfono sin enmascarar en los logs del flujo.

## 15. Lo que publica el hito (para H0 y H0.2)

En `shared/contracts/events/`: `envelope.v1.schema.json`, los tres esquemas de evento, `topics.md` (nombre,
particiones, retención, productor, consumidor de cada topic) y `README.md` con el catálogo y las reglas de
versionado. En `shared/contracts/wa-event-platform/handoff.md`: cómo producir y consumir (configuración de §7 y §8),
headers y clave, contrato de idempotencia, política de reintentos y DLQ, y el runbook de reproceso.

Lo que cada vecino tiene que cumplir, resumido:

- **H0 gateway**: genera `correlationId` y `conversationId` (ADR-0006); publica `MessageReceived` con
  `messageId = MessageSid`; confirma el webhook antes de completar el procesamiento; consume
  `MessageReadyToSend` con idempotencia por `messageId`; guarda el mapeo `messageId` ↔ `MessageSid` y publica
  `MessageStatus`.
- **H0.2 orquestador**: consume `MessageReceived` y `MessageStatus` con idempotencia por `messageId` en PostgreSQL,
  serializa por `conversationId`, publica `MessageReadyToSend` con `messageId` propio y `replyToMessageId`, y
  propaga el `correlationId` sin cambiarlo.

### ADRs

En `docs/adr/`: ADR-0001 Kafka como backbone · ADR-0002 at-least-once + idempotencia · ADR-0003 partición por
conversación · ADR-0004 contratos JSON Schema versionados sin registro ni librería compartida · ADR-0005 reintentos
bloqueantes y DLQ única · ADR-0006 `conversationId` opaco y determinístico.

## 16. Supuestos

Detalles técnicos que la especificación delegó en la arquitectura. Quedan asentados acá; si alguno se cambia, se
cambia en este documento y en el handoff.

1. **Política de reintentos**: 4 reintentos con backoff exponencial 500 ms → 8 s (5 entregas, ~7,5 s), bloqueantes.
   Elegido para que un corte breve se absorba sin romper el orden y sin retener la partición más de unos segundos.
2. **Retención**: 7 días en los topics de negocio (alcanza para reprocesar un incidente del fin de semana) y 30 días
   en la DLQ (diagnóstico y reproceso con calma). Los eventos no son el registro de verdad de nada: eso es de los
   servicios de dominio.
3. **Puerto HTTP `8092`**: las convenciones del programa asignan 8090, 8091 y 8101–8103, y no dan puerto a H0.1.
   Se toma el siguiente libre de la serie (8090 gateway, 8091 orquestador, 8092 plataforma). Configurable por
   `SERVER_PORT`; ningún otro componente depende de este puerto (es solo Actuator y el humo local). El frente del
   runtime local puede reasignarlo.
4. **Registro de esquemas**: archivos versionados en `shared/contracts/events/` validados en los dos lados, en vez
   de un Schema Registry (ADR-0004).
5. **Imagen de Kafka local**: `apache/kafka` en modo KRaft, un nodo, tag fijo. El Dockerfile y el compose son del
   frente de DevOps de este hito.
6. **Perfiles**: `local` (default en la PC), `dev`, `test` y, para ambientes compartidos, el perfil que defina el
   frente de runtime. Los `NewTopic`, el consumidor de referencia y `DevEventController` solo existen en
   `local`/`dev`/`test`.

## 17. Preguntas abiertas

Ninguna. Los datos del proyecto salieron de la especificación del hito y de las convenciones técnicas del programa
(carpeta, grupo Maven, paquete base, prefijo de topics, carpeta de contratos, puertos de los vecinos). Lo que la
especificación dejó abierto a propósito se decidió acá, en §16 y en los ADRs.
