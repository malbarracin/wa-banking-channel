# wa-credentials-service

Base del Servicio de Acceso y Sesión (H2) del canal WhatsApp. Identidad: `com.bcra`,
paquete `com.bcra.credentials.ws`, Java 21 y Spring Boot 3.5.16, como los servicios existentes.

Desde esta carpeta, con JDK 21:

```sh
./mvnw -q compile
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

En PowerShell usar `.\mvnw.cmd` con los mismos argumentos.
El wrapper descarga Maven 3.9.16 desde Maven Central.

El perfil default y el perfil dev usan PostgreSQL: crear una base propia `wa_credentials`
y definir `DB_PASSWORD`; opcionalmente `DB_URL` y `DB_USERNAME`.
Puerto HTTP: `PORT` (8082 por defecto). Flyway aplica las migraciones versionadas de
`src/main/resources/db/migration` al arrancar; Hibernate no modifica el esquema.
`V001__credencial.sql` crea las credenciales y su auditoría e impone un único `jti` y
una única credencial `ACTIVA` por número mediante un índice parcial PostgreSQL.
El perfil dev mantiene PostgreSQL y no habilita consola H2.
Los tests activan el perfil test, que usa H2 en memoria con esquema temporal y sin servicios externos.
En ese perfil Flyway está desactivado: H2 no soporta el índice parcial, cuya validación requiere PostgreSQL.
H2 es dependencia exclusiva de test; para ejecutar la aplicación localmente usar dev con PostgreSQL.

Disponibles `/actuator/health` y `/actuator/info`. No se implementan aún endpoints de negocio.
Las futuras rutas del módulo credencial estarán bajo `/api/v1/credenciales`.
Los paquetes por capa están declarados con `package-info.java`, sin clases de negocio vacías.

Los errores usan `code`, `message`, `details` (lista nunca nula) y `timestamp` UTC.
El handler cubre validación, solicitudes inválidas, recurso inexistente, conflicto y error interno,
sin exponer valores rechazados ni mensajes internos. JaCoCo exige al menos 80% de líneas;
solo se excluye la clase de arranque de Spring Boot.

Fuente funcional: `../../specs/acceso-sesion/spec.md`.
Arquitectura: `../../specs/acceso-sesion/arquitectura.md`.
