# Callback de estado (TASK-82)

`POST /webhooks/twilio/status`, cuerpo `application/x-www-form-urlencoded`.
Configurar `TWILIO_STATUS_CALLBACK_PUBLIC_URL` con la URL pública exacta del callback
y `TWILIO_AUTH_TOKEN` por entorno. Sin URL el endpoint rechaza todos los callbacks.
La firma incluye todos los campos del formulario; nunca se usa la URL interna del proxy.

Campos requeridos: `MessageSid`, `MessageStatus`, `From` (banco), `To` (cliente).
Estados admitidos por el contrato: queued, sent, delivered, read, failed.
Otros estados de Twilio se rechazan con 400, sin publicar. `ErrorCode` es opcional;
no se publican textos de error, teléfonos ni el formulario original.

Publica `MessageStatus` en `wa.message.status.v1`, validado contra el esquema compartido.
`messageId` es el SID saliente de Twilio. `conversationId` es SHA-256 hexadecimal de
To + salto de línea + From, conservando el orden cliente/banco de TASK-81.
El header HTTP `correlationId` debe ser UUID; se propaga al evento, headers Kafka y MDC.
Si falta, se genera uno y se devuelve en el ACK. Cuando se integre outbound, ese
componente debe conservar la relación entre el SID de Twilio y su messageId de negocio
y suministrar la correlación original; este endpoint no inventa esa relación.
TASK-83 agrega esa relación en `outbound_send`: cuando existe un SID persistido,
el normalizador recupera el messageId de negocio y la correlación originales.
Ver [OUTBOUND.md](OUTBOUND.md) para el caso de callback anterior a la persistencia.

Firma inválida: 403. Entrada inválida: 400. Cuerpo >16 KiB: 413.
Límite global por instancia: 120 callbacks/minuto; excedido: 429.
Kafka confirmado antes de 500 ms: 200. Fallo/timeout: 503, permitiendo reintento de Twilio.
El productor usa acks=all, idempotencia y reintentos acotados por delivery.timeout.ms.
No se confirma ni descarta silenciosamente un fallo de publicación.

Deduplicación local por SID + estado, TTL/capacidad del AntiReplayService existente.
Un reintento confirmado recibe 200 sin publicar; una transición de estado sí publica.
Reintento concurrente o capacidad agotada: 503. La ventana no cubre reinicios,
réplicas ni confirmaciones ambiguas tras timeout: se requiere deduplicación downstream.
Fuera de local configurar TLS/SASL y ACL Kafka con permiso de producción solo al topic de estado.

Verificación: `./mvnw -q -B clean verify` (Windows: `mvnw.cmd`).
Los tests MVC calculan firmas HMAC reales y comprueban rechazo sin efectos, replay,
transiciones, fallos y correlación. El test del productor valida el esquema real,
clave/headers, privacidad y espera del ACK de Kafka usando un KafkaTemplate simulado.
