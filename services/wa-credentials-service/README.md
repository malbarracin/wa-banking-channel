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

Disponibles `/actuator/health`, `/actuator/info`, `POST /api/v1/credenciales/validar`
y `POST /api/v1/credenciales/emitir`.
La validación recibe `{"credencial":"<JWT>"}` y devuelve identidad y vencimiento cuando es válido.
Configurar `CREDENTIAL_JWT_SECRET` con al menos 32 bytes UTF-8; no hay clave por defecto.
Sin esa configuración, el servicio no arranca.
Se verifica exclusivamente HS256, `exp`, `iat`, `jti`, `sub` (número E.164) y
`doc` (string `documentoTipo:documentoNumero`, por ejemplo `DNI:12345678`).
La identidad y el vencimiento deben coincidir con la credencial persistida activa.
Cada validación consulta H1: solo un cliente `ACTIVE` puede operar; 404, estado inactivo
o indisponibilidad rechazan con 401 `UNAUTHORIZED` y el mismo mensaje genérico.
Los rechazos registran `VALIDACION_FALLIDA` mediante una transacción independiente;
si no se reconoce una credencial firmada persistida, la identidad queda vacía.
`V002__auditoria_validacion_sin_identidad.sql` permite estos eventos sin número,
sin inventar identidad ni guardar claims no confiables o contenido del JWT.

La emisión recibe `numeroWhatsapp` (E.164), `documentoTipo`, `documentoNumero`,
`vinculoActivo` e `identidadVerificada`; las confirmaciones deben ser verdaderas y H1 debe informar ACTIVE.
Devuelve 201 con `credencial` y `fechaExpiracion`. El JWT es exclusivamente para H3:
nunca incluirlo en mensajes al cliente, logs ni auditoría.
Configurar `CREDENTIAL_JWT_SECRET` con al menos 32 bytes UTF-8 (obligatorio, sin default)
y opcionalmente `CREDENTIAL_VIGENCIA_MINUTOS` (positivo, default 1440).
El JWT firmado HS256 contiene `sub`, `doc` (`tipo:numero`), `iat`, `exp` y `jti`.
Como el contrato no recibe el UUID del vínculo H3, `vinculoId` conserva una referencia interna
estable derivada de número y documento; no representa el ID externo de H3.
La nueva emisión revoca la anterior por REEMPLAZO; ambas modificaciones y la auditoría de éxito
se confirman juntas. Los rechazos usan auditoría independiente. El índice parcial de PostgreSQL
resuelve las carreras de emisión concurrente con 409 y rollback, sin dejar dos activas.
Errores: 400 validación, 403 sin confirmaciones o cliente inactivo, 404 cliente inexistente,
503 H1 no disponible y 409 conflicto de persistencia.

Los errores usan `code`, `message`, `details` (lista nunca nula) y `timestamp` UTC.
El handler cubre validación, solicitudes inválidas, recurso inexistente, conflicto y error interno,
sin exponer valores rechazados ni mensajes internos. JaCoCo exige al menos 80% de líneas;
solo se excluye la clase de arranque de Spring Boot.

Fuente funcional: `../../specs/acceso-sesion/spec.md`.
Arquitectura: `../../specs/acceso-sesion/arquitectura.md`.

La renovación interna se ejecuta cada `CREDENTIAL_RENOVACION_INTERVALO_MS` (default 60000 ms).
`CREDENTIAL_RENOVACION_HABILITADA=false` deshabilita el planificador; el perfil test lo deshabilita.
La ventana es `CREDENTIAL_RENOVACION_VENTANA_MINUTOS` (default 30) y debe ser positiva y menor
que `CREDENTIAL_VIGENCIA_MINUTOS` para evitar rotaciones sucesivas en cada ejecución.
Solo se consideran credenciales ACTIVA que aún no expiraron. ACTIVA representa vínculo activo:
la baja del vínculo llega como revocación desde H3. Con riesgo se conserva el marcador `riesgo=true`,
se audita RENOVACION RECHAZADO con `REVERIFICACION_REQUERIDA` y la validación devuelve 401.
Sin riesgo, el reemplazo REVOCADA/REEMPLAZO, la nueva ACTIVA y las auditorías se confirman juntos.
El JWT se firma con la clave configurada. Según la última decisión del arquitecto de TASK-34,
el job es exclusivamente interno: no entrega el JWT a H3 ni implementa receptor u outbox.
El token anterior deja de validar al confirmar la rotación.

Swagger UI está disponible en `http://localhost:8082/swagger-ui.html` (ajustar si se define `PORT`).
El contrato se consulta en `/v3/api-docs` y `/v3/api-docs.yaml` e incluye los cinco endpoints
de emisión, validación, revocación individual, revocación masiva e historial.
Los ejemplos JWT son ilustrativos: reemplazar `JWT_DEVUELTO_POR_EMITIR` por el valor real
obtenido de la emisión y utilizarlo solo entre servicios.
`./mvnw -q -B verify` ejecuta `OpenApiExportTest` con el perfil test y regenera
`delivery/openapi/openapi.json` y `delivery/openapi/openapi.yaml` dentro de este servicio,
sin PostgreSQL ni H1 externos. El test comprueba endpoints, respuestas, ejemplos y schemas.

El adaptador `UsersServiceClient` consulta `GET /api/v1/users/{tipo}/{numero}/status` de H1.
Configurar `WA_USERS_SERVICE_BASE_URL` (default `http://localhost:8080`),
`WA_USERS_SERVICE_CONNECT_TIMEOUT` (default `1s`) y `WA_USERS_SERVICE_READ_TIMEOUT` (default `2s`).
Los timeouts deben ser positivos. No hay reintentos automáticos.
El 404 produce `ClienteNoEncontradoException`; errores HTTP, de transporte y respuestas inválidas
producen `UsersServiceUnavailableException`. El consumidor decide la política de su caso de uso;
una falla nunca se convierte en un cliente activo. Las excepciones omiten URL, documento y cuerpo remoto
para evitar exponer datos sensibles en logs o respuestas.
