# ADR-0005 — Reintentos bloqueantes con backoff y una sola DLQ

- **Estado**: Aceptada
- **Fecha**: 2026-11-21
- **Alcance**: todo consumidor de eventos `wa.*`

## Contexto

Un consumidor falla por dos motivos muy distintos: algo transitorio (la base se reinicia, un servicio devuelve 5xx,
se cortó la red) o algo que no va a mejorar por reintentar (el evento no cumple su esquema, viene un campo
imposible). Hay que absorber el primero sin perder el mensaje y sacar el segundo del camino rápido, sin tapar la
partición.

La restricción que manda es el ADR-0003: el orden dentro de una conversación no se puede romper. Y la
especificación del hito fija un único topic de DLQ, `wa.message.dlq.v1`.

## Decisión

**Reintentos bloqueantes** en el mismo hilo de la partición, con `DefaultErrorHandler` y
`ExponentialBackOffWithMaxRetries(4)`: intervalo inicial 500 ms, multiplicador 2.0, tope 8 s. Son 5 entregas en
total, unos 7,5 segundos. Agotados los intentos, `DeadLetterPublishingRecoverer` manda el registro a
**`wa.message.dlq.v1`**, una sola DLQ para todo el canal.

- **No se crean topics de retry.** Sacar el registro a un topic de retry y volverlo después rompe el orden de la
  conversación, que es exactamente lo que el canal no puede permitirse.
- **No reintentables** (`addNotRetryableExceptions`, van derecho a DLQ): error de parseo JSON, validación de esquema,
  desajuste entre headers y sobre, `eventVersion` que no corresponde al topic, y las violaciones de contrato que
  declare cada consumidor.
- El registro en DLQ va **tal cual**: misma clave, mismos bytes, más headers de falla (topic, partición y offset
  original, clase de la excepción, mensaje truncado a 200 caracteres y saneado, intentos, momento de la falla, y el
  `correlationId`, `eventType` y `eventVersion` originales).
- **El stack trace completo no va a la DLQ**: se excluye ese header y el stack queda en el log del consumidor, con
  su `correlationId`.
- Reproceso: una herramienta de operación lee un rango de offsets de la DLQ y republica cada registro, con su clave
  original, en el topic que indica el header de origen. La DLQ no se borra, y los consumidores toleran el repetido
  por el ADR-0002.

## Alternativas consideradas

- **Reintentos no bloqueantes con topics de retry** (`@RetryableTopic`): libera la partición y permite backoffs de
  minutos, pero reordena los mensajes de una misma conversación. Incompatible con el ADR-0003. Descartada.
- **Reintentos infinitos sin DLQ**: un evento imposible frena la conversación para siempre y, con reintentos largos,
  la partición entera. Descartada.
- **Una DLQ por topic**: más prolijo para enrutar el reproceso, pero la especificación del hito fija una sola DLQ y
  el header de topic original ya alcanza para devolver cada registro a su lugar. Descartada.
- **Pausar el contenedor ante una caída de infraestructura** en vez de mandar a DLQ: evita llenar la DLQ durante un
  corte largo, pero agrega una máquina de estados de operación que el piloto no necesita. Se revisa si los cortes
  reales exceden los 7,5 segundos de backoff.

## Consecuencias

- El orden por conversación se mantiene incluso cuando hay fallas.
- Una conversación que falla retiene su partición hasta 7,5 segundos; las demás conversaciones de esa partición
  esperan. Es el compromiso que se elige a cambio del orden, y por eso el tope del backoff es corto.
- Un corte más largo que el backoff manda eventos a la DLQ: la recuperación es el reproceso, que está documentado y
  es seguro.
- Cualquier incremento de `wa_events_dlq_total` es una alerta: la DLQ no es un lugar donde los mensajes vivan.
- Soporte técnico necesita acceso de lectura a la DLQ y de escritura a los topics de negocio para reprocesar.
