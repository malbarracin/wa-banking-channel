# wa-users-service

Base del servicio de usuarios bancarios (Java 21, Spring Boot 3, Maven y PostgreSQL).
La identidad Java es `com.bcra:wa-users-service`, paquete `com.bcra.users.ws`, según
`project.yaml`. Los endpoints de negocio se implementan en tareas posteriores bajo `/api/v1/users`.

Desde esta carpeta:

```sh
./mvnw -q compile
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

En PowerShell usar `.\mvnw.cmd` en lugar de `./mvnw`. Se requiere JDK 21; el wrapper
descarga Maven 3.9.16 en el primer uso. Para ejecutar las verificaciones desde la raíz:
`services/wa-users-service/mvnw -f services/wa-users-service/pom.xml -q -B verify`.

El perfil default y `dev` usan PostgreSQL: configurar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.
La URL local por defecto es `jdbc:postgresql://localhost:5432/wa_users`; no hay contraseña incorporada.
`dev` permite actualizar el esquema; `prod` requiere las tres variables y valida el esquema existente.
Activar producción con `SPRING_PROFILES_ACTIVE=prod`.
El puerto se configura mediante `PORT` (8080 por defecto).

El perfil `test` usa H2 en memoria en modo PostgreSQL y recrea el esquema.
H2 tiene scope test, no se empaqueta en el JAR ni está disponible al ejecutar producción o dev.
La consola H2 permanece deshabilitada en todos los perfiles.
Los tests cargan el perfil test automáticamente. `spring.jpa.open-in-view` está deshabilitado.

Actuator expone `/actuator/health` y `/actuator/info`.
El contrato común de errores contiene `code`, `message`, `details` y `timestamp` UTC;
no incluye stacktraces. JaCoCo exige un mínimo de 80% de líneas en `verify`, sin exclusiones.

Las capas del módulo `user` se documentan con `package-info.java`; todavía no contienen
entidades, repositorios ni endpoints de negocio. Lombok y MapStruct quedan preparados para esas tareas.
Docker, migraciones y documentación completa se entregan en sus tareas correspondientes.
