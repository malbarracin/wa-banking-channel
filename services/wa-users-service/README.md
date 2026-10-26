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

La auditoría interna usa `user.entity.AuditLog`, `user.repository.AuditLogRepository` y
`user.service.AuditService`, manteniendo las capas del módulo `user`. No expone endpoint HTTP.
La referencia es el documento (tipo y número), sin clave foránea a User, para registrar también
altas rechazadas antes de que exista un usuario. No se registra el contenido de datos modificados.
`registrar(accion, resultado, tipoDocumento, numeroDocumento, motivo)` admite `ALTA`,
`ACTUALIZACION` y `CAMBIO_ESTADO`, con resultado `EXITOSO` o `RECHAZADO`.
El motivo es obligatorio para rechazos (hasta 255 caracteres, sin datos sensibles); para éxitos puede ser vacío.
El servicio asigna fecha UTC y persiste en `REQUIRES_NEW`, conservando la auditoría ante rollback del llamador.
Las futuras operaciones deben invocarlo a través del bean Spring al determinar su resultado;
la integración con esas operaciones corresponde a sus tareas. Producción necesita crear la tabla
`user_audit_log` antes de activar la validación del esquema.
Las consultas usan MapStruct para devolver DTOs; auditoría no utiliza DTOs.
GET `/api/v1/users/{tipo}/{numero}` devuelve el UserResponse completo y el estado actual.
El filtrado de campos por perfil queda pendiente de parametrizar cuando Compliance defina la política.
GET `/api/v1/users/{tipo}/{numero}/status` devuelve únicamente documento (tipo y número) y estado,
incluidos SUSPENDED y LOGICAL_DELETE. H3 aplica la restricción de nuevos vínculos de canal.
Ambas consultas responden 404 NOT_FOUND cuando no existe el documento, sin datos personales en el error.
Docker, migraciones y documentación completa se entregan en sus tareas correspondientes.
