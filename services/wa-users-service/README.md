# wa-users-service

Servicio de usuarios bancarios del canal WhatsApp. Base de H1 con Java 21, Spring Boot 3,
Spring MVC y JPA/PostgreSQL. Identidad `com.bank:wa-users-service`, paquete `com.bank.users`
según `project.yaml`. Los endpoints de negocio se incorporan en las siguientes tareas.

Requiere JDK 21. El wrapper descarga Maven automáticamente (requiere red en la primera ejecución).

```sh
./mvnw clean verify
./mvnw spring-boot:run
```

En PowerShell usar `./mvnw.cmd clean verify` y `./mvnw.cmd spring-boot:run`.
Para ejecutar, configurar `DB_USERNAME` y `DB_PASSWORD` en el entorno; `DB_URL` es opcional
(por defecto `jdbc:postgresql://localhost:5432/wa_users`). PostgreSQL debe estar disponible.
El esquema se valida por defecto; DevOps completará la configuración por entorno.

Puerto por defecto: 8080 (`PORT` opcional). Salud: `/actuator/health`; información:
`/actuator/info`; Swagger UI: `/swagger-ui.html`; OpenAPI: `/v3/api-docs`.
Las pruebas usan H2 en memoria y no requieren PostgreSQL. `verify` exige cobertura de líneas
JaCoCo de al menos 80%; reporte en `target/site/jacoco/index.html`.

Los errores usan `code`, `message`, `details` (lista) y `timestamp` UTC:
400 `VALIDATION_ERROR`/`BAD_REQUEST`, 404 `NOT_FOUND`, 409 `CONFLICT`, 500 `INTERNAL_ERROR`.
No se devuelven valores rechazados ni detalles internos de excepciones.
