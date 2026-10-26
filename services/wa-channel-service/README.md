# wa-channel-service

Base del Servicio Canal WhatsApp (H3), Java 21 y Spring Boot 3. Identidad confirmada por
tech-lead en TASK-12 y registrada en `project.yaml`, bloque `services.wa-channel-service`:
groupId `com.bcra`, paquete `com.bcra.channel.ws`, puerto 8081 y base propia `wa_channel`.

Desde esta carpeta:

```sh
./mvnw -q -B clean verify
./mvnw -Pdev spring-boot:run -Dspring-boot.run.profiles=dev
```

En PowerShell usar `./mvnw.cmd`. El perfil Maven `dev` habilita H2 en ejecución local;
el perfil Spring `dev` configura la base en memoria y su consola. Los tests activan el
perfil Spring `test` y usan H2 con scope test. El artefacto normal usa PostgreSQL.
Para PostgreSQL definir `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` y ejecutar
`./mvnw spring-boot:run`. El perfil Spring `docker` usa `postgres:5432/wa_channel`
por defecto y exige usuario y contraseña por entorno.

Salud: `http://localhost:8081/actuator/health`. JaCoCo exige 80% de líneas en `verify`;
solo se excluye la clase de arranque sin lógica. El contrato de errores contempla
400, 401 UNAUTHORIZED, 404, 409, 423 LOCKED y 500 sin detalles internos.

El módulo `vinculo` seguirá los subpaquetes `controller`, `service`, `repository`,
`mapper`, `entity`, `dto`, `client` y `exception`; configuración transversal en `config`.
Las clases de dominio y endpoints se incorporan en sus tareas de implementación.
