# Envío outbound (TASK-83)

El listener `wa-twilio-gateway` consume `wa.message.ready-to-send.v1` en orden por
partición, con una hebra, sin auto commit y ACK manual inmediato. Valida el JSON
Schema compartido, la clave `conversationId` y los tres headers del sobre antes
de efectuar envíos. Publica resultados `MessageStatus` con el ID de negocio y la
correlación original; confirma el consumo después del ACK de esa publicación.

Configurar `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_WHATSAPP_FROM` y
`TWILIO_STATUS_CALLBACK_PUBLIC_URL` (HTTPS, sin query ni fragmento). El SDK oficial
envía To, From, Body y StatusCallback. El callback existente valida la URL pública
exacta. La relación SID → messageId/conversationId/correlationId permite normalizar
callbacks posteriores con la identidad original; un SID desconocido conserva el
comportamiento de TASK-82. Un callback que llegue antes de persistir el SID puede
usar ese comportamiento anterior; los consumidores de estados deben tolerarlo.

La tabla técnica `outbound_send` vive en H2 local persistente (`OUTBOUND_DB_URL`),
con escrituras sin demora. Conserva ID, hash SHA-256 de destino y contenido,
conversación, correlación, intentos, estado, SID y código de error; nunca guarda
el cuerpo, destino ni credenciales. Un índice único por messageId y una transición
condicional de RETRYABLE a SENDING evitan dos propietarios del mismo envío.
El mismo ID con distinto contenido/conversación se deriva a DLQ.

El piloto requiere **una instancia y un volumen persistente para `data/`**, con
permisos de acceso restringidos. No borrar la tabla ni el volumen para reprocesar.
No hay vencimiento automático de claves de deduplicación. Antes de escalar a
réplicas, debe sustituirse este almacenamiento local por uno compartido, con el
mismo protocolo de reserva. `gateway.outbound.enabled=false` deshabilita el flujo
cuando se necesite arrancar únicamente los webhooks.

No existe una transacción atómica entre una base local y la API externa de Twilio.
Primero se confirma SENDING, después se realiza el POST y finalmente se guarda
SENT con su SID. Ante una caída entre esos pasos o un timeout de transporte, no
se vuelve a enviar automáticamente: se deriva a DLQ para reconciliar con Twilio.
Esto evita un segundo POST que pueda duplicar un mensaje ya aceptado. Un fallo
al publicar estado después de SENT solo repite la publicación de estado, cuyo
eventId es estable; nunca repite el envío a Twilio.

HTTP 429 y 5xx tienen tres reintentos (1, 2 y 4 segundos), máximo cuatro POSTs,
con presupuesto persistido incluso si el proceso reinicia. Otros errores de
Twilio, validaciones y resultados ambiguos van directamente a DLQ. El SDK y
Apache HTTP no hacen reintentos adicionales. Timeouts: conexión 3 s, lectura 5 s.
Los estados finales fallidos y códigos seguros quedan registrados localmente.

DLQ usa el contrato H0.1 `DeadLetter`, incluyendo coordenadas del registro y
originalValueBase64; **base64 no es cifrado**. ACL de soporte y retención H0.1
son obligatorias, porque el original contiene contenido/destino del contrato.
Headers seguros: correlationId, eventType, eventVersion, errorCode y
providerErrorCode (código numérico de Twilio o identificador interno, sin texto).
No se confirma una recuperación hasta confirmar Kafka. Se suprimen logs del SDK
y del error handler que pueden incluir el registro original.

Para reconciliar un envío ambiguo, soporte debe comprobar los registros de Twilio
antes de autorizar un nuevo envío desde el productor. No republicar ciegamente
una DLQ de timeout ni cambiar el ID de negocio para eludir la deduplicación.
Fuera de local: TLS/SASL y ACL que permitan consumir únicamente ready-to-send y
producir únicamente status/DLQ. El broker y sus topics pertenecen a H0.1.

Métricas: `gateway.outbound.consumed`, `.sent`, `.errors` (kind) y `.dlq` (code).
No usan teléfonos, cuerpos ni IDs de cliente como etiquetas.

Verificación: `mvnw.cmd -q -B clean verify` o `./mvnw -q -B clean verify`.
Hay pruebas de SDK con WireMock (OK, 4xx, 429, 5xx y timeout), persistencia,
duplicados, conflictos, correlación y recuperación de estados. La misma suite de
flujo/orden/retry/DLQ se ejecuta con Kafka KRaft embebido sin Docker y con
Testcontainers `confluentinc/cp-kafka:7.8.0` (`@Tag("integration")`); esta última
se omite cuando Docker no está accesible. Para ejecutarla específicamente:
`mvnw.cmd -q -B -Dtest=OutboundKafkaIntegrationTest test`.
