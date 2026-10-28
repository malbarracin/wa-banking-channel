# Handoff H0.2 — wa-channel-orchestrator

Fecha: 2026-10-28. Para H0, H9/H7, QA y operación.
Fuentes: [spec R-001–R-006](../../../specs/orquestador-canal/spec.md),
[arquitectura](../../../specs/orquestador-canal/arquitectura.md) e implementación del componente.

## Topics y contratos

| Topic | Uso actual H0.2 | Schema |
|---|---|---|
| wa.message.received.v1 | Consume desde H0 | [MessageReceived](../events/wa.message.received.v1.schema.json) |
| wa.message.ready-to-send.v1 | Produce hacia H0 | [MessageReadyToSend](../events/wa.message.ready-to-send.v1.schema.json) |
| wa.message.status.v1 | Publica H0; consumidor H0.2 pendiente | [MessageStatus](../events/wa.message.status.v1.schema.json) |
| wa.message.dlq.v1 | Recuperación inbound/outbox hacia soporte | [DeadLetter](../events/wa.message.dlq.v1.schema.json) |

Los nombres efectivos de schema son `wa.message.*`, aunque la spec usa nombres conceptuales.
Clave UTF-8 conversationId; headers correlationId/eventType/eventVersion coinciden con el sobre.
DLQ agrega únicamente errorCode. [Topics](../events/topics.md): tres particiones locales;
retención received/ready-to-send 7 días, status 30 días, DLQ 90 días. No cambiar particiones sin migración.
Productor idempotente, acks=all y máximo cinco requests en vuelo; entrega at-least-once.

Inbound valida schema/formato/headers/clave antes de deduplicar globalmente por messageId.
Grupo wa-channel-orchestrator, concurrencia 3, auto commit desactivado, ACK MANUAL_IMMEDIATE
tras commit. Procesamiento secuencial por partición incluso durante retry.
Efectos mediante listener síncrono de InboundMessage en la misma TX de deduplicación,
sin @Async ni AFTER_COMMIT. Rollback permite reentrega, duplicado confirmado no repite efectos.
ConversationService carga INITIAL o recupera estado y actualiza último mensaje/correlación con versión optimista.
OutboundEventPublisher exige TX MANDATORY y messageId saliente estable; dispatcher lee filas confirmadas,
bloquea la más antigua hasta ACK/commit y preserva orden serial global. Un retry frena las siguientes filas.
Una caída posterior al ACK puede reenviar eventId/messageId: H0 debe deduplicar.
Conversación/outbox limitan identificadores a 128 caracteres; inbound acepta hasta 256 según schema.

## H9 y límites actuales

`shared/contracts/wa-conversation-agent/turn.md` sigue pendiente. La definición provisional implementada
está en [H9.md](../../../platform/wa-channel-orchestrator/H9.md): POST /api/v1/agent/turn,
Bearer de servicio y X-Correlation-Id/X-Conversation-Id/X-Message-Id/X-Idempotency-Key=messageId.
Request: identificadores, customerAddress, body, context (technicalContext/history).
Respuesta 200: identificadores, reply.body, escalate, toolsUsed; se verifica correspondencia del turno.
H9 debe garantizar deduplicación siete días, resultado cacheado y herramientas como máximo una vez
antes de habilitar retries reales. H9TurnClient.turn es síncrono: reintenta TIMEOUT/UNAVAILABLE
(transporte/5xx), conserva request/clave; 4xx incluido 409/429 → REJECTED sin retry.
Otras razones seguras: INVALID_RESPONSE y NOT_CONFIGURED. No publica contingencia por sí solo.

Falta conectar los puertos con un handler de turno de producción: el consumidor aislado registra
deduplicación sin invocar H9 ni responder. También faltan señales H9/H7 y adaptadores de compensación H3/H2.
El orquestador no interpreta texto, implementa herramientas ni duplica reglas H1–H6.

## Configuración

Exportar [.env.example](../../../platform/wa-channel-orchestrator/.env.example) al entorno;
no se carga automáticamente. Fuente: application.yml y application-local.yml.

