# wa-accounts-service

Base del servicio de cuentas H4: Java 21, Spring Boot 3, JPA y PostgreSQL.
Identidad `com.bcra.accounts.ws` (from tech-lead confirmation — pattern <group_id>.<dominio>.ws).
`project.yaml` conserva la identidad de H1.

Desde esta carpeta: `./mvnw clean verify` compila, ejecuta tests con H2 en memoria y verifica JaCoCo ≥80%.
En PowerShell usar `./mvnw.cmd clean verify`.

Para iniciar: configurar PostgreSQL con la base `wa_accounts` y las variables `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`; ejecutar `./mvnw spring-boot:run`. Puerto por defecto: 8084 (`PORT`).
El esquema se valida por defecto; las entidades y migraciones corresponden a las tareas de dominio.
Actuator: `/actuator/health`; Swagger UI: `/swagger-ui.html`.

La validación de credenciales usa H2 (servicio wa-credentials-service), configurable mediante
`CREDENTIALS_SERVICE_BASE_URL` (http://localhost:8082), `CREDENTIALS_CONNECT_TIMEOUT` (2s) y
`CREDENTIALS_READ_TIMEOUT` (5s). H2 en memoria es únicamente la base de datos de tests.
Los endpoints y el cliente de credenciales se implementan en las tareas posteriores.
