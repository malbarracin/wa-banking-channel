# wa-event-platform

Base H0.1 con Java 21, Spring Boot 3, groupId `com.bcra`, paquete `com.bcra.events` y puerto 8092.
TASK-69 confirma el paquete propuesto por arquitectura; project.yaml no define integracion.

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
La publicación validada, listeners de negocio y retry/DLQ se implementan en tareas posteriores.
La configuración base usa String, acks=all, idempotencia, snappy, commit manual y read_committed.
Actuator integra métricas de clientes Kafka cuando hay productores/consumidores activos;
las observaciones de template y listeners están habilitadas.

## Estructura y observabilidad

Paquetes messaging/{producer,consumer,config,schema}, topic, dlq, config y observability
reservados mediante package-info.java. Lombok y MapStruct preparados con sus procesadores.
Actuator expone /actuator/health, /actuator/info y /actuator/prometheus.
Logs ECS JSON: las entradas MDC, incluido correlationId cuando se propague en mensajería,
se incorporan al log estructurado.

## Seguridad

PLAINTEXT es exclusivamente para desarrollo local. Antes de desplegar fuera de local,
configurar TLS + SASL y ACL por topic y componente, con credenciales inyectadas por entorno
(por ejemplo SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL, SPRING_KAFKA_PROPERTIES_SASL_MECHANISM
y SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG); nunca almacenarlas ni registrarlas.
Actuator debe quedar en la red interna. No hay endpoints de negocio ni deserialización de tipos arbitrarios.
No enviar secretos ni PII innecesaria en eventos o logs.
