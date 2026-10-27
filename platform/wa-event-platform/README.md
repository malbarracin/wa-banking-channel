# wa-event-platform

Base H0.1 con Java 21, Spring Boot 3, groupId `com.bcra`, paquete `com.bcra.events` y puerto 8092.
TASK-69 confirma el paquete propuesto por arquitectura; project.yaml no define integracion.

El [handoff de operación e integración](handoff.md) reúne responsabilidades de H0/H0.2,
topics y schemas, variables, arranque local, reproducción y reproceso de DLQ, métricas y
límites de verificación. Decisiones aceptadas: [Kafka backbone](../../docs/adr/ADR-001-kafka-backbone.md),
[at-least-once e idempotencia](../../docs/adr/ADR-002-at-least-once-idempotency.md) y
[partición por conversación](../../docs/adr/ADR-003-partition-by-conversation.md).

## Ejecución y verificación

Desde este directorio: `./mvnw -q -B clean verify` (Windows: `.\mvnw.cmd -q -B clean verify`).
Ejecutar el JAR con `java -jar target/wa-event-platform-0.1.0-SNAPSHOT.jar`.
Desde la raíz: `python scripts/verify_integracion.py`.
Las variables de .env.example se exportan al entorno del proceso; Spring no carga .env automáticamente.

Para desarrollo ejecutar `docker compose up -d --wait` desde este directorio y luego iniciar el JAR
en el host. El broker KRaft de un nodo escucha solo en localhost:9092 y conserva datos en un volumen.
KafkaAdmin crea los cuatro topics declarados (3 particiones, réplica 1) y actualiza su retención.
Si Kafka no está disponible el arranque falla; para pruebas sin broker usar KAFKA_ADMIN_AUTO_CREATE=false.
Retenciones: received/ready-to-send 7d, status 30d, DLQ 90d; configurables en .env.example.
El catálogo está en shared/contracts/events/topics.md. La creación implícita del broker está desactivada.
Las pruebas levantan Kafka KRaft embebido sin Docker y verifican topics, retención y orden por conversationId.
KafkaEventProducer valida antes de enviar y devuelve un futuro que el llamador debe observar antes de confirmar.
KafkaEventConsumer es una base para listeners de cada componente: valida esquema, clave y correlationId,
ejecuta el efecto con MDC y confirma únicamente tras éxito; restaura el MDC incluso ante errores.
Cada componente debe deduplicar messageId en la misma transacción de su efecto (H0/H0.2).
No hay listeners de negocio activos en H0.1.
DefaultErrorHandler se aplica a la factory autoconfigurada: tres reintentos con backoff exponencial
de 1000 ms hasta 10000 ms, configurables por las variables KAFKA_CONSUMER_* de .env.example.
InvalidEventException no se reintenta. DeadLetterPublisher espera confirmación de Kafka y propaga fallos,
para evitar recuperar el registro si no se pudo publicar en DLQ.
La DLQ cumple su esquema DeadLetter, conserva originalValueBase64 para reproceso y coordenadas originales;
solo publica headers correlationId, eventType, eventVersion y errorCode, sin mensajes de excepción ni stack traces.
Si falta correlationId válido, deriva uno estable de topic/partición/offset; messageId identifica esas coordenadas.
La DLQ requiere los mismos controles de acceso y protección de datos que los topics originales: Base64 no cifra.
La configuración base usa String, acks=all, idempotencia, snappy, commit manual y read_committed.
Actuator integra métricas de clientes Kafka cuando hay productores/consumidores activos;
las observaciones de template y listeners están habilitadas.

## Estructura y observabilidad

Paquetes messaging/{producer,consumer,config,schema}, topic, dlq, config y observability
reservados mediante package-info.java. Lombok y MapStruct preparados con sus procesadores.
Actuator expone /actuator/health, /actuator/info y /actuator/prometheus.
Logs ECS JSON: las entradas MDC, incluido correlationId cuando se propague en mensajería,
se incorporan al log estructurado.

## Observabilidad de TASK-73

El filtro HTTP acepta el header `correlationId` si tiene formato UUID; si falta o es inválido genera uno.
Devuelve el identificador en el header de respuesta y lo mantiene en MDC durante el request,
restaurando el contexto anterior incluso ante errores. El llamador debe conservar el mismo UUID en el
sobre del evento: KafkaEventProducer lo copia a headers y KafkaEventConsumer lo verifica y coloca en MDC.
No se copian cuerpos, credenciales ni otros headers al MDC.

Consultar `/actuator/health`, `/actuator/info` y `/actuator/prometheus` en el puerto de la aplicación.
Los logs usan ECS JSON e incluyen `correlationId` desde MDC; no registrar payloads ni mensajes de excepciones
que puedan contener datos del cliente. No usar identificadores de conversación o correlación como etiquetas.

Spring Boot registra los clientes creados por sus factories con Micrometer. Con consumidores activos,
`kafka_consumer_fetch_manager_records_lag_max` muestra el máximo lag del cliente y
`kafka_consumer_fetch_manager_records_consumed_total` el volumen consumido. Estas series aparecen después
de crear el cliente y recibir sus métricas; H0.1 no ejecuta listeners de negocio ni inventa lag cero.
Clientes creados manualmente necesitan `KafkaClientMetrics` con cierre al destruirlos.
`platform_kafka_delivery_errors_total` cuenta cada intento de consumo fallido, incluidos reintentos;
`platform_kafka_dlq_published_total` cuenta recuperaciones confirmadas y `platform_kafka_dlq_errors_total`
recuperaciones fallidas. Los contadores se conectan al DefaultErrorHandler y no contienen etiquetas de PII.
El contador de publicaciones es acumulativo, no representa el tamaño actual de la DLQ.

Las pruebas cubren HTTP/MDC, endpoints, lag nativo y contadores del error handler. La propagación por H0,
H0.2, H9 y Twilio requiere pruebas del canal completo cuando esos componentes estén disponibles.

## Seguridad de despliegue

PLAINTEXT es exclusivamente para desarrollo local. Antes de desplegar fuera de local,
configurar TLS + SASL y ACL por topic y componente, con credenciales inyectadas por entorno
(por ejemplo SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL, SPRING_KAFKA_PROPERTIES_SASL_MECHANISM
y SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG); nunca almacenarlas ni registrarlas.
Actuator debe quedar en la red interna. No hay endpoints de negocio ni deserialización de tipos arbitrarios.
No enviar secretos ni PII innecesaria en eventos o logs.
