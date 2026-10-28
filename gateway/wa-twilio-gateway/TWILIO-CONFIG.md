# Configuración Twilio, webhook, firma, trazabilidad y operación (H0)

Esta guía consolida lo necesario para configurar, operar y dar soporte al `wa-twilio-gateway`.

## 1) Configuración
### Variables de entorno
Ver [.env.example](.env.example). Spring **no** carga `.env` automáticamente.

| Variable | Obligatorio | Uso | Notas |
| --- | --- | --- | --- |
| `SERVER_PORT` | No | Puerto HTTP | Default `8090` |
| `TWILIO_AUTH_TOKEN` | Sí | Firma + envío | Secreto. Solo entorno/gestor de secretos |
| `TWILIO_WEBHOOK_PUBLIC_URL` | Sí | URL exacta pública `POST /webhook/twilio` | Debe coincidir con lo configurado en Twilio |
| `TWILIO_STATUS_CALLBACK_PUBLIC_URL` | Sí (outbound) | URL exacta pública `POST /webhooks/twilio/status` | HTTPS, sin query/fragmento |
| `TWILIO_ACCOUNT_SID` | Sí (outbound) | Cuenta Twilio | - |
| `TWILIO_WHATSAPP_FROM` | Sí (outbound) | Emisor WhatsApp | `whatsapp:+E164` |
| `KAFKA_BOOTSTRAP_SERVERS` | Sí | Bootstrap Kafka | Default `localhost:9092` |
| `WEBHOOK_ANTI_REPLAY_WINDOW_SECONDS` | No | TTL anti-replay | Default `300` (positivo) |
| `WEBHOOK_ANTI_REPLAY_CAPACITY` | No | Capacidad anti-replay | Default `10000` (positivo) |
| `OUTBOUND_DB_URL`/`OUTBOUND_DB_USER`/`OUTBOUND_DB_PASSWORD` | No | H2 outbound | Ver [OUTBOUND.md](OUTBOUND.md) |
| `gateway.outbound.enabled` | No | Habilitar outbound | Default `true`. `false` para webhooks-only |

Fuera de local: TLS/SASL + ACL por topic. Nunca guardar credenciales en repo/logs/eventos.

## 2) Configurar webhook en Twilio
1. Exponer `:8090` con túnel HTTPS (ngrok/ngrok-alt/Cloudflare Tunnel).
2. Configurar en Twilio Messaging:
   - **Webhook para mensajes entrantes**: `POST https://<public-host>/webhook/twilio` → `TWILIO_WEBHOOK_PUBLIC_URL`
   - **Status Callback URL**: `POST https://<public-host>/webhooks/twilio/status` → `TWILIO_STATUS_CALLBACK_PUBLIC_URL`
3. Mantener esquema/host/path exactos (la firma se calcula sobre la URL pública exacta). No depende de headers del proxy.
4. Probar con mensaje de prueba; Twilio reintentará en 503.

Ver [WEBHOOK.md](WEBHOOK.md) y [STATUS-CALLBACK.md](STATUS-CALLBACK.md).

## 3) Firma, validación y respuestas
| Endpoint | Reglas clave | Respuestas |
| --- | --- | --- |
| `POST /webhook/twilio` | `X-Twilio-Signature`, URL pública exacta, parámetros del cuerpo, sin repetidos | 200 (confirmado/duplicado), 400 (inválido), 403 (firma), 503 (fallo/timeout/capacidad), 500 (inesperado) |
| `POST /webhooks/twilio/status` | Firma con todos los campos, URL exacta, `MessageSid/MessageStatus/From/To`, estados válidos, `correlationId` UUID si presente | 200, 400, 403, 413 (>16KiB), 429 (>120/min), 503, 500 |

ACK HTTP se espera <500ms confirmando publicación Kafka (medir bajo carga).

## 4) Trazabilidad (correlación)
- Filtro HTTP normaliza/canoniza/genera `correlationId` UUID, lo devuelve en respuesta y coloca en MDC.
- Se propaga a evento, headers Kafka (`correlationId,eventType,eventVersion`) y logs.
- Inbound/outbound/status mantienen correlación; callbacks pueden recuperar correlación original vía relación SID en `outbound_send`.
- Logs JSON ECS sin formularios/cuerpos/teléfonos/firma/token.

Ver [OBSERVABILITY.md](OBSERVABILITY.md).

## 5) Catálogo de eventos (resumen)
Ver detalle en [`shared/contracts/events/README.md`](../../shared/contracts/events/README.md).

