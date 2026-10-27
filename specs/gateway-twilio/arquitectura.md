# Arquitectura — H0 Gateway WhatsApp (Twilio)

**Hito:** H0  
**Componente:** `gateway/wa-twilio-gateway` (nuevo)  
**Estado:** borrador  
**Fecha:** 2026-10-27  

## Base

- **Java 21 + Spring Boot 3.x**, Maven con wrapper propio (`./mvnw`).
- **GroupId:** `com.bcra` (`project.yaml` → `java.group_id`).
- **Base package:** `com.bcra.gateway` (propuesto; no figura en `project.yaml` bajo `integracion.gateway.base_package`. Se anota para `tech-lead`).
- **Puerto:** `8090` (según convención de stack; confirmar en `project.yaml` si existe `integracion.ports.gateway`).
- **Stack:** Spring Web, Spring for Apache Kafka, Lombok, MapStruct, Twilio Java SDK (`com.twilio.sdk:twilio`), Actuator/Micrometer, Validation, Testcontainers/WireMock/Awaitility.

## Límites

| Hace | No hace |
|---|---|
| Webhooks inbound/outbound callbacks de Twilio, validación de firma, anti-replay, ACK rápido | Vinculación/identidad (H3), sesión/credenciales (H2), comprensión de lenguaje/generación (H9) |
| Normalización a `MessageReceived`/`MessageStatus`, publicación/consumo en Kafka | Coordinación de flujos/sagas (H0.2), reglas de negocio H1–H6, payload completo de Twilio expuesto fuera |
| Envío outbound vía Twilio SDK desde `MessageReadyToSend` | Persistencia de negocio (solo idempotencia/cache local/Redis si se habilita) |

## Árbol de paquetes

```text
com.bcra.gateway/
├── GatewayApplication.java
├── web/
│   ├── TwilioWebhookController    # POST /webhook/inbound, /webhook/status
│   └── dto/                       # DTOs mínimos (solo para binding Twilio)
├── security/
│   ├── TwilioSignatureValidator   # RequestValidator con URL pública + token
│   ├── AntiReplayService          # Por MessageSid + ventana de tiempo
│   └── WebhookSecurityConfig     # Filtros/orden
├── normalization/
│   ├── MessageNormalizer          # Twilio -> MessageReceived
│   └── StatusNormalizer           # Twilio -> MessageStatus
├── messaging/
│   ├── producer/
│   │   ├── InboundEventPublisher  # Publica MessageReceived a wa.message.received.v1
│   │   └── StatusEventPublisher   # Publica MessageStatus a wa.message.status.v1
│   ├── consumer/
│   │   └── OutboundEventConsumer  # Consume wa.message.ready-to-send.v1
│   └── config/                    # Kafka producer/consumer (idempotencia, acks, ackMode)
├── client/
│   └── TwilioClient              # Envío vía SDK, reintentos, mapeo errores
├── service/
│   └── OutboundSendService       # Idempotencia de envío, trazabilidad
├── config/
│   ├── KafkaConfig, TwilioConfig
│   └── ObservabilityConfig
└── observability/
    ├── CorrelationIdFilter       # Genera/propaga correlationId, MDC
    └── WebhookMetrics            # Métricas ACK/errores/envío
```

## Contratos y topics

Sigue `shared/contracts/events/*` y `int-event-contracts.md`:

| Topic | Productor | Consumidor | Evento | Notas |
|---|---|---|---|---|
| `wa.message.received.v1` | H0 | H0.2 | `MessageReceived` | `conversationId` = clave; headers `correlationId,eventType,eventVersion` |
| `wa.message.ready-to-send.v1` | H0.2 | H0 | `MessageReadyToSend` | `customerAddress`, `body` mínimos |
| `wa.message.status.v1` | H0 | H0.2/obs | `MessageStatus` | Callbacks de estado |
| `wa.message.dlq.v1` | infra | soporte | `DeadLetter` | Según política H0.1 |

Validación: productor valida antes de enviar; lo inválido no se publica. Headers obligatorios propagados. `correlationId` generado si falta (UUID) y propagado punta a punta.

## Flujo

