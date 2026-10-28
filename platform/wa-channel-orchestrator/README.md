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

# Orquestador del canal — persistencia (TASK-91)

Base Java 21 / Spring Boot para PostgreSQL. Paquete `com.bcra.orchestrator` y puerto 8091 según arquitectura.
Esta entrega incorpora únicamente persistencia; los consumidores, H9 y la ejecución de sagas corresponden a otras tareas.

Configurar las variables de `.env.example` en el entorno antes de arrancar con `./mvnw spring-boot:run`.
Flyway aplica V1–V5; Hibernate valida el esquema sin crearlo ni actualizarlo.

| Tabla | Entidad / repositorio | Garantías |
| --- | --- | --- |
| conversation_state | ConversationState / ConversationStateRepository | Identificador de conversación y versión optimista |
| saga_instance | SagaInstance / SagaInstanceRepository | FK conversación, estados/tipos explícitos, `@Version` |
| saga_step_execution | SagaStepExecution / SagaStepExecutionRepository | FK saga, intento positivo, paso/intento únicos |
| message_dedupe | MessageDedupe / MessageDedupeRepository | message_id globalmente único, incluso entre conversaciones |
| outbox | OutboxEvent / OutboxEventRepository | FK conversación, sobre y JSON de evento, fecha de publicación |

Los repositorios deben usarse dentro de la misma transacción del futuro servicio: estado, deduplicación y outbox
se confirman juntos. No capturar una violación de unicidad y continuar una transacción marcada para rollback.
La tabla es `message_dedupe`, siguiendo TASK-91 y la lista de migraciones de arquitectura.
El publicador de outbox y sus reintentos se implementan en su tarea correspondiente.

El contexto JSON de saga debe contener solo datos técnicos; no guardar credenciales, documentos ni respuestas
completas de H9. El payload de outbox debe ser el evento normalizado mínimo validado por su productor.
No se generan logs con entidades ni sus contenidos. `result_code` guarda códigos técnicos, nunca excepciones completas.
Los campos de estado/paso de conversación se mantienen técnicos sin definir reglas de negocio de H1–H6.

Verificación en Windows (`./mvnw` en Linux):

```powershell
.\mvnw.cmd -q -B clean verify
python ../../scripts/verify_integracion.py
```

`PersistenceTest` aplica las cinco migraciones en H2 (modo PostgreSQL), valida los mapeos JPA, recupera las cinco
entidades, rechaza duplicados globales y prueba dos transacciones con versiones de saga en conflicto.
`PostgresPersistenceIntegrationTest` ejecuta los mismos escenarios contra PostgreSQL 16.6 con Testcontainers
y etiqueta `integration`; se omite automáticamente cuando Docker no está disponible.
Para ejecutar solo PostgreSQL con Docker disponible:

```powershell
.\mvnw.cmd -q -B '-Dtest=PostgresPersistenceIntegrationTest' test
```

Si la caché Maven del usuario es de solo lectura, agregar `'-Dmaven.repo.local=.maven-cache'`.
