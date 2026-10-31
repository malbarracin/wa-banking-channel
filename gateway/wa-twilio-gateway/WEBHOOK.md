# Webhook inbound (TASK-80/81)

`POST /webhook/twilio` acepta `application/x-www-form-urlencoded`.

## Firma y validación
- Validación con `com.twilio.sdk.security.RequestValidator` usando `X-Twilio-Signature`.
- Se firma la **URL pública exacta** configurada en `TWILIO_WEBHOOK_PUBLIC_URL`, nunca reconstruida desde headers del proxy.
- `TWILIO_AUTH_TOKEN` y `TWILIO_WEBHOOK_PUBLIC_URL` son obligatorios.
- Solo parámetros del cuerpo. Parámetros repetidos → **400**.
- Firma ausente o inválida → **403** (sin publicar ni reservar `MessageSid`).

## Normalización y publicación
- El adaptador `InboundEventPublisher` (TASK-81) recibe los campos **dentro del gateway**, normaliza y valida contra el schema compartido antes de publicar a Kafka.
- `InboundWebhookPublisher` publica `MessageReceived` en `wa.message.received.v1`.
- `messageId` = `MessageSid` (validado `SM` + 32 hex). `conversationId` = SHA-256(`From\nTo`) (cliente\banco). `customerAddress` = `From`, `body` = `Body` (solo texto, 1..4096). No se envía el formulario completo.
- El controller espera confirmación Kafka hasta **500 ms** sin invocar negocio. Si no hay adaptador → **503**.
- Publicación confirmada → **200 vacío**. Duplicado confirmado → **200 vacío sin publicar**. Pendiente/fallido/timeout/capacidad → **503 vacío** (permite reintento Twilio). Errores inesperados → **500 vacío** sin detalles.
- Ver [NORMALIZATION.md](NORMALIZATION.md).

## Anti-replay (piloto)
- Store en memoria compartido con callbacks: reserva `MessageSid`. Máximo **10000** claves (pendientes+confirmadas), **TTL 300 s** desde confirmación.
- Variables: `WEBHOOK_ANTI_REPLAY_WINDOW_SECONDS`, `WEBHOOK_ANTI_REPLAY_CAPACITY` (positivas).
- Reserva pendiente no da ACK duplicado → **503**. Fallo libera reserva. Una firma válida **no** acredita antigüedad (no se inventa timestamp Twilio).
- Tras TTL, reinicio o réplica → puede repetirse; el consumidor **debe** deduplicar por `messageId`.
- Para múltiples réplicas reemplazar por store compartido.

## Límites y observabilidad
- `WebhookInputFilter` aplica los límites antes de que Spring MVC convierta el cuerpo a `byte[]`: `Content-Length` mayor de 16384 → **413** sin abrir el stream; longitud desconocida o declarada menor que la real → lee como máximo **16385** bytes y responde **413** si los supera. El cupo de **120** solicitudes por IP remota por ventana de un minuto e instancia (**429**) se evalúa antes de leer el cuerpo. Incluye solicitudes con firma inválida y no toma la IP de `X-Forwarded-For`. La tabla está acotada a **10000** IP por ventana; nuevas IP reciben **429** si se alcanza la capacidad. El controller conserva ambos chequeos como segunda barrera. Detrás de un proxy el cupo corresponde a su IP; configurar además límites de transporte en el borde para acotar la lectura HTTP y coordinar cuotas entre réplicas. Ver [INPUT-LIMITS.md](INPUT-LIMITS.md).
- Trazabilidad con `correlationId` (ver [OBSERVABILITY.md](OBSERVABILITY.md)).

## Verificación
```bash
cd gateway/wa-twilio-gateway
./mvnw -q -B clean verify  # o mvnw.cmd
```
Pruebas cubren ruta/formulario, firma inválida/ausente/URL incorrecta, ACK <500 ms con publicador confirmado, duplicados, errores recuperables, timeout, publicador ausente, capacidad/expiración. La medición local no sustituye pruebas con Kafka real.
