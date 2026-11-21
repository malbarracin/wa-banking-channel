# Plataforma de eventos

Requiere Java 21. Ejecutar `./mvnw spring-boot:run` (en Windows, `mvnw.cmd spring-boot:run`). El puerto y los brokers se configuran mediante las variables de `.env.example`; el perfil predeterminado es `local` y también están disponibles `dev` y `test`. La aplicación expone salud, información y métricas Prometheus mediante Actuator.

Ejecutar `./mvnw -q -B clean verify` para compilar y comprobar la cobertura. Los contratos se copian desde `shared/contracts/events` cuando están disponibles.
