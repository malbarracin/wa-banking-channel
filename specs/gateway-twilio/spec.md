# Especificación — H0 Gateway WhatsApp (Twilio)

## Propósito
El Gateway WhatsApp (Twilio) es el borde del canal. Es la única pieza que conoce el contrato de Twilio. Recibe webhooks inbound, valida su autenticidad, normaliza el payload externo a un evento interno versionado (`MessageReceived`) y lo publica en Kafka. Consume eventos outbound (`MessageReadyToSend`) y los envía a Twilio. No contiene lógica conversacional ni de negocio.

## Fuera de alcance
- Vinculación/verificación de identidad (H3)
- Gestión de sesión o credenciales (H2)
- Comprensión del lenguaje y generación de respuestas (H9)
- Coordinación de flujos (H0.2)
- Payload completo de Twilio expuesto al resto del sistema (aislado por contrato interno)

## Decisiones de arquitectura (del hito)
- Twilio no invoca directamente al agente ni a los servicios de negocio.
- El Gateway responde rápidamente al webhook (ACK) y desacopla el procesamiento mediante Kafka.
- El Gateway no decide intenciones, no mantiene el flujo de conversación y no ejecuta lógica de H1–H9.
- El webhook se confirma antes de completar el procesamiento de negocio (ADR requerido).

## Contratos y topics
**Topics (H0.1):**
| Topic | Productor | Consumidor | Notas |
|-------|-----------|------------|-------|
| `wa.message.received.v1` | H0 (Gateway) | H0.2 (Orquestador) | Evento normalizado `MessageReceived` |
| `wa.message.ready-to-send.v1` | H0.2 (Orquestador) | H0 (Gateway) | Evento outbound `MessageReadyToSend` |
| `wa.message.status.v1` | H0 (Gateway) | H0.2 / observabilidad | Callbacks de estado de Twilio |
| `wa.message.dlq.v1` | infraestructura | soporte técnico | Errores no recuperables |

**Contratos relacionados:**
- `shared/contracts/events/MessageReceived.v1.schema.json` (producido/consumido con H0.1)
- `shared/contracts/events/MessageReadyToSend.v1.schema.json` (producido por H0.2, consumido por H0)
- `shared/contracts/events/MessageStatus.v1.schema.json` (producido por H0)
- `shared/contracts/wa-twilio-gateway/handoff.md`
- `shared/contracts/wa-channel-orchestrator/handoff.md` (consumido por H9)

**Trazabilidad:** `correlationId` extremo a extremo (Twilio → H0 → Kafka → H0.2 → H9 → Kafka → H0 → Twilio), en headers y MDC. Clave de partición = `conversationId`.

## Requisitos

### R-001 Webhook inbound
- **Descripción:** Endpoint público para recibir webhooks de Twilio.
- **Escenarios:**
  - Dado webhook con firma válida → Cuando se recibe → Entonces se valida firma, normaliza a `MessageReceived`, publica en `wa.message.received.v1` y responde ACK rápido (dentro del timeout exigido por Twilio).
  - Dado webhook con firma inválida → Cuando se recibe → Entonces se rechaza sin publicar eventos (HTTP 403/401 según política) y se registra sin PII sensible.
  - Dado reintento de Twilio (mismo webhook) → Cuando se recibe → Entonces debe ser idempotente (no generar mensajes duplicados). Usar `messageId`/deduplicación por claves conocidas.

### R-002 Normalización
- **Descripción:** Aislar payload de Twilio detrás de contrato interno versionado.
- **Escenarios:**
  - Dado payload Twilio válido → Cuando se normaliza → Entonces se produce `MessageReceived.v1` con `messageId`, `conversationId`, `correlationId`, `from`, `to`, `channel`, `content`, `timestamp`, `metadata` mínimos (sin PII innecesaria).
  - Dado payload incompleto/inválido → Cuando se normaliza → Entonces se rechaza o se deriva error controlado sin filtrar datos sensibles.

