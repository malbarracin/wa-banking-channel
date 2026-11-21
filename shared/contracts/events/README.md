# Catálogo de eventos

Los contratos JSON Schema usan draft 2020-12. Cada evento v1 combina el sobre común con las restricciones de su payload:

| Evento | Esquema | Topic |
| --- | --- | --- |
| `MessageReceived` | [message-received.v1.schema.json](message-received.v1.schema.json) | `wa.message.received.v1` |
| `MessageReadyToSend` | [message-ready-to-send.v1.schema.json](message-ready-to-send.v1.schema.json) | `wa.message.ready-to-send.v1` |
| `MessageStatus` | [message-status.v1.schema.json](message-status.v1.schema.json) | `wa.message.status.v1` |

El [sobre común](envelope.v1.schema.json) contiene los identificadores, tipo, versión, fecha UTC y payload. Los productores y consumidores validan el JSON exacto; deben resolver la referencia relativa al sobre desde el catálogo local. Los formatos UUID y fecha requieren validación de formato activada en el validador.

La clave Kafka es `conversationId`; los headers UTF-8 obligatorios `correlationId`, `eventType` y `eventVersion` deben coincidir con el sobre. `messageId` identifica el mensaje para deduplicación. Los adjuntos se representan por `providerMediaId`, nunca por sus bytes. Los teléfonos son datos personales y se enmascaran en logs.

`errorCode` y `errorDetail` deben ser saneados por el productor: no incluir secretos, cuerpos de solicitudes ni trazas. Sus límites de longitud no sustituyen ese saneamiento. La [configuración de topics y DLQ](topics.md) describe el transporte del registro original.

Un cambio incompatible requiere una nueva versión del esquema y un topic nuevo; los cambios compatibles mantienen la versión.
