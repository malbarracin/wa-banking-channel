# Normalizacion inbound — TASK-81

`InboundEventPublisher` implementa la frontera de TASK-80. Produce exclusivamente
`wa.message.received.v1`, validando antes del envio el JSON Schema publicado en
`shared/contracts/events/wa.message.received.v1.schema.json`. Maven empaqueta ese
archivo desde la fuente compartida; no se mantiene una copia divergente.

- `messageId`: `MessageSid` original, obligatorio (SM + 32 digitos hexadecimales).
- `conversationId`: SHA-256 hexadecimal de `From + "\n" + To`, ambos direcciones
  canonicas `whatsapp:+E164`. Incluye el banco para separar destinos y no expone
  el telefono en la clave Kafka. No constituye anonimización del contenido.
- `eventId`: UUID nuevo por intento; `occurredAt`: instante UTC de recepcion.
- `correlationId`: UUID del header HTTP `correlationId`, normalizado; ausente o
  invalido genera UUID nuevo. Se devuelve en el header HTTP y vive en MDC durante
  el request. El executor propaga el contexto y lo limpia al finalizar.
- Payload: solo `customerAddress = From` y `body = Body`; campos adicionales de
  Twilio, medios y datos de cuenta no salen del gateway. Cuerpo vacio o mayor
  que 4096 caracteres se rechaza con 400, sin publicar. La version actual del
  contrato no admite mensajes exclusivamente multimedia.

Kafka usa serializers String, `acks=all`, productor idempotente y maximo cinco
requests en vuelo por conexion. La clave es `conversationId`; los headers son
`correlationId`, `eventType=MessageReceived`, `eventVersion=1`, codificados UTF-8.
Los reintentos del cliente Kafka tienen backoff de 100 ms y un presupuesto de
entrega de 10 segundos. La llamada potencialmente bloqueante por metadata se
ejecuta en dos workers con cola limitada a 100 tareas. El CompletionStage solo
termina exitosamente al confirmar Kafka; el webhook conserva su espera de 500 ms.
Saturacion, fallo o timeout devuelven 503 y permiten reintento de Twilio.

La deduplicacion basica sigue siendo el store local de TASK-80 (MessageSid,
TTL 300 s). Reinicio, varias replicas o timeout ambiguo pueden repetir eventos:
H0.2 debe deduplicar por `messageId`, nunca por `eventId`. No se promete exactly
once entre HTTP y Kafka. Un fallo Kafka no se convierte en ACK ni se intenta
mandar a una DLQ en el mismo broker caido: se conserva el reintento HTTP.

No se registran formularios ni errores con contenido. Para ambientes no locales
se debe configurar TLS/SASL por el mecanismo externo de propiedades Spring Kafka
y ACL con permiso de escritura unicamente sobre el topic inbound. No guardar
credenciales en archivos versionados.

Verificacion desde el componente:

```text
mvnw.cmd -q -B clean verify
```

Unitarios: contrato, normalizacion, claves/headers, confirmacion asincrona,
fallo, saturacion, validacion y contexto MDC. La suite existente cubre firma y
anti-replay. `InboundKafkaIntegrationTest` (`@Tag("integration")`) usa
`apache/kafka:3.9.0` KRaft y prueba webhook firmado, duplicado, orden y headers.
Se omite automaticamente si Docker no esta disponible. Para exigir su ejecucion,
comprobar que Surefire informe cero pruebas omitidas con Docker accesible.