| Variable | Default / función |
|---|---|
| SERVER_PORT | 8091 |
| SPRING_PROFILES_ACTIVE | Sin default; ejemplo local fuerza PLAINTEXT |
| SPRING_DATASOURCE_URL | jdbc:postgresql://localhost:5432/wa_orchestrator |
| SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD | Obligatorios, sin default |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 |
| KAFKA_SECURITY_PROTOCOL | PLAINTEXT solo local |
| H9_BASE_URL | http://localhost:8099; HTTPS fuera de local |
| H9_SERVICE_TOKEN | Vacío; sin token se rechaza la llamada antes de enviar |
| H9_CONNECT_TIMEOUT_MS | 1000 |
| H9_TIMEOUT_MS | 5000 por intento completo |
| H9_MAX_RETRIES | 2 (rango 0–10), hasta 3 intentos por defecto |
| H9_BACKOFF_MS | 200 exponencial, tope 16 veces inicial |
| ORCHESTRATOR_SAGA_STEP_TIMEOUT | PT24H, ISO-8601 |
| SAGA_TIMEOUT_CHECK_INTERVAL_MS | 60000 |
| OUTBOX_ENABLED | true; false detiene scheduler sin borrar pendientes |
| OUTBOX_POLL_INTERVAL_MS | 250 |
| OUTBOX_MAX_ATTEMPTS | 3 envíos outbound |
| OUTBOX_BACKOFF_MS | 1000, exponencial con tope 60000 ms |
| OUTBOX_SEND_TIMEOUT_MS | 10000 |

Retry inbound: propiedades orchestrator.kafka.retry.max-retries=3, initial-interval=1000,
max-interval=10000 (ms) bajo ese mismo prefijo, ajustables por configuración externa Spring.
InvalidEventException/ValidationException no reintentan; fallo de publicación DLQ no confirma recuperación.
Al cambiar tiempos revisar presupuesto completo del turno frente a max.poll.interval.ms.

## Operación y seguridad

Flyway V1–V7 administra conversation_state, saga_instance, saga_step_execution, message_dedupe y outbox;
Hibernate validate. No guardar credenciales/conversación en contexto técnico.
Outbox contiene texto/destinatario mínimos: restringir acceso y definir retención operativa.
ONBOARDING: LINK → ISSUE_CREDENTIAL; REVERIFICATION: VERIFY_IDENTITY → ISSUE_CREDENTIAL.
WAITING permite continuar tras reinicio; fallo/timeout compensa en orden inverso incluido paso incierto.
Confirmación completa → COMPENSATED; adaptador ausente o rechazo → FAILED/SUPPORT_REQUIRED.
Consultar saga_step_execution.result_code='SUPPORT_REQUIRED'; scheduler/señales comparten bloqueo de escritura.
Ver [SAGAS.md](../../../platform/wa-channel-orchestrator/SAGAS.md).

Correlación en Kafka, HTTP H9 y MDC restaurado al salir. Logs ECS; no cuerpos/teléfonos/tokens ni trazas HTTP/Kafka.
Actuator health/info/prometheus en 8091, acceso restringido. Métricas:
orchestrator.kafka.dlq.published, orchestrator.outbox.published, orchestrator.outbox.failures,
orchestrator.outbox.dlq. Vigilar lag, pendientes antiguos y SUPPORT_REQUIRED; sin SLA definido en esta tarea.
Fuera de local quitar perfil local y suministrar TLS/SASL, certificados y secretos vía propiedades Spring externas.
ACL H0.2: consumir received con su grupo, producir ready-to-send/DLQ; soporte con permisos separados.

Reproceso: [DLQ.md](../../../platform/wa-channel-orchestrator/DLQ.md), rutas distintas inbound/outbox.
Secuencias y contingencias: [FLUJOS.md](../../../platform/wa-channel-orchestrator/FLUJOS.md);
máquinas de estado: [SAGAS.md](../../../platform/wa-channel-orchestrator/SAGAS.md).
Arranque y verificación: [README](../../../platform/wa-channel-orchestrator/README.md).
verify_integracion.py descubre el POM automáticamente, ejecuta wrapper verify y JaCoCo ≥80%.
Tests integration requieren Docker; su omisión no certifica conectividad real.
