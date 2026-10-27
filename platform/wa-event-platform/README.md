# wa-event-platform

Base H0.1 con Java 21, Spring Boot 3, groupId `com.bcra`, paquete `com.bcra.events` y puerto 8092.
TASK-69 confirma el paquete propuesto por arquitectura; project.yaml no define integracion.

## Ejecución y verificación

Desde este directorio: `./mvnw -q -B clean verify` (Windows: `.\mvnw.cmd -q -B clean verify`).
Ejecutar el JAR con `java -jar target/wa-event-platform-0.1.0-SNAPSHOT.jar`.
Desde la raíz: `python scripts/verify_integracion.py`.
Las variables de .env.example se exportan al entorno del proceso; Spring no carga .env automáticamente.

El arranque y las pruebas de base no necesitan un broker. Para desarrollo, configurar
KAFKA_BOOTSTRAP_SERVERS con un broker Kafka en modo KRaft, sin ZooKeeper.
La creación automática de topics está desactivada. Topics, contratos, retry/DLQ y contenedores
se implementan en las tareas siguientes; esta base no publica ni consume eventos.

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
