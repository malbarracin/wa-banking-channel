# wa-insurance-service

Servicio independiente de seguros (H6), Java 21 y Spring Boot 3.
Paquete base: `com.bcra.insurance.ws`. PostgreSQL: `wa_insurance`; puerto: 8085.

Desde esta carpeta:

- Compilar: `./mvnw -q compile`.
- Probar y verificar cobertura mínima del 80%: `./mvnw clean verify`.
- Ejecutar: `./mvnw spring-boot:run`.
- En PowerShell usar `.\mvnw.cmd` en los comandos anteriores.

Configurar `DB_PASSWORD` y, si corresponde, `DB_URL`, `DB_USERNAME` y `PORT`.
El esquema PostgreSQL debe existir; Hibernate valida por defecto (`JPA_DDL_AUTO`).
Los tests usan H2 sin requerir PostgreSQL.

Salud: `GET /actuator/health`. Información: `GET /actuator/info`.
Los módulos de negocio se incorporarán bajo `seguro/{controller,service,repository,mapper,entity,dto}`
en las tareas siguientes, según la arquitectura.
