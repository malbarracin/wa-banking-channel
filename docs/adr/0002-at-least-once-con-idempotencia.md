# ADR-0002 — Entrega at-least-once con consumidores idempotentes

- **Estado**: Aceptada
- **Fecha**: 2026-11-21
- **Alcance**: todo consumidor de eventos `wa.*`

## Contexto

Un mensaje del canal puede llegar repetido por varios caminos: Twilio reintenta el webhook si no recibe el ACK a
tiempo, el productor reintenta un envío cuya confirmación se perdió, y el consumidor reprocesa registros ya
procesados cuando hay un rebalanceo o un reinicio entre el efecto y el commit del offset.

En un canal bancario un duplicado no es un detalle: puede significar dos respuestas al cliente o, peor, dos veces
una misma operación.

## Decisión

La semántica del canal es **at-least-once**, y **la idempotencia es responsabilidad de cada consumidor**, no del
transporte. Concretamente:

- Productores con `enable.idempotence=true` y `acks=all`: evitan duplicados y huecos del propio reintento del broker.
- Consumidores con `enable.auto.commit=false` y ack manual **después** del efecto (`MANUAL_IMMEDIATE`).
- Deduplicación de negocio por **`messageId`**: el consumidor registra `(groupId, messageId)` como procesado **en la
  misma transacción que el efecto**, con restricción única. Si la inserción choca, es duplicado: se confirma el
  registro sin repetir el efecto.
- Deduplicación de reentrega por `eventId`, como corte temprano.
- No se usan transacciones de Kafka ni `exactly-once` en el piloto.

## Alternativas consideradas

- **Exactly-once con transacciones de Kafka** (`read_committed` más productor transaccional): no cubre el caso real
  que más duele, que es el webhook repetido de Twilio con otro `eventId`, y los efectos salen de Kafka (PostgreSQL,
  llamadas a servicios, envío a Twilio), así que igual harían falta claves de idempotencia. Suma costo operativo y
  latencia sin sacar el requisito. Descartada.
- **Ack antes de procesar** (at-most-once): elimina duplicados y cambia el problema por mensajes perdidos.
  Inaceptable en un canal bancario. Descartada.
- **Deduplicación central en la plataforma**: un filtro común exigiría estado compartido y código compartido entre
  componentes, que el programa no permite; además cada consumidor tiene su propio concepto de efecto ya hecho.
  Descartada.

## Consecuencias

- Todo consumidor nuevo del canal necesita su store de idempotencia; es parte de la definición de terminado.
- H0.2 lleva una tabla con `UNIQUE (consumer_group, message_id)` en PostgreSQL; H0 guarda los `MessageSid` vistos
  con TTL de 24 horas o más.
- La plataforma incluye un store en memoria **solo de referencia**, documentado como no apto para un consumidor real.
- Se puede reprocesar desde la DLQ sin miedo: los consumidores ya toleran repetidos (ADR-0005).
- Las pruebas de cada consumidor deben incluir el caso del mismo `messageId` dos veces con un solo efecto.
