# wa-users-service

Servicio de usuarios del canal WhatsApp del banco. Base Java 21 / Spring Boot 3, Maven, JPA y PostgreSQL.
Esta entrega prepara la aplicación y el contrato global de errores; los endpoints de negocio se agregan después.

## Compilar y verificar

Requiere JDK 21. El wrapper descarga Maven 3.9.16 la primera vez (requiere acceso a Maven Central).

```sh
./mvnw clean verify
./mvnw -q -B verify
```

En PowerShell usar `.\mvnw.cmd clean verify`. Los tests activan el perfil `test`, con H2 en memoria.
JaCoCo exige al menos 80% de cobertura de líneas en `verify`; reporte en `target/site/jacoco/index.html`.

## Ejecutar

Configurar `DB_URL` (por defecto `jdbc:postgresql://localhost:5432/wa_users`), `DB_USERNAME` y `DB_PASSWORD`.
Las credenciales no tienen valores por defecto. No guardar secretos en el repositorio.
`JPA_DDL_AUTO` vale `validate` por defecto; para desarrollo se puede usar `update`.

```sh
./mvnw -DskipTests package
java -jar target/wa-users-service-0.1.0-SNAPSHOT.jar
```

Puerto por defecto 8080 (`PORT`). Actuator expone `/actuator/health` y `/actuator/info`.
Swagger UI: `/swagger-ui.html`; OpenAPI: `/v3/api-docs`. La consola H2 está deshabilitada.

## Estructura y errores

Identidad: `com.bank` / `com.bank.users`. Los futuros módulos usan subpaquetes por capa:
`user/{controller,service,repository,mapper,entity,dto,exception}` y `audit/{service,repository,entity}`.
La API se ubicará bajo `/api/v1`, sin paquetes versionados. No hay endpoints de negocio en esta base.

`exception/` contiene el contrato `{code,message,details,timestamp}`. `details` siempre es una lista;
`timestamp` es UTC. Códigos: 400 `VALIDATION_ERROR`/`BAD_REQUEST`, 404 `NOT_FOUND`, 409 `CONFLICT`,
500 `INTERNAL_ERROR`. Los errores inesperados y de parseo usan mensajes genéricos.
Las excepciones de dominio extienden `NotFoundException` o `ConflictException` con mensajes públicos seguros.
