# Plataforma de eventos

Requiere Java 21. Ejecutar `./mvnw spring-boot:run` (en Windows, `mvnw.cmd spring-boot:run`). El puerto y los brokers se configuran mediante las variables de `.env.example`; el perfil predeterminado es `local` y también están disponibles `dev` y `test`. La aplicación expone salud, información y métricas Prometheus mediante Actuator.

Para desarrollo local, iniciar Kafka KRaft con `docker compose up -d` desde esta carpeta; escucha solamente en `localhost:9092`. Detenerlo con `docker compose down`. La aplicación crea los topics al iniciar. El consumidor de referencia se registra únicamente en `local`, `dev` y `test`, utiliza su propio grupo y confirma la recepción sin realizar efectos de negocio. Es un ejemplo mínimo de suscripción, no un consumidor de producción: aún requiere validación de contratos, idempotencia y manejo de errores antes de aplicarlo a efectos reales. En entornos compartidos Kafka requiere TLS, SASL/SCRAM y ACL, y los topics se provisionan externamente.

Ejecutar `./mvnw -q -B clean verify` para compilar y comprobar la cobertura. Los contratos se copian desde `shared/contracts/events` cuando están disponibles.
# Idempotencia de referencia

El consumidor de ejemplo usa memoria con vencimiento de 24 horas y un límite de entradas. Sus registros se pierden
al reiniciar y no están vinculados transaccionalmente a efectos: los consumidores reales deben guardar la deduplicación
en la misma transacción que su efecto.
