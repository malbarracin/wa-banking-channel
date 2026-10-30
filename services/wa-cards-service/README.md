# wa-cards-service

Base del servicio de tarjetas H5. Java 21, Spring Boot 3.5.16 y PostgreSQL.
Identidad `com.bcra.cards.ws` confirmada por tech-lead para TASK-103, según
`specs/tarjetas/arquitectura.md:7`. Puerto por defecto: 8086.

## Compilar y verificar

Desde esta carpeta: `./mvnw -q compile` y `./mvnw clean verify`.
En Windows usar `.\mvnw.cmd`. Las pruebas usan H2 y comprueban el arranque
del contexto y el contrato HTTP de errores. JaCoCo exige 80% de líneas;
se excluye solamente el punto de entrada, que delega en Spring.

## Ejecutar

Configurar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`, luego ejecutar
`./mvnw spring-boot:run`. DB_URL por defecto:
`jdbc:postgresql://localhost:5432/wa_cards`.
El perfil `dev` (por defecto) actualiza el esquema.
Para producción definir `SPRING_PROFILES_ACTIVE=prod`: valida un esquema
previamente provisionado. El perfil `test` usa H2, disponible solo en pruebas.
Salud: `/actuator/health`; información: `/actuator/info`.

Las futuras funcionalidades se ubican en `tarjeta/{controller,service,repository,mapper,entity,dto}`.
Este bootstrap incluye el contrato común de errores, sin endpoints de negocio.
