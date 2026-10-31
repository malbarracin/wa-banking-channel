# Consumo inbound (TASK-92)

`InboundEventConsumer` consume `wa.message.received.v1` con la factory de Spring Boot:
ACK manual inmediato, auto commit desactivado y concurrencia 3 (tres particiones).
La factory incorpora el bean `kafkaErrorHandler` de TASK-97. No hay procesamiento
asíncrono ni topics de retry: cada partición se procesa secuencialmente, incluso
durante los reintentos. El productor debe conservar la clave y el número de
particiones para mantener el orden; una clave distinta de `conversationId` se rechaza.

El schema compartido se carga desde el classpath y valida también formatos UUID y
fecha. Los headers `correlationId`, `eventType`, `eventVersion` deben coincidir con
el sobre. Un evento inválido lanza `InvalidEventException`: DLQ sin reintentos,
sin copiar contenido privado, con la política existente de TASK-97.

`DedupeService.process` hace `INSERT ... ON CONFLICT DO NOTHING` sobre
`message_dedupe` (tabla existente de TASK-91, sin renombrar). La restricción única
es global por `message_id`, incluso entre conversaciones diferentes. PostgreSQL
serializa las reclamaciones concurrentes de la misma clave. La migración V6 amplía
los identificadores a los 256 caracteres admitidos por el contrato de eventos.

Para integrar los próximos pasos de orquestación, registrar un `@EventListener`
**síncrono** de `InboundMessage`. Se ejecuta dentro de la misma transacción que
la deduplicación: debe escribir estado/outbox ahí y propagar los fallos. No usar
`@Async`, multicaster asíncrono ni listeners posteriores al commit para esos efectos.
El DTO contiene el contenido del turno solo en memoria y oculta sus campos en
`toString()`. Esta tarea no implementa conversación ni llamadas a H9.

Si falla el efecto o el commit, no hay ACK y la deduplicación se revierte. Una
reentrega posterior al commit se confirma sin repetir el efecto. El ACK está fuera
del proxy transaccional. El MDC conserva/restaura la correlación previa.

Verificación desde esta carpeta:

```powershell
.\mvnw.cmd -q -B clean verify -Dmaven.repo.local=../../.maven-cache
```

`InboundEventConsumerTest` prueba contrato, headers, clave, MDC y ACK.
`InboundTransactionTest` prueba commit, rollback, reentrega e identificadores largos
con H2 en modo PostgreSQL. `InboundKafkaIntegrationTest` repite las pruebas
transaccionales en PostgreSQL y prueba Kafka real, orden, duplicado, DLQ y
reclamaciones concurrentes; está etiquetada `integration` y se omite sin Docker.
No se cambian los umbrales de JaCoCo.
