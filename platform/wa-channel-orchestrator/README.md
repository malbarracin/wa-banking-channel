# wa-channel-orchestrator

Base de H0.2: Java 21, Spring Boot 3.5.16, Maven Wrapper, Kafka y PostgreSQL con JPA/Flyway.
Paquete com.bcra.orchestrator y puerto 8091: valores de arquitectura, al faltar integracion en project.yaml.

## Ejecutar

Definir las variables de .env.example en el entorno (Spring no carga .env automáticamente).
Se requiere PostgreSQL accesible con base creada y credenciales propias.
El perfil local usa Kafka PLAINTEXT; fuera de local configurar TLS/SASL y ACL por topic,
con credenciales y truststores externos mediante configuración Spring Kafka.
No exponer Actuator fuera de la red interna.

Desde esta carpeta:

```sh
./mvnw -q -DskipTests compile
./mvnw -q -B clean verify
./mvnw spring-boot:run
```

En PowerShell usar .\mvnw.cmd. Desde la raíz: python scripts/verify_integracion.py.
Actuator: /actuator/health, /actuator/info y /actuator/prometheus.
Logs ECS incorporan correlationId cuando está presente en MDC.

## Alcance

Estructura de paquetes según specs/orquestador-canal/arquitectura.md.
La configuración prepara productor idempotente, consumidores manuales y Flyway.
Las migraciones, listeners, validación de esquemas, outbox, sagas y cliente H9
corresponden a tareas posteriores. Los esquemas existentes se empaquetan en contracts/events.
La prueba de arranque excluye infraestructura externa; no verifica conectividad Kafka/PostgreSQL.
