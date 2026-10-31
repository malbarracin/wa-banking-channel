# Envío outbound (TASK-83)

## Consumo y publicación
- Listener consume `wa.message.ready-to-send.v1` en orden por partición, **1 hebra**, sin auto-commit, ACK manual inmediato.
- Valida JSON Schema compartido, clave `conversationId` y headers del sobre (`correlationId`, `eventType`, `eventVersion`) antes de enviar.
- Publica resultados `MessageStatus` con ID de negocio y correlación original; confirma consumo **después** del ACK de esa publicación.
- Grupo: `wa-twilio-gateway`. Sin paralelismo dentro de partición.

## Configuración Twilio
Configurar: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_WHATSAPP_FROM`, `TWILIO_STATUS_CALLBACK_PUBLIC_URL` (HTTPS, sin query/fragmento). SDK envía To, From, Body, StatusCallback. Ver [TWILIO-CONFIG.md](TWILIO-CONFIG.md).

## Persistencia outbound (`outbound_send`)
- H2 local persistente (`OUTBOUND_DB_URL`, default `jdbc:h2:file:./data/outbound;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`).
- Guarda: id, hash SHA-256 destino/contenido, conversación, correlación, intentos, estado, SID, código de error. **Nunca** guarda cuerpo/destino/token/credenciales.
- Índice único por `messageId`. Transición condicional `RETRYABLE → SENDING` evita dos propietarios.
- Mismo ID con distinto hash/conversación → `MESSAGE_ID_CONFLICT` y deriva a DLQ.
- `SENT` reentregado solo republica estado, **nunca** reenvía a Twilio.

## Estado de envío (atómico entre HTTP y DB)
- No hay transacción atómica HTTP+DB. Secuencia: confirmar `SENDING` → POST Twilio → guardar `SENT` con SID.
- Caída/timeout entre pasos → **no** reenvío automático; deriva a DLQ para reconciliación (evita duplicado).
- Fallo al publicar estado tras `SENT` → solo reintenta publicación de estado (`eventId` estable).

## Reintentos, DLQ y reconciliación
- HTTP 429/5xx: **3 reintentos** (1s, 2s, 4s), **máximo 4 POSTs**, presupuesto persistido entre reinicios.
- Otros errores Twilio, validaciones, resultados ambiguos → directo a DLQ.
- SDK/Apache HTTP sin reintentos adicionales. Timeouts: conexión **3s**, lectura **5s**.
- DLQ `wa.message.dlq.v1` con `DeadLetter`: coordenadas, `originalValueBase64` (**no cifrado**), códigos seguros. Headers seguros: `correlationId`, `eventType`, `eventVersion`, `errorCode`, `providerErrorCode` (numérico/interno, sin texto). ACL/retenido restringidos.
- Ante `SENDING`/timeout ambiguo: **comprobar Twilio antes** de nuevo envío; no republicar ciegamente ni cambiar ID para eludir deduplicación. Soporte: ver [hand-off H0.1 DLQ](../../shared/contracts/events/README.md#reproceso-de-dlq-guía-para-soporte) y [TWILIO-CONFIG.md](TWILIO-CONFIG.md#operación-dlq-y-reconciliación).

## Operación / restricciones piloto
- **Una instancia + volumen persistente `data/`**, permisos restringidos. No borrar tabla/volumen para reprocesar.
- Sin vencimiento automático. Antes de escalar a réplicas → store compartido con mismo protocolo.
- `gateway.outbound.enabled=false` deshabilita flujo (útil para webhooks-only). Con outbound activo se requieren credenciales Twilio.
- Fuera de local: TLS/SASL, ACL consumir solo `ready-to-send`, producir solo `status`/`DLQ`.

## Métricas
`gateway.outbound.consumed`, `.sent`, `.errors` (kind), `.dlq` (code). Sin teléfonos/cuerpos/IDs como etiquetas.

## Verificación
```bash
./mvnw -q -B clean verify        # o mvnw.cmd
./mvnw -q -B -Dtest=OutboundKafkaIntegrationTest test  # con Docker/Testcontainers
```
Tests: WireMock (OK/4xx/429/5xx/timeout), persistencia, duplicados/conflictos, correlación, recuperación, DLQ. KRaft embebido sin Docker + Testcontainers `confluentinc/cp-kafka:7.8.0` (`@Tag("integration")`), se omiten si Docker inaccesible.
