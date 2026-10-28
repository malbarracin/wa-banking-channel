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

## Retry y DLQ

Spring Boot conecta el bean `DefaultErrorHandler` con su factory de listeners por defecto.
Los listeners que creen una factory propia deben configurar ese mismo handler.
El backoff exponencial usa 3 reintentos, intervalo inicial 1000 ms y máximo 10000 ms;
se configura con `orchestrator.kafka.retry.max-retries`, `initial-interval` y `max-interval`.
`InvalidEventException` (contrato inválido) y `ValidationException` no se reintentan,
incluidas sus causas envueltas. El consumidor debe traducir el fallo de schema a esa excepción.
La recuperación confirma el offset solo después de publicar exitosamente, con MANUAL_IMMEDIATE.
Un fallo de publicación mantiene el registro disponible para reentrega.

`wa.message.dlq.v1` recibe un sobre DeadLetter v1 con coordenadas originales y código de error.
`originalValueBase64` queda vacío intencionalmente: ni claves, contenido, headers arbitrarios,
mensajes de excepción ni stack traces se copian a la DLQ. `conversationId` y clave son un UUID
derivado de las coordenadas; correlationId conserva un header UUID válido o usa el mismo derivado.
La recuperación registra correlationId en MDC y restaura el contexto previo; el contador
`orchestrator.kafka.dlq.published` incrementa solo tras publicación confirmada.
Evitar habilitar DEBUG de Spring Kafka en producción: sus diagnósticos pueden incluir el registro original.

Para reprocesar: soporte autorizado consulta el registro original por topic/partición/offset,
verifica la causa y corrige el productor o servicio antes de republicar al topic original,
con su clave de conversación y headers de contrato, conservando messageId y correlationId.
No republicar el sobre DeadLetter como inbound. El reproceso requiere que el original siga retenido;
la DLQ tiene 90 días y el inbound 7 días según H0.1. Tras esa retención requiere recuperación
desde una fuente autorizada. Aplicar TLS/SASL y ACL de soporte y del productor sobre DLQ.

Las pruebas de resiliencia usan el handler y recoverer reales con transporte simulado;
`KafkaDlqIntegrationTest`, etiquetada `integration`, verifica además la publicación en Kafka KRaft
con Testcontainers (se omite sin Docker). Ejecutar `./mvnw -q -B verify -Dgroups=integration`
en un entorno con Docker. No verifica commits de un listener de negocio, que pertenece a otra tarea.
