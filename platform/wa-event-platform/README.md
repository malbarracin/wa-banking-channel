# Plataforma de eventos

Requiere Java 21. Ejecutar `./mvnw spring-boot:run` (en Windows, `mvnw.cmd spring-boot:run`). El puerto y los brokers se configuran mediante las variables de `.env.example`; el perfil predeterminado es `local` y también están disponibles `dev` y `test`. La aplicación expone salud, información y métricas Prometheus mediante Actuator.

Para desarrollo local, iniciar Kafka KRaft con `docker compose up -d` desde esta carpeta; escucha solamente en `localhost:9092`. Detenerlo con `docker compose down`. La aplicación crea los topics al iniciar. El consumidor de referencia se registra únicamente en `local`, `dev` y `test`, utiliza su propio grupo y confirma la recepción sin realizar efectos de negocio. Valida el contrato y usa deduplicación en memoria, que no sustituye la idempotencia transaccional de un consumidor de producción. En entornos compartidos Kafka requiere TLS, SASL/SCRAM y ACL, y los topics se provisionan externamente.

Ejecutar `./mvnw -q -B clean verify` para compilar y comprobar la cobertura. Los contratos se copian desde `shared/contracts/events` cuando están disponibles.
# Idempotencia de referencia

El consumidor de ejemplo usa memoria con vencimiento de 24 horas y un límite de entradas. Sus registros se pierden
al reiniciar y no están vinculados transaccionalmente a efectos: los consumidores reales deben guardar la deduplicación
en la misma transacción que su efecto.

## Operación de la DLQ

Los fallos transitorios se reintentan en la partición cuatro veces, con esperas de 0,5, 1, 2 y 4 segundos; los errores de contrato van directamente a `wa.message.dlq.v1`. El runbook completo (política de reintentos, headers y reproceso) está en `shared/contracts/wa-event-platform/handoff.md`.

Antes de reprocesar, identificar la partición y el rango de offsets, examinar la causa sin divulgar datos personales y corregir el problema de origen. Un evento inválido por esquema debe corregirse en el productor o descartarse con registro de la decisión; no se reprocesa en masa.

Con la aplicación compilada y la configuración de Kafka del entorno, ejecutar desde esta carpeta:

```sh
java -cp "target/classes:target/dependency/*" com.bcra.events.messaging.dlq.DlqReprocessor 0 10 20
```

Preparar el classpath una vez con `./mvnw dependency:copy-dependencies`. Los argumentos son partición, offset inicial inclusivo y offset final exclusivo (máximo 1000 posiciones). La herramienta valida el rango completo antes de publicar, conserva clave, valor y headers del contrato, y agrega `x-dlq-reprocessed-from`. No borra la DLQ. La publicación puede interrumpirse parcialmente; al repetir, los consumidores deben deduplicar por `messageId` en la misma transacción que el efecto. Inspeccionar el resultado antes de repetir.
