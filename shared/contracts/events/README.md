# Contratos de eventos v1

JSON Schema draft-07. Los cuatro archivos `wa.message.*.v1.schema.json` son
autocontenidos y usan el sobre definido en `event-envelope.v1.schema.json`.
El sobre y cada payload rechazan propiedades desconocidas. UUID y fechas RFC 3339
se validan con comprobación de formatos habilitada.

| Topic | eventType | Payload obligatorio |
| --- | --- | --- |
| wa.message.received.v1 | MessageReceived | customerAddress, body |
| wa.message.ready-to-send.v1 | MessageReadyToSend | customerAddress, body |
| wa.message.status.v1 | MessageStatus | status |
| wa.message.dlq.v1 | DeadLetter | originalTopic, originalPartition, originalOffset, originalValueBase64, errorCode |

`customerAddress` es la dirección mínima de entrega que necesita el gateway;
`body` contiene solo el texto del turno. No incluir documentos, credenciales,
saldos ni payloads del proveedor. No registrar estos campos en logs.
`errorCode` de estado es opcional y contiene solo un código, nunca texto de excepción.

## Transporte

Clave Kafka: `conversationId`. Headers UTF-8 obligatorios: `correlationId`,
`eventType`, `eventVersion` (cadena `1`), iguales a los campos del sobre.
El consumidor debe verificar también clave y headers: JSON Schema valida el valor
JSON, no los metadatos del registro Kafka.

## Validación y errores

`EventSchemaValidator.validate(topic, json)` en `platform/wa-event-platform`
implementa la validación con networknt. Maven empaqueta directamente los schemas
de esta carpeta en `contracts/events`, sin copias mantenidas a mano.
Ejecutar `mvnw.cmd -q -B clean verify` desde el componente (Linux: `./mvnw`).

El productor debe validar antes del envío: `InvalidEventException` impide publicar.
El consumidor debe validar antes de ejecutar efectos. Su error handler debe declarar
`addNotRetryableExceptions(InvalidEventException.class)` y recuperar inmediatamente
en `wa.message.dlq.v1`, sin reintentar validación. Esta tarea publica el validador y
la política; el cableado de productores, listeners y recuperador corresponde a sus
tareas de implementación. No hay listeners activos en este componente todavía.

La recuperación construye un nuevo sobre `DeadLetter` válido y conserva el valor
original exacto en base64, topic, partición y offset. Base64 no cifra: la DLQ requiere
ACL restringida a soporte, cifrado en tránsito y retención limitada. No agregar
stack traces, mensajes de excepción ni headers arbitrarios. Para un sobre original
ilegible generar UUID nuevos y usar identificadores técnicos de recuperación para
conversationId/messageId; conservar los originales únicamente si son válidos.
No reenviar un fallo de la propia DLQ hacia ella: detener y alertar para evitar bucles.
Confirmar el registro original solo después del envío confirmado a DLQ; si este
envío falla, conservar el offset para recuperarlo sin perder el evento.

Soporte reprocesa de forma controlada después de corregir la causa, validando el
valor decodificado contra el topic original y preservando la clave y trazabilidad.
Los consumidores deben deduplicar por messageId; el reproceso puede repetir entregas.

## Evolución

Un cambio incompatible requiere nueva versión y topic, con publicación paralela
hasta migrar consumidores. Un campo opcional exige coordinar el despliegue del
schema con consumidores que rechazan propiedades desconocidas.
