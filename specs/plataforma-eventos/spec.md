---
title: Especificación — Plataforma de Eventos (Kafka)
status: aprobada
source: null
updated: 2026-11-21T03:53:57.457Z
---

# Especificación — Plataforma de Eventos (Kafka)

## Propósito

Proveedor de la columna vertebral asíncrona del canal WhatsApp bancario: topics con prefijo `wa.` y forma
`wa.<entidad>.<hecho>.v<n>`, esquemas versionados, partición por `conversationId` para preservar el orden,
reintentos, DLQ y observabilidad. Desacopla ingreso, orquestación y salida. Kafka es mensajería, no base de
datos: no contiene lógica de negocio ni almacenamiento de dominio.

## Fuera de alcance

- Lógica de negocio o conversación.
- Uso de Kafka como base de datos.
- Exponer topics directamente a Twilio.

## Datos del proyecto

- Carpeta del componente: `platform/wa-event-platform`.
- Grupo Maven: `com.bcra`.
- Paquete base: `com.bcra.events`.
- Carpeta de contratos: `shared/contracts/events/`.
- Prefijo de topics: `wa.`; forma `wa.<entidad>.<hecho>.v<n>`.
- Dependencias: ninguna (primer componente de la plataforma).

### Topics iniciales del piloto

| Topic | Productor | Consumidor | Propósito |
| --- | --- | --- | --- |
| `wa.message.received.v1` | H0 Gateway | H0.2 Orquestador | Mensaje inbound validado y normalizado |
| `wa.message.ready-to-send.v1` | H0.2 Orquestador | H0 Gateway | Respuesta final lista para Twilio |
| `wa.message.status.v1` | H0 Gateway | H0.2 / observabilidad | Estado de entrega informado por Twilio |
| `wa.message.dlq.v1` | Infraestructura | Soporte técnico | Mensajes agotados para diagnóstico/reproceso |

## Decisiones de arquitectura

- Kafka como backbone asíncrono del canal.
- Semántica de entrega at-least-once; todos los consumidores deben ser idempotentes.
- Particionar por `conversationId` para preservar el orden dentro de una conversación.
- Eventos son contratos versionados y no transportan secretos ni PII innecesaria.
- El webhook se confirma antes de completar el procesamiento de negocio.
- `correlationId` de punta a punta (proveedor → gateway → Kafka → orquestador → servicio que resuelve el turno → Kafka → gateway → proveedor), en headers y en MDC.

### ADRs requeridos

- ADR — Kafka como backbone asíncrono del canal.
- ADR — At-least-once + idempotencia como semántica de entrega.
- ADR — Partición por conversación para preservar orden.

## Requisitos

### R1 — Topics y partición

- Dado un mensaje inbound normalizado, cuando el gateway lo publica, entonces llega al topic `wa.message.received.v1` particionado por `conversationId`.
- Dado dos mensajes de la misma conversación, cuando se publican, entonces conservan su orden de llegada en la misma partición.
- Dado un mensaje de una conversación distinta, cuando se publica, entonces puede procesarse en paralelo sin afectar el orden de otra.

### R2 — Esquemas versionados

- Dado un evento publicado, cuando se valida, entonces cumple el esquema versionado publicado en `shared/contracts/events/`.
- Dado un cambio incompatible, cuando se publica, entonces se crea una nueva versión (`v2`) sin romper consumidores de la versión anterior.

### R3 — Idempotencia

- Dado un evento reentregado (at-least-once), cuando un consumidor lo procesa, entonces no duplica efectos (idempotencia por `messageId`).

### R4 — Reintentos y DLQ

- Dado un evento no procesable, cuando se agotan los reintentos según la política, entonces termina en `wa.message.dlq.v1` para diagnóstico/reproceso.
- Dado un evento en DLQ, cuando soporte lo reprocesa, entonces puede reingresar al flujo original.

### R5 — Observabilidad

- Dado el flujo completo, cuando se procesa un mensaje, entonces se observan lag, throughput, errores y trazabilidad por `correlationId`.

### R6 — Seguridad

- Dado productores y consumidores, cuando se comunican, entonces el tránsito está cifrado y la autenticación entre ellos está habilitada.
- Dado un evento, cuando se publica, entonces no transporta secretos ni PII innecesaria (teléfonos enmascarados en logs).

### R7 — KRaft local

- Dado el entorno local, cuando se levanta la plataforma, entonces Kafka corre en modo KRaft (sin ZooKeeper) con los topics creados.

## Criterios de aceptación

- Un mensaje conserva orden respecto de otros mensajes de la misma conversación.
- Reentregas no duplican efectos en consumidores.
- Un evento no procesable termina en DLQ tras la política de reintentos.
- Los contratos son versionados y validados.
- Se observan lag, errores y trazabilidad por `correlationId`.

## Requisitos no funcionales

- Entrega at-least-once con consumidores idempotentes.
- Orden por conversación mediante partición por `conversationId`.
- Trazabilidad extremo a extremo con `correlationId`, en headers y MDC.
- Sin secretos ni PII innecesaria en eventos; teléfonos enmascarados en logs.
- El webhook se confirma antes de completar el procesamiento de negocio.

## Supuestos

- La política exacta de reintentos (cantidad de intentos, backoff) y la retención de topics se definen en la implementación; se asume un valor razonable documentado en el código.
- El registro de esquemas puede ser un mecanismo equivalente al Schema Registry (archivos versionados en `shared/contracts/events/`).

## Preguntas abiertas

Ninguna: todos los datos del proyecto provienen de la especificación de la sesión y del roadmap del programa.
