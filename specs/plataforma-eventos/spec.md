---
title: Plataforma de Eventos (Kafka) — especificación
status: aprobada
source: null
updated: 2026-10-27T19:27:33.951Z
---

# Plataforma de Eventos (Kafka) — especificación

## Propósito

La Plataforma de Eventos (H0.1) provee la columna vertebral asíncrona del canal WhatsApp mediante Kafka: desacopla ingreso (H0 Gateway), orquestación (H0.2 Orquestador) y salida (H0 Gateway). Absorbe picos, permite reintentos controlados y garantiza trazabilidad sin bloquear a Twilio.

## Fuera de alcance (de la fuente)

- Lógica de negocio o conversación
- Uso de Kafka como base de datos
- Exponer topics directamente a Twilio

## Decisiones de arquitectura

- Kafka como backbone asíncrono del canal
- Entrega at-least-once; todos los consumidores deben ser idempotentes
- Partición por `conversationId` para preservar el orden dentro de una conversación
- Eventos como contratos versionados; sin secretos ni PII innecesaria
- `correlationId` de punta a punta (Twilio → H0 → Kafka → H0.2 → H9 → Kafka → H0 → Twilio), en headers y en MDC
- El webhook se confirma antes de completar el procesamiento de negocio (ADR del roadmap)

## Topics iniciales del piloto

| Topic | Productor | Consumidor | Propósito |
| --- | --- | --- | --- |
| `wa.message.received.v1` | H0 Gateway | H0.2 Orquestador | Mensaje inbound validado y normalizado |
| `wa.message.ready-to-send.v1` | H0.2 Orquestador | H0 Gateway | Respuesta final lista para Twilio |
| `wa.message.status.v1` | H0 Gateway | H0.2 / observabilidad | Estado de entrega informado por Twilio |
| `wa.message.dlq.v1` | Infraestructura | Soporte técnico | Mensajes agotados para diagnóstico/reproceso |

## Requisitos

### Requisito: Convenciones de topics, claves y retención
El sistema DEBE definir convenciones de nombres de topic, clave de partición (`conversationId`), política de retención y versionado de esquemas.

#### Escenario: Partición por conversación
- **DADO** mensajes pertenecientes a la misma conversación
- **CUANDO** se publican a un topic particionado
- **ENTONCES** todos los mensajes de esa conversación deben ir a la misma partición
- **Y** el orden dentro de la conversación se preserva

#### Escenario: Retención configurable
- **DADO** un topic configurado con política de retención
- **CUANDO** los mensajes exceden el tiempo/espacio configurado
- **ENTONCES** el sistema aplica la política de retención definida

### Requisito: Contratos versionados y validados
Los eventos DEBEN tener esquemas versionados en `shared/contracts/events/` y DEBEN validarse al publicar/consumir según corresponda.

#### Escenario: Evento con esquema válido
- **DADO** un evento que cumple su esquema versionado
- **CUANDO** se publica o consume
- **ENTONCES** la validación pasa

#### Escenario: Evento con esquema inválido
- **DADO** un evento que no cumple su esquema versionado
- **CUANDO** se intenta publicar/consumir
- **ENTONCES** la validación falla y se sigue la política de error/reintento definida

### Requisito: Semántica at-least-once e idempotencia
La plataforma DEBE operar en semántica at-least-once; todos los consumidores DEBEN ser idempotentes.

#### Escenario: Reentrega no duplica efectos
- **DADO** un mensaje entregado y procesado exitosamente
- **CUANDO** Kafka reenvía el mismo mensaje (reentrega)
- **ENTONCES** el consumidor detecta la duplicidad (por clave de idempotencia) y no duplica efectos
- **Y** registra el evento de duplicado cuando corresponda

### Requisito: Retry, DLQ y reproceso
El sistema DEBE implementar retry con backoff y política de reintentos; los mensajes no procesables DEBEN ir a `wa.message.dlq.v1`.

#### Escenario: Agotamiento de reintentos
- **DADO** un mensaje que falla consistentemente tras agotar la política de reintentos
- **CUANDO** se alcanza el máximo de reintentos
- **ENTONCES** el mensaje se envía a `wa.message.dlq.v1` con metadata para diagnóstico/reproceso
- **Y** no se pierde el mensaje

#### Escenario: Reproceso desde DLQ
- **DADO** un mensaje en DLQ con causa corregida
- **CUANDO** se reprocesa
- **ENTONCES** el sistema permite reprocesarlo de forma controlada con trazabilidad

### Requisito: Observabilidad y trazabilidad
El sistema DEBE exponer métricas (lag, throughput, errores), trazas y logs con `correlationId` en headers y MDC.

#### Escenario: Trazabilidad de punta a punta
- **DADO** un mensaje inbound con `correlationId`
- **CUANDO** fluye por H0 → Kafka → H0.2 → H9 → Kafka → H0 → Twilio
- **ENTONCES** el `correlationId` se propaga en headers y aparece en MDC/logs de todos los componentes
- **Y** es observable en métricas/trazas

### Requisito: Seguridad
La plataforma DEBE asegurar la comunicación entre productores/consumidores y cifrado en tránsito. Los eventos NO DEBEN contener secretos ni PII innecesaria.

#### Escenario: Evento sin PII innecesaria
- **DADO** un evento publicado
- **CUANDO** se inspecciona su contenido
- **ENTONCES** no contiene secretos ni PII innecesaria
- **Y** cumple las políticas transversales

## Criterios de aceptación

- [ ] Un mensaje conserva orden respecto de otros mensajes de la misma conversación (partición por `conversationId`).
- [ ] Reentregas no duplican efectos en consumidores (idempotencia + clave de idempotencia).
- [ ] Un evento no procesable termina en DLQ tras la política de reintentos.
- [ ] Los contratos son versionados y validados (esquemas en `shared/contracts/events/`).
- [ ] Se observan lag, errores y trazabilidad por `correlationId`.
- [ ] Comunicación segura entre productores/consumidores con cifrado en tránsito.

## Requisitos no funcionales

- Timeout de Twilio considerado en el diseño de ACK/webhook (coordinado con H0)
- Throughput/latencia adecuados para piloto
- Trazabilidad con `correlationId`
- Seguridad entre componentes

## ADRs requeridos

- ADR — Kafka como backbone asíncrono del canal
- ADR — At-least-once + idempotencia como semántica de entrega
- ADR — Partición por conversación para preservar orden

## Dependencias

- Provee integración asíncrona para H0 Gateway y H0.2 Orquestador
- Los contratos se publican en `shared/contracts/events/`

## Supuestos

- Se usará Kafka en modo KRaft para piloto
- Los consumidores implementan idempotencia con una clave de idempotencia acordada
- La retención se define por topic según necesidades del piloto

## Preguntas abiertas

- Retención exacta por topic (tiempo/bytes) para el piloto
- Política exacta de reintentos (max intentos, backoff) y cuándo enviar a DLQ
- Mecanismo de registro/validación de esquemas (Schema Registry o equivalente)
- Configuración de seguridad entre productores/consumidores para el piloto
- Estrategia concreta de reproceso desde DLQ (automatizado/manual)
