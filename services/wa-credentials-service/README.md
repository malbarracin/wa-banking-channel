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

Disponibles `/actuator/health`, `/actuator/info` y `POST /api/v1/credenciales/validar`.
Este último recibe `{"credencial":"<JWT>"}` y devuelve identidad y vencimiento cuando es válido.
Configurar `CREDENTIAL_JWT_SECRET` con al menos 32 bytes UTF-8; no hay clave por defecto.
Sin esa configuración, la validación falla de forma segura con error interno.
Se verifica exclusivamente HS256, `exp`, `iat`, `jti`, `sub` (número E.164) y
`doc` (string `documentoTipo:documentoNumero`, por ejemplo `DNI:12345678`).
La identidad y el vencimiento deben coincidir con la credencial persistida activa.
Cada validación consulta H1: solo un cliente `ACTIVE` puede operar; 404, estado inactivo
o indisponibilidad rechazan con 401 `UNAUTHORIZED` y el mismo mensaje genérico.
Los rechazos registran `VALIDACION_FALLIDA` mediante una transacción independiente;
si no se reconoce una credencial firmada persistida, la identidad queda vacía.
`V002__auditoria_validacion_sin_identidad.sql` permite estos eventos sin número,
sin inventar identidad ni guardar claims no confiables o contenido del JWT.

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