### R-003 Publicación inbound
- **Descripción:** Publicar evento normalizado en Kafka con trazabilidad.
- **Escenarios:**
  - Dado mensaje normalizado válido → Cuando se publica → Entonces se envía a `wa.message.received.v1` con headers (`correlationId`, `eventType`, `eventVersion`) y clave `conversationId` (partición por conversación).
  - Dado fallo transitorio al publicar → Cuando ocurre → Entonces aplicar reintentos con backoff; si no recuperable → enrutar a DLQ según política H0.1.

### R-004 Callbacks de estado
- **Descripción:** Recibir callbacks de estado de Twilio.
- **Escenarios:**
  - Dado callback de estado válido → Cuando se recibe → Entonces se normaliza a `MessageStatus` y se publica en `wa.message.status.v1` con trazabilidad.
  - Dado callback con firma inválida/replay → Cuando se recibe → Entonces se rechaza sin efectos.

### R-005 Consumo outbound y envío
- **Descripción:** Consumir `MessageReadyToSend` y enviar respuesta vía Twilio.
- **Escenarios:**
  - Dado evento `MessageReadyToSend` válido → Cuando se consume → Entonces se envía a Twilio con idempotencia (evitar reenvío duplicado), se registra resultado y se actualiza trazabilidad.
  - Dado fallo al enviar (timeout/error Twilio) → Cuando ocurre → Entonces aplicar política de reintentos/DLQ según H0.1; no bloquear procesamiento de otros mensajes de la misma conversación indebidamente.

### R-006 Seguridad y anti-replay
- **Descripción:** Validación de firma, límites de entrada, protección contra replay.
- **Escenarios:**
  - Dado webhook sin/firma inválida → Rechazo sin publicar.
  - Dado replay sospechoso → Aplicar protección (nonce/timestamp/window) según configuración; rechazar o deduplicar según política.

### R-007 Observabilidad
- **Descripción:** Trazabilidad, métricas, logs seguros.
- **Escenarios:**
  - Todo evento/log debe incluir `correlationId` cuando disponible.
  - Métricas de publicación/consumo/envío y errores.
  - Logs sin PII/secreto; teléfonos enmascarados.

## Criterios de aceptación del hito
- [ ] Un webhook con firma inválida se rechaza sin publicar eventos.
- [ ] Un webhook válido se confirma dentro del timeout exigido por Twilio.
- [ ] Reintentos de Twilio no generan mensajes duplicados.
- [ ] El payload de Twilio queda aislado del resto del sistema por un contrato interno.
- [ ] Cada mensaje puede trazarse extremo a extremo mediante `correlationId`.
- [ ] Un evento outbound válido se envía a Twilio y su resultado queda registrado.

## Requisitos no funcionales
- **Timeout Twilio:** Responder ACK rápidamente antes de procesamiento pesado (webhook se confirma antes de completar procesamiento de negocio).
- **Entrega:** at-least-once; consumidores idempotentes.
- **Orden:** por `conversationId` (partición).
- **Trazabilidad:** `correlationId` extremo a extremo.
- **Seguridad:** firma Twilio, anti-replay, límites, sin secretos/PII innecesaria en eventos/logs.
- **Resiliencia:** reintentos, DLQ según política H0.1.

## ADRs requeridos
- ADR — Twilio queda encapsulado detrás de un Gateway de canal.
- ADR — El webhook se confirma antes de completar el procesamiento de negocio.

## Supuestos
- H0.1 provee topics, esquemas versionados, productor/consumidor con validación/reintentos/DLQ.
- Twilio provee firma para validación y webhooks de estado.
- Clave de partición es `conversationId`.

## Preguntas abiertas
- [ ] ¿Ventana/estrategia exacta de anti-replay (timestamp window + nonce store)? ¿Dónde se persiste (Redis/in-mem/local)?
- [ ] ¿Timeout objetivo para ACK de webhook (ms) para cumplir con Twilio en entorno local?
- [ ] ¿Política exacta de idempotencia para reintentos de Twilio (clave: MessageSid + event type)?
- [ ] ¿Formato exacto de enmascarado de teléfonos en logs?
