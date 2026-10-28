# Webhook inbound — TASK-80

`POST /webhook/twilio` acepta `application/x-www-form-urlencoded` y valida
`X-Twilio-Signature` con el `RequestValidator` del SDK oficial. Se firma la URL
exacta de `TWILIO_WEBHOOK_PUBLIC_URL`, nunca una URL reconstruida desde headers
del proxy. `TWILIO_AUTH_TOKEN` y la URL son obligatorios. Solo se usan parámetros
del cuerpo; parámetros repetidos se rechazan con 400.

Firma ausente o inválida: 403 sin llamar al publicador ni reservar MessageSid.
MessageSid ausente o inválido: 400. Publicación confirmada: 200 vacío. Duplicado
confirmado: 200 sin publicar. Publicación pendiente, fallida, timeout o capacidad
agotada: 503 vacío, permitiendo reintentar. No se devuelven excepciones ni payloads.

El adaptador `InboundEventPublisher` de TASK-81 recibe los campos **dentro del
gateway**, los normaliza y valida el evento antes de Kafka. Retorna
un CompletionStage cuya finalizacion confirma la publicacion.
El controller espera como máximo 500 ms esa confirmación, sin invocar negocio.
Si no hay adaptador, se responde 503. La estrategia de normalizacion,
correlacion y publicacion se documenta en NORMALIZATION.md.

Anti-replay del piloto: memoria local, máximo 10000 claves entre pendientes y
confirmadas, TTL de 300 s desde confirmación. Variables:
`WEBHOOK_ANTI_REPLAY_WINDOW_SECONDS` y `WEBHOOK_ANTI_REPLAY_CAPACITY`, ambas
positivas. Una reserva pendiente no obtiene ACK de duplicado; responde 503.
Un fallo libera la reserva y permite reintento. No se inventa un timestamp de
Twilio: una firma válida no acredita antigüedad del mensaje. Tras TTL, reinicio
o en otra réplica, el evento puede repetirse; el consumidor debe deduplicar por
messageId = MessageSid. Un timeout de publicación también puede ser ambiguo.
Para varias réplicas se necesita reemplazar este store por uno compartido.

La base Maven incluye Spring Web, Spring Boot Test y `com.twilio.sdk:twilio`.
Los tests usan MockMvc y HMAC-SHA1 calculado independientemente del validador:

```text
cd gateway/wa-twilio-gateway
mvnw.cmd -q -B clean verify
```

Prueban ruta, formulario, firma inválida/ausente/URL incorrecta, ACK <1 s con
publicador confirmado, duplicados, errores recuperables, timeout, publicador
ausente, capacidad y expiración sin sleeps. La medición local no sustituye una
prueba de latencia con Kafka real al integrar TASK-81. Los límites HTTP de tamaño
y tasa y la observabilidad transversal se completan en TASK-84.
