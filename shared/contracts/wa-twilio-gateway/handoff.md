# Handoff — H0 Gateway WhatsApp (Twilio)

Entrega TASK-85 para integración, H0.2 y soporte. Dependencias del tablero:
TASK-79, TASK-80, TASK-81, TASK-82, TASK-83 y TASK-84 (base, webhook,
normalización/publicación, callbacks, outbound y observabilidad). Esta entrega
documenta el comportamiento implementado; no modifica los contratos ni implementa
funciones de H1–H9.

## Fuentes y responsabilidades

Componente: `gateway/wa-twilio-gateway`, Java 21, Spring Boot 3,
paquete `com.bcra.gateway`, puerto predeterminado 8090. H0 es el único adaptador
de Twilio; H0.1 administra Kafka/topics y H0.2 deduplica inbound, conserva el
orden de conversación y produce outbound. La especificación y el diseño están en
[spec.md](../../../specs/gateway-twilio/spec.md) y
[arquitectura.md](../../../specs/gateway-twilio/arquitectura.md).
Las rutas y decisiones concretas de abajo corresponden al código actual y
precisan las propuestas y preguntas abiertas de esos documentos.

## Endpoints y firma

| Método y ruta real | Entrada / resultado |
| --- | --- |
| `POST /webhook/twilio` | Formulario `application/x-www-form-urlencoded`: `MessageSid`, `From` (cliente), `To` (banco), `Body`. Publica `MessageReceived`. |
| `POST /webhooks/twilio/status` | Mismo tipo de formulario: `MessageSid`, `MessageStatus`, `From` (banco), `To` (cliente), `ErrorCode` opcional. Publica `MessageStatus`. |
| `GET /actuator/health` | Salud del proceso sin detalles; no acredita disponibilidad de Kafka/Twilio. |
| `GET /actuator/info` | Información de la aplicación. |
| `GET /actuator/prometheus` | Métricas; restringir acceso en el despliegue. |

Ambos POST requieren `X-Twilio-Signature`. El SDK oficial `RequestValidator`
valida todos los parámetros del formulario contra la URL pública exacta
configurada para cada endpoint y `TWILIO_AUTH_TOKEN`. No reconstruye la URL
desde headers del proxy. Configurar esas mismas URLs en Twilio, preservando
esquema, host, path y cualquier componente firmado. Los parámetros repetidos
se rechazan con 400; inbound toma parámetros del cuerpo, no de la query.
El callback outbound exige URL HTTPS sin query ni fragmento.

| HTTP | Interpretación operativa |
| --- | --- |
| 200 vacío | Kafka confirmó la publicación o el duplicado ya estaba confirmado. No implica respuesta del agente ni entrega al cliente. |
| 400 | Formulario, identificadores, contenido o estado inválidos. Callback con `correlationId` no interpretable como UUID también se rechaza. |
| 403 | Firma ausente/incorrecta o URL de callback sin configurar; no publica. |
| 413 / 429 | Callback mayor que 16384 bytes / más de 120 solicitudes por minuto e instancia. |
| 503 vacío | Publicación fallida, timeout, saturación, reserva pendiente o capacidad anti-replay agotada; permite reintento de Twilio. |
| 500 vacío | Error inesperado, sin detalles técnicos. |

El controller espera hasta 500 ms por la confirmación Kafka. Confirma antes del
procesamiento de negocio, sin invocar servicios ni al agente. El objetivo de ACK
menor a 1 s se prueba localmente; debe medirse bajo carga y con la red real.
Inbound valida `MessageSid` como `SM` + 32 dígitos hexadecimales, direcciones
`whatsapp:+E164` y texto de 1 a 4096 caracteres. No admite mensajes exclusivamente
multimedia. El límite de texto no equivale a un límite del formulario completo:
el controller inbound no implementa el límite global de bytes/tasa del callback.
Configurar esos límites en el borde antes de exposición pública; no asumir que
TASK-84 añadió controles inbound de tamaño o tasa.

## Contratos para H0.2

Los nombres reales de los archivos son `wa.message.*.v1.schema.json`, dentro de
[events](../events/README.md); los schemas compartidos son la fuente única y
se empaquetan en Maven. Rechazan propiedades desconocidas en sobre y payload.