### Inbound (webhook)
1. Recibe POST `application/x-www-form-urlencoded` (form de Twilio). 
2. **Validación de firma** con `RequestValidator` usando URL pública exacta (`TWILIO_WEBHOOK_PUBLIC_URL` o reconstruida) y `TWILIO_AUTH_TOKEN`. Si falla → 403, sin publicar.
3. **Anti-replay**: clave por `MessageSid` + ventana temporal (ej. 300s). Si duplicado conocido y ya procesado → responder ACK sin republicar (idempotente). Si sospechoso fuera de ventana → rechazar según política.
4. **Normalización**: extraer `MessageSid`, `From`, `To`, `Body`, `NumMedia`, etc. Generar `conversationId` (estrategia: derivado estable, p.ej. hash normalizado de `From`/canal; definir y documentar en código/spec). Generar/propagar `correlationId` (UUID). Construir `MessageReceived` con `messageId = MessageSid` (o estable).
5. **Publicación**: validar esquema, publicar a `wa.message.received.v1` con clave `conversationId` y headers. 
6. **ACK rápido**: responder 200/2xx a Twilio **antes** de esperar procesamiento downstream completo (async tras validación/normalización/publicación). 

### Status callback
1. Validar firma + anti-replay.
2. Normalizar a `MessageStatus` (incluir `messageId` relacionado).
3. Publicar a `wa.message.status.v1`, ACK rápido.

### Outbound
1. Consumir `MessageReadyToSend` (clave `conversationId`, `ackMode` manual tras efecto).
2. **Idempotencia de envío**: clave por `messageId`/`conversationId` + hash de destino/cuerpo (evitar reenvío duplicado en reentregas). 
3. Enviar vía `TwilioClient` (SDK). Registrar resultado. 
4. Confirmar ACK solo tras intento exitoso o tras decisión no reintentable según política.

## Idempotencia y orden
- **Inbound**: `MessageSid` (tabla/cache con TTL) + ventana anti-replay.
- **Outbound**: deduplicación por `messageId` de evento + clave de envío; SDK/Twilio puede reintentar.
- **Orden**: partición por `conversationId` respetada; consumidor con concurrencia acorde a particiones, sin procesamiento paralelo dentro de partición.

## Seguridad
- **Firma**: obligatoria. Nunca publicar si inválida.
- **Secretos**: solo variables de entorno (`TWILIO_AUTH_TOKEN`, `TWILIO_ACCOUNT_SID`, `TWILIO_WEBHOOK_PUBLIC_URL`). Sin commitear.
- **PII**: logs sin cuerpo completo ni números completos; enmascarar teléfonos. Eventos con mínimos (`customerAddress` enmascarado o formato acordado, `body` mínimo necesario).
- **Límites**: tamaño máximo de body/form, rate limit por IP/origen si aplica.
- **Errores**: sin stack traces hacia Twilio/cliente.
- **Kafka**: TLS+SASL fuera de local (documentado).

## Observabilidad
- `correlationId` en MDC y headers, propagado extremo a extremo.
- Métricas: webhook ACK latencia, validación fallida, publicados/inbound, consumidos outbound, enviados Twilio, errores, reintentos.
- Logs JSON con `correlationId`, sin PII/secreto.

## Configuración (variables)
| Variable | Requerida | Descripción |
|---|---|---|
| `SERVER_PORT` | no | default `8090` |
| `TWILIO_ACCOUNT_SID` | sí | Cuenta Twilio |
| `TWILIO_AUTH_TOKEN` | sí | Token para validar firma/enviar |
| `TWILIO_WEBHOOK_PUBLIC_URL` | sí | URL pública exacta usada por `RequestValidator` |
| `KAFKA_BOOTSTRAP_SERVERS` | sí | Bootstrap Kafka |
| `KAFKA_PRODUCER_*`, `KAFKA_CONSUMER_*` | según | Idempotencia/acks/ackMode |
| `WEBHOOK_ANTI_REPLAY_WINDOW_SECONDS` | no | default 300 |
| `WEBHOOK_MAX_BODY_SIZE` | no | límite |

## ADRs requeridos
- `docs/adr/ADR-004-twilio-encapsulado-detras-del-gateway.md`
- `docs/adr/ADR-005-webhook-confirma-antes-de-procesamiento.md`

## Supuestos
- `conversationId` se derivará de `From` (normalizado) para el piloto; se documentará y podrá ajustarse si H0.2 requiere otra estrategia.
- Anti-replay con store en memoria + TTL para piloto local; si se requiere persistente entre réplicas, migrar a Redis (fuera de alcance actual).
- URL pública de webhook debe configurarse correctamente en Twilio para validar firma.
- H0.1 ya provee topics/esquemas/producer-consumer base.

## Preguntas abiertas
- ¿Estrategia exacta para generar `conversationId` (¿usar `From` completo o parte, incluir `To`/canal)?
- ¿Ventana anti-replay definitiva y store (in-mem vs Redis)?
- ¿Timeout objetivo para ACK de webhook?
- ¿Clave exacta de idempotencia para reintentos de Twilio?
- ¿Formato de enmascarado de teléfonos en logs?