| Topic | Productor | Consumidor | eventType | Payload |
| --- | --- | --- | --- | --- |
| `wa.message.received.v1` | H0 Gateway | H0.2 | `MessageReceived` | `customerAddress`, `body` |
| `wa.message.ready-to-send.v1` | H0.2 | H0 Gateway | `MessageReadyToSend` | `customerAddress`, `body` |
| `wa.message.status.v1` | H0 Gateway | H0.2/observabilidad | `MessageStatus` | `status` (+`errorCode` opcional) |
| `wa.message.dlq.v1` | Infra/Gateway | Soporte | `DeadLetter` | coords + `originalValueBase64` + códigos seguros |

Clave `conversationId`, headers obligatorios, `additionalProperties:false`, validación con schema compartido.

## 6) Operación
### Arranque
```bash
# Local
./mvnw spring-boot:run  # o .\mvnw.cmd

# Docker compose (desde gateway/wa-twilio-gateway)
docker compose up -d --wait
docker compose --profile app up -d --wait
```
`gateway.outbound.enabled=false` permite arrancar sin credenciales outbound; webhooks siguen requiriendo Kafka para confirmar publicaciones.

### Health/métricas
- `/actuator/health`, `/actuator/info`, `/actuator/prometheus` (restringir red)
- Métricas HTTP: `gateway_webhook_requests_total{endpoint,status}`, `gateway_webhook_duration_seconds`
- Outbound: `gateway.outbound.consumed`, `.sent`, `.errors{kind}`, `.dlq{code}`
- Sin PII en etiquetas

### Anti-replay e idempotencia
- Inbound/callbacks: memoria compartida, TTL 300s, cap 10000, reserva por clave. No cubre réplicas/reinicio/timeout ambiguo → deduplicación downstream.
- Outbound: `outbound_send` H2 con reserva condicional, clave única `messageId`. Piloto **1 instancia + volumen persistente `data/`**.

## 7) DLQ y reconciliación (soporte)
Contrato `DeadLetter` en `wa.message.dlq.v1`. **Base64 no es cifrado** → ACL/retenido/cifrado en tránsito obligatorios.

**Categorías gateway:** `SCHEMA_VALIDATION_FAILED`, `NON_RETRYABLE_ERROR`, `RETRIES_EXHAUSTED`. Headers: `correlationId`, `eventType`, `eventVersion`, `errorCode`, `providerErrorCode` (numérico/interno).

### Procedimiento
1. **Autorizar**: incidente, coordenadas (`originalTopic/partition/offset`), `correlationId`, sin copiar payload sensible.
2. **Inspeccionar**: leer DLQ con acceso restringido, decodificar `originalValueBase64`, validar contra schema original. Consultar `outbound_send` y registros Twilio si aplica.
3. **Reconciliar** (outbound ambiguo): si estado `SENDING`/timeout, **comprobar Twilio** antes de nuevo envío. No republicar ciegamente, no cambiar ID, no borrar filas. Gateway sin endpoint de desbloqueo.
4. **Reproceso seguro**: reconstruir clave `conversationId` y headers desde sobre original validado, conservar `messageId` negocio, publicar en topic original esperando confirmación. **Nunca** publicar `DeadLetter` en negocio ni reenviar DLQ a sí misma. Reparaciones con trazabilidad.
5. **Verificar**: efecto/duplicado, anotar coordenadas. En `SENT` solo republica estado (sin reenviar a Twilio). Si DLQ caída → restaurar/alertar primero.

Ver guía completa H0.1: [`shared/contracts/events/README.md#reproceso-de-dlq-guía-para-soporte`](../../shared/contracts/events/README.md#reproceso-de-dlq-guía-para-soporte).

## 8) Verificación
```bash
# Componente
cd gateway/wa-twilio-gateway
./mvnw -q -B clean verify        # Windows: .\mvnw.cmd
# Específico integración (requiere Docker)
./mvnw -q -B "-Dtest=InboundKafkaIntegrationTest,OutboundKafkaIntegrationTest" test

# Raíz
python scripts/verify_integracion.py
```
Revisar `target/surefire-reports`, `target/site/jacoco/index.html`. Tests embebidos KRaft sin Docker; Testcontainers se omiten si Docker inaccesible.

## Referencias
- [README.md](README.md)
- [WEBHOOK.md](WEBHOOK.md)
- [STATUS-CALLBACK.md](STATUS-CALLBACK.md)
- [NORMALIZATION.md](NORMALIZATION.md)
- [OUTBOUND.md](OUTBOUND.md)
- [OBSERVABILITY.md](OBSERVABILITY.md)
- [shared/contracts/events/README.md](../../shared/contracts/events/README.md)
- [shared/contracts/wa-twilio-gateway/handoff.md](../../shared/contracts/wa-twilio-gateway/handoff.md)