| Topic / schema | Dirección para H0 | Tipo y payload |
| --- | --- | --- |
| [wa.message.received.v1](../events/wa.message.received.v1.schema.json) | Produce | `MessageReceived`: `customerAddress`, `body`. |
| [wa.message.ready-to-send.v1](../events/wa.message.ready-to-send.v1.schema.json) | Consume | `MessageReadyToSend`: `customerAddress`, `body`. |
| [wa.message.status.v1](../events/wa.message.status.v1.schema.json) | Produce | `MessageStatus`: `status`, `errorCode` opcional. |
| [wa.message.dlq.v1](../events/wa.message.dlq.v1.schema.json) | Produce errores outbound | `DeadLetter`: coordenadas del registro, original en base64 y código seguro. |

Sobre obligatorio: `eventId` UUID, `eventType`, `eventVersion=1`, `occurredAt`
RFC3339, `correlationId` UUID, `conversationId`, `messageId`, `payload`.
Clave Kafka String UTF-8 = `conversationId`; headers UTF-8 `correlationId`,
`eventType`, `eventVersion` (cadena `1`) iguales al sobre. El consumidor outbound
valida schema, clave y headers antes del efecto. Los productores validan schema
antes del envío, con `acks=all`, idempotencia habilitada y máximo cinco requests
en vuelo. Esto no proporciona exactly-once entre HTTP, Kafka y Twilio.

Inbound usa `messageId=MessageSid`, `eventId` nuevo por intento y hora UTC de
recepción. `conversationId` es SHA-256 hexadecimal de `From + "\n" + To`
(cliente, banco, direcciones canónicas). Solo salen `customerAddress=From` y
`body=Body`; no el formulario Twilio. El hash de conversación no anonimiza el
contenido ni la dirección del payload.

H0.2 debe conservar esa conversación, emitir un `messageId` de salida estable
y único por mensaje, propagar `correlationId` y deduplicar inbound por
`messageId`, nunca por `eventId`. Outbound consume con grupo
`wa-twilio-gateway`, concurrencia 1, sin auto-commit y ACK manual inmediato,
después de publicar el estado confirmado. No paraleliza dentro de la partición.
El orden es por clave; un replay tardío no restaura el orden histórico.
Particiones y retenciones pertenecen a [topics.md](../events/topics.md): local,
3 particiones, réplica 1; received/ready 7 días, status 30 días, DLQ 90 días.
No aumentar particiones con conversaciones activas sin plan de migración.

## Correlación y estados

El filtro HTTP normaliza un UUID canónico de `correlationId` o genera uno si
falta/es inválido; lo devuelve en respuesta y lo coloca en MDC. Inbound lo
propaga al executor, evento y headers Kafka, limpiando el contexto al terminar.
El callback valida también el header recibido y puede responder 400 si es inválido.

Outbound guarda la relación SID de Twilio → `messageId`, `conversationId` y
`correlationId` originales. Un callback con SID conocido recupera esos valores;
uno desconocido (incluido el que llega antes de persistir el SID) usa el SID
como `messageId`, SHA-256 de `To + "\n" + From` y correlación HTTP.
H0.2 debe tolerar ese caso y estados duplicados o fuera de orden; el gateway
no impone una máquina de transiciones de entrega. Estados admitidos: `queued`,
`sent`, `delivered`, `read`, `failed`; otros se rechazan con 400.
El resultado inmediato del POST outbound publica estado con ID de negocio;
su `eventId` deriva de `messageId + ":outbound-result"` y es estable en reentregas.
Los callbacks pueden generar eventos adicionales del mismo estado.

## Anti-replay e idempotencia

Inbound reserva `MessageSid`; callbacks reservan `status:<SID>:<estado>`.
Store compartido entre ambos flujos, en memoria de la instancia: TTL 300 s
desde confirmación y capacidad total 10000 claves pendientes/confirmadas.
Duplicado confirmado → 200 sin republicar; pendiente/capacidad agotada → 503;
fallo libera reserva. Una firma válida no prueba antigüedad: no se inventa un
timestamp Twilio. Tras TTL, reinicio, otra réplica o timeout ambiguo puede
repetirse el evento. La deduplicación durable downstream es obligatoria.

Outbound persiste `outbound_send` en H2 de archivo. Una clave primaria por
`messageId` y reserva condicional `RETRYABLE → SENDING` evitan dos propietarios.
Guarda hash de destino/cuerpo, conversación, correlación, intentos, estado, SID
y código seguro; no guarda destino/cuerpo/token. Reusar ID con distinto hash
o conversación produce `MESSAGE_ID_CONFLICT` y DLQ. Un `SENT` reentregado solo
republica el resultado, nunca vuelve a hacer POST a Twilio.

