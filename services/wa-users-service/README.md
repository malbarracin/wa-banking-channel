# wa-users-service

Servicio de usuarios bancarios. Requiere Java 21 y PostgreSQL.

Configurar `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` y, opcionalmente, `SERVER_PORT`. Ejecutar `./mvnw spring-boot:run` y verificar con `./mvnw clean verify`. Las políticas del banco se configuran bajo `bank.users`.
