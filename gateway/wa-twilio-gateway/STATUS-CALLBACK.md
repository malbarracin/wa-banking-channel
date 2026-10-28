# Callback de estado (TASK-82/83)

`POST /webhooks/twilio/status` acepta `application/x-www-form-urlencoded`.

## Configuración
- Configurar `TWILIO_STATUS_CALLBACK_PUBLIC_URL` con la **URL pública exacta** del callback.
- `TWILIO_AUTH_TOKEN` obligatorio por entorno. Sin URL configurada, el endpoint rechaza todos los callbacks.
- La firma incluye **todos** los campos del formulario; nunca se usa la URL interna del proxy.

## Validación
- Campos requeridos: `MessageSid`, `MessageStatus`, `From` (banco), `To` (cliente).
- Estados admitidos: `queued`, `sent`, `delivered`, `read`, `failed`. Otros → **400** sin publicar.
- `ErrorCode` opcional. No se publican textos de error, teléfonos ni el formulario original.
- `correlationId` HTTP debe ser UUID; si falta/se genera, se propaga a evento/headers Kafka/MDC. Si es inválido como UUID → **400**.
- Cuerpo > **16 KiB** → **413**. Límite global **120 callbacks/minuto** por instancia → **429**.

## Publicación
- Publica `MessageStatus` en `wa.message.status.v1` validado contra el schema compartido.
- `messageId` = SID saliente de Twilio.
- `conversationId` = SHA-256 hex de `To\nFrom` (conservando orden cliente/banco).
- Kafka confirmado antes de 500 ms → **200**. Fallo/timeout → **503** (permite reintento Twilio).
- Productor: `acks=all`, idempotencia, reintentos acotados por `delivery.timeout.ms`. No se confirma ni descarta silenciosamente un fallo.

## Relación con outbound (TASK-83)
- `outbound_send` conserva relación `SID → messageId` de negocio, `conversationId`, `correlationId` originales.
- Si existe SID persistido, el normalizador recupera identidad/correlación originales.
- Si llega antes de persistir o SID desconocido, conserva comportamiento de TASK-82 (usa SID como `messageId`, SHA-256 de `To\nFrom`, correlación HTTP). Ver [OUTBOUND.md](OUTBOUND.md).

## Anti-replay / idempotencia
- Deduplicación por `SID + estado` en store compartido (memoria), TTL/capacidad heredados de `AntiReplayService`.
- Reintento confirmado → **200 sin publicar**; transición de estado → publica.
- Reintento concurrente o capacidad agotada → **503**.
- Ventana no cubre reinicios/réplicas/timeout ambiguo → deduplicación downstream obligatoria.

## Seguridad
- Fuera de local: TLS/SASL y ACL Kafka con permiso de producción solo a `wa.message.status.v1`.

## Verificación
```bash
./mvnw -q -B clean verify  # Windows: mvnw.cmd
```
Tests MVC con firmas HMAC reales, rechazo sin efectos, replay, transiciones, fallos, correlación. Productor valida schema/clave/headers/privacidad y ACK con `KafkaTemplate` simulado.
