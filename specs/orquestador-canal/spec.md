---
title: Orquestador del Canal (H0.2) — especificación
status: borrador
source: https://app.notion.com/p/H0-2-Orquestador-del-Canal-3a675bb08ca7812c9b8bed1723e9206f
updated: 2026-10-28T10:54:46.7645847+01:00
---

# Orquestador del Canal (H0.2) — especificación

## Propósito

El Orquestador del Canal coordina el ciclo de vida técnico y los flujos multi-componente de cada interacción. Consume mensajes normalizados desde Kafka, recupera el contexto de conversación, entrega el turno a H9, coordina resultados y publica la respuesta final. No reemplaza la lógica de negocio de H1–H6 ni el razonamiento conversacional de H9.

## Fuera de alcance

- Adaptación al payload de Twilio (H0).
- Transporte Kafka (H0.1).
- Reglas de vínculo e identidad (H3), sesión (H2) o productos (H4–H6).
- Comprensión de lenguaje o redacción de respuestas (H9).
- Implementar tools MCP (H7).

## Decisiones de arquitectura

- Separar la orquestación de proceso de la conversación y de los dominios.
- H9 entiende intención, decide cómo responder y usa capacidades H7; el Orquestador controla estado, correlación, timeout, retry y ciclo inbound/outbound.
- Los servicios H1–H6 conservan sus invariantes; el Orquestador no duplica reglas de negocio.
- Flujos multi-paso persistentes se modelan como máquina de estados/saga con compensaciones explícitas.
- Entrega at-least-once; consumidores idempotentes. Clave de partición = conversationId para preservar orden por conversación.
- correlationId de punta a punta (Twilio → H0 → Kafka → H0.2 → H9 → Kafka → H0 → Twilio), en headers y en MDC.

## Contratos y topics

**Topics (H0.1):**
| Topic | Productor | Consumidor | Propósito |
|-------|-----------|------------|-----------|
| wa.message.received.v1 | H0 Gateway | H0.2 Orquestador | Mensaje inbound validado y normalizado (MessageReceived) |
| wa.message.ready-to-send.v1 | H0.2 Orquestador | H0 Gateway | Respuesta final lista para Twilio (MessageReadyToSend) |
| wa.message.status.v1 | H0 Gateway | H0.2 / observabilidad | Estado de entrega informado por Twilio |

**Contratos relacionados:**
- shared/contracts/events/MessageReceived.v1.schema.json
- shared/contracts/events/MessageReadyToSend.v1.schema.json
- shared/contracts/events/MessageStatus.v1.schema.json
- shared/contracts/wa-conversation-agent/turn.md (contrato H0.2 ↔ H9)
- shared/contracts/wa-channel-orchestrator/handoff.md
- shared/contracts/wa-twilio-gateway/handoff.md

## Requisitos

### R-001 Consumo y deduplicación
- **Descripción:** Consumir wa.message.received.v1 con deduplicación por messageId y serialización por conversationId.
- **Escenarios:**
  - Dado un MessageReceived nuevo → Cuando se consume → Entonces se registra el procesamiento por messageId (idempotencia) y se procesa una sola vez a efectos de negocio.
  - Dado una reentrega (mismo messageId) → Cuando se consume → Entonces se detecta duplicado y no se reprocesa; se registra en trazabilidad.
  - Dado mensajes de la misma conversación → Cuando se procesan → Entonces se procesan en orden por conversationId (partición/clave).

### R-002 Contexto y estado de conversación
- **Descripción:** Cargar/persistir estado mínimo y contexto técnico de la conversación.
- **Escenarios:**
  - Dado un mensaje inbound → Cuando inicia el procesamiento → Entonces se carga el estado de conversación/correlación existente o se inicializa uno nuevo.
  - Dado estado persistido → Cuando hay reintento/pausa → Entonces el estado permite reanudar sin pérdida.

### R-003 Invocación a H9 y coordinación
- **Descripción:** Entregar el turno a H9 según contrato shared/contracts/wa-conversation-agent/turn.md, coordinar timeouts/reintentos y resultados.
- **Escenarios:**
  - Dado turno válido → Cuando se invoca a H9 → Entonces se respeta el contrato de turno (headers/campos) con correlationId.
  - Dado timeout de H9 → Cuando expira → Entonces se aplica política de reintentos o derivación controlada (sin dejar flujo en estado desconocido).
  - Dado respuesta de H9 con solicitud de capacidades → Cuando H9 usa H7/MCP → Entonces el Orquestador coordina la llamada según contrato, con trazabilidad y timeouts.