Se requiere **una instancia con volumen persistente para `data/`**, permisos
restringidos y respaldo operativo del archivo. No hay expiración automática
de claves outbound. No borrar tabla/volumen para reprocesar. Antes de replicar,
sustituir ambos stores locales por almacenamiento compartido manteniendo el
protocolo de reserva; el piloto no garantiza idempotencia entre réplicas.

## Configuración y arranque

Spring no carga `.env` automáticamente: inyectar variables desde el entorno o
el gestor de secretos. La referencia es
[.env.example](../../../gateway/wa-twilio-gateway/.env.example) y
[application.yml](../../../gateway/wa-twilio-gateway/src/main/resources/application.yml).

| Variable / propiedad | Uso y valor predeterminado |
| --- | --- |
| `SERVER_PORT` | 8090. |
| `TWILIO_AUTH_TOKEN` | Obligatorio para validar firmas y enviar; secreto solo por entorno. |
| `TWILIO_WEBHOOK_PUBLIC_URL` | Obligatoria; URL exacta pública de `/webhook/twilio`. |
| `TWILIO_STATUS_CALLBACK_PUBLIC_URL` | URL exacta de `/webhooks/twilio/status`; sin ella se rechazan callbacks. Obligatoria para outbound. |
| `TWILIO_ACCOUNT_SID`, `TWILIO_WHATSAPP_FROM` | Obligatorios con outbound activo; cuenta y emisor `whatsapp:+E164`. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`; Kafka debe estar disponible para confirmar publicaciones. |
| `WEBHOOK_ANTI_REPLAY_WINDOW_SECONDS` | 300, positivo. |
| `WEBHOOK_ANTI_REPLAY_CAPACITY` | 10000, positivo. |
| `OUTBOUND_DB_URL` | `jdbc:h2:file:./data/outbound;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`; relativo al directorio de ejecución. |
| `OUTBOUND_DB_USER`, `OUTBOUND_DB_PASSWORD` | Defaults locales `sa` / vacío; configurar credencial fuera del repo. |
| `gateway.outbound.enabled` / `GATEWAY_OUTBOUND_ENABLED` | `true` por defecto. `false` desactiva listener/envío y sus credenciales adicionales; webhooks siguen necesitando Kafka para publicar. |

Con Java 21 y los topics H0.1 disponibles, desde el componente:

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux/macOS usar `./mvnw`. Fuera de local configurar propiedades Spring Kafka
de TLS/SASL mediante despliegue seguro. ACL H0: consumir ready-to-send y usar su
grupo; producir received/status/DLQ. Soporte tiene acceso restringido a DLQ.
Replicación y `min.insync.replicas` según H0.1 (3 y 2 fuera de local). Nunca
guardar credenciales en configuración versionada, logs, eventos ni tickets.

## Reintentos, DLQ y replay de soporte

Inbound/status: Kafka reintenta con backoff de 100 ms, presupuesto de entrega
10 s; la espera HTTP sigue siendo 500 ms. Inbound usa dos workers y cola de 100.
Un 503 puede coexistir con una publicación tardía: Twilio puede reintentar.
Un fallo del broker no se confirma con 200 ni se manda a DLQ en el mismo broker
caído; se conserva el reintento HTTP.

Outbound: SDK sin reintentos adicionales, conexión 3 s y lectura 5 s. HTTP 429
y 5xx permiten tres reintentos con backoff 1/2/4 s, hasta cuatro POSTs con
presupuesto persistido entre reinicios. Validación, errores permanentes y
resultados ambiguos van directamente a DLQ. Se confirma `SENDING` antes del
POST; caída en esa ventana o timeout de transporte requiere reconciliación,
sin segundo POST automático. Si falla publicar estado después de `SENT`, solo
se reintenta el estado. Tras agotar recuperación, también puede ir a DLQ.

`DeadLetter` guarda `originalTopic`, `originalPartition`, `originalOffset`,
`originalValueBase64` y `errorCode`. El `messageId` de DLQ es técnico
`topic:partition:offset`, no el ID de negocio. Categorías: `SCHEMA_VALIDATION_FAILED`,
`NON_RETRYABLE_ERROR`, `RETRIES_EXHAUSTED`. El gateway añade headers seguros
`correlationId`, `eventType=DeadLetter`, `eventVersion=1`, `errorCode` y
`providerErrorCode` (código Twilio o interno, sin texto de excepción).
Este último es una extensión del gateway al listado general H0.1.
El offset original solo se confirma después de confirmar Kafka para la DLQ;
si DLQ falla, no se descarta el original.

Procedimiento para soporte, complementario a la
[guía H0.1](../events/README.md#reproceso-de-dlq-guía-para-soporte):

1. Registrar incidente/autorización operativa, coordenadas y correlación, sin
   copiar contenido sensible. Leer DLQ con acceso restringido. Base64 no cifra:
   el original contiene destino/texto; aplicar TLS, ACL y retención limitada.
2. Decodificar en un entorno autorizado y validar contra el schema del topic
   original. Identificar el `messageId` de negocio y consultar el registro
   `outbound_send` y los registros Twilio mediante acceso operativo restringido.
3. Ante `SENDING`, timeout o `SEND_REQUIRES_RECONCILIATION`, comprobar si Twilio
   aceptó el mensaje antes de autorizar cualquier nuevo envío desde el productor.
   No republicar ciegamente, cambiar el ID para eludir deduplicación ni borrar
   filas. El gateway no expone endpoint de reconciliación ni desbloqueo automático
   de `FAILED`/reservas ambiguas; registrar la resolución operativa.
4. Si corresponde reproceso seguro, reconstruir clave y headers desde el sobre
   original validado, conservar ID de negocio y publicar el evento original en
   su topic, esperando confirmación. Nunca publicar `DeadLetter` como outbound
   ni reenviar la DLQ a sí misma. Reparaciones requieren registrar la relación
   con el original; no mover offsets del grupo de negocio.
5. Verificar el efecto o duplicado y anotar coordenadas/resultados. En `SENT`,
   replay recupera publicación de estado sin reenviar a Twilio. Un registro
   `FAILED` o sin presupuesto no se vuelve enviable por republicarlo. Si la
   DLQ está caída, restaurarla y alertar antes de continuar recuperación.

## Observabilidad y verificación

Logs JSON ECS con MDC; nunca formularios, cuerpos, teléfonos, firma ni token.
Métricas HTTP: `gateway_webhook_requests_total{endpoint,status}` y
`gateway_webhook_duration_seconds`. Los 200 incluyen duplicados, no equivalen
a publicaciones. Outbound: `gateway.outbound.consumed`, `.sent`, `.errors`
(kind), `.dlq` (code), además de métricas Kafka/JVM. No usar datos de clientes
como etiquetas. Ver [OBSERVABILITY.md](../../../gateway/wa-twilio-gateway/OBSERVABILITY.md).

Comandos desde la raíz (Windows):

```powershell
Push-Location gateway/wa-twilio-gateway
.\mvnw.cmd -q -B clean verify
Pop-Location
python scripts/verify_integracion.py
```

`clean verify` ejecuta tests y gate JaCoCo de líneas ≥80%, sin reducirlo.
La verificación del equipo descubre gateway y plataforma. Revisar los reportes
`target/surefire-reports` y `target/site/jacoco/index.html` del componente.
Las suites existentes cubren firma real de prueba/rechazo sin efectos, ACK,
deduplicación/expiración, normalización/schema/headers, orden, errores Kafka,
WireMock Twilio (OK, 4xx, 429, 5xx, timeout), persistencia, recuperación y DLQ.
Los tests KRaft embebidos inbound/outbound permiten verificar flujos sin Docker.

Con Docker accesible, exigir ejecución de los tests `@Tag("integration")` y
revisar que no se omitan:

```powershell
Push-Location gateway/wa-twilio-gateway
.\mvnw.cmd -q -B "-Dtest=InboundKafkaIntegrationTest,OutboundKafkaIntegrationTest" test
Pop-Location
```

Testcontainers usa `apache/kafka:3.9.1` inbound y `confluentinc/cp-kafka:7.8.0`
outbound. Se omiten automáticamente sin Docker; un build verde con omisiones
no prueba esos contenedores. En el despliegue comprobar URLs/firma, volumen y
permisos, conectividad y ACL, webhook firmado con datos sintéticos, publicación
única ante duplicado y correlación hasta status; medir ACK con Kafka real.
No se requiere Twilio real para las suites automatizadas.

Verificación de TASK-85 (2026-10-28): `mvnw.cmd -q -B clean verify` del gateway
finalizó con código 0; Surefire informó 91 pruebas, 0 fallos, 0 errores y 3
omitidas (las suites Testcontainers por Docker no disponible). JaCoCo: 494/524
líneas cubiertas, 94,27 %. Los 12 enlaces locales de este documento resuelven
y el archivo es UTF-8 sin BOM. La ejecución pendiente de contenedores se realiza
con el comando específico anterior en un entorno con Docker accesible.
`python scripts/verify_integracion.py` también finalizó con código 0:
verificación correcta de los dos componentes (gateway y plataforma de eventos).
