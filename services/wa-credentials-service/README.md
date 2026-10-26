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
Puerto HTTP: `PORT` (8082 por defecto). El esquema no se modifica automáticamente.
El perfil dev mantiene PostgreSQL y no habilita consola H2.
Los tests activan el perfil test, que usa H2 en memoria con esquema temporal y sin servicios externos.
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

El adaptador `UsersServiceClient` consulta `GET /api/v1/users/{tipo}/{numero}/status` de H1.
Configurar `WA_USERS_SERVICE_BASE_URL` (default `http://localhost:8080`),
`WA_USERS_SERVICE_CONNECT_TIMEOUT` (default `1s`) y `WA_USERS_SERVICE_READ_TIMEOUT` (default `2s`).
Los timeouts deben ser positivos. No hay reintentos automáticos.
El 404 produce `ClienteNoEncontradoException`; errores HTTP, de transporte y respuestas inválidas
producen `UsersServiceUnavailableException`. El consumidor decide la política de su caso de uso;
una falla nunca se convierte en un cliente activo. Las excepciones omiten URL, documento y cuerpo remoto
para evitar exponer datos sensibles en logs o respuestas.