### R-004 Flujos multi-paso (sagas)
- **Descripción:** Coordinar flujos como onboarding y re-verificación con máquina de estados/saga, pausas, reanudación y compensaciones.
- **Escenarios:**
  - Dado flujo de onboarding → Cuando requiere pasos con otros componentes (H3/H2) → Entonces avanza por estados definidos, persiste estado y puede pausar/reanudar.
  - Dado fallo en un paso → Cuando ocurre → Entonces se aplica compensación explícita o se deriva a soporte/DLQ con trazabilidad.
  - Dado re-verificación → Cuando se requiere → Entonces se coordina respetando límites con H3/H2 sin duplicar reglas.

### R-005 Publicación outbound
- **Descripción:** Publicar wa.message.ready-to-send.v1 hacia H0 Gateway con trazabilidad y sin PII innecesaria.
- **Escenarios:**
  - Dado respuesta lista → Cuando se publica → Entonces se envía a wa.message.ready-to-send.v1 con headers (correlationId, eventType, eventVersion) y clave conversationId.
  - Dado fallo al publicar → Cuando ocurre → Entonces aplicar reintentos/DLQ según H0.1.

### R-006 Trazabilidad, resiliencia y DLQ
- **Descripción:** CorrelationId extremo a extremo, auditoría, manejo de fallos, derivación a DLQ/soporte.
- **Escenarios:**
  - Todo procesamiento debe propagar correlationId en headers/MDC.
  - Mensajes agotados tras reintentos → Enviar a wa.message.dlq.v1 con metadata para diagnóstico/reproceso.
  - Estado desconocido debe evitarse mediante timeouts/compensaciones y logging trazable.

## Criterios de aceptación

- [ ] Cada mensaje pasa una sola vez a efectos de negocio pese a reentregas (deduplicación por messageId).
- [ ] Se preserva el orden por conversación (conversationId).
- [ ] Timeout o falla parcial no deja el flujo en estado desconocido (timeouts, reintentos, compensaciones).
- [ ] El onboarding puede pausarse y continuar sin perder estado.
- [ ] Toda interacción se traza con correlationId desde Gateway hasta respuesta.
- [ ] No existe lógica de negocio duplicada respecto de H1–H9.
- [ ] La invocación a H9 respeta el contrato de turno y se coordina con trazabilidad.

## Requisitos no funcionales

- Timeout de Twilio considerado (coordinado con H0).
- Entrega at-least-once; consumidores idempotentes.
- Orden por conversationId.
- Trazabilidad con correlationId extremo a extremo.
- Seguridad: sin secretos/PII innecesaria en eventos/logs.
- Resiliencia: reintentos, sagas con compensaciones, DLQ según política H0.1.

## ADRs requeridos

- ADR — Orquestador dedicado para el ciclo de vida del canal.
- ADR — Estado de conversación y saga persistente.
- ADR — Límite entre orquestación de proceso (H0.2) y conversación (H9).

## Dependencias

- H0.1 Kafka: entrada/salida asíncrona.
- H9 Agente: conversación, intención y respuesta (contrato de turno).
- H7 MCP: acceso estructurado a capacidades.
- H3/H2: flujos de vínculo, identidad y sesión.

## Supuestos

- H0.1 provee topics, esquemas versionados, productor/consumidor con validación/reintentos/DLQ.
- H9 expone contrato de turno en shared/contracts/wa-conversation-agent/turn.md.
- Clave de partición es conversationId. messageId usado para deduplicación.
- Estados de saga persistidos con trazabilidad.

## Preguntas abiertas

- [ ] ¿Clave exacta de deduplicación (¿solo messageId o combinación con conversationId/source)? ¿Dónde se persiste el store de deduplicación?
- [ ] ¿Política exacta de timeouts/reintentos para invocación a H9 (valores por entorno)?
- [ ] ¿Estados mínimos de la saga para onboarding/re-verificación (máquina de estados) y reglas de compensación?
- [ ] ¿Cuándo derivar a DLQ vs reintento vs derivación a soporte (criterios)?
- [ ] ¿Alcance del "contexto técnico" persistido (qué campos, TTL)?
