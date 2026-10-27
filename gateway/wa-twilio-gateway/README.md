# wa-twilio-gateway

Base de H0 con Java 21 y Spring Boot 3. Paquete `com.bcra.gateway` y puerto
8090 por convención, ya que project.yaml no define integracion.gateway.

Desde esta carpeta ejecutar `./mvnw spring-boot:run` (Windows:
`.\mvnw.cmd spring-boot:run`). `SERVER_PORT` permite cambiar el puerto.
No requiere Kafka ni credenciales de Twilio para arrancar esta base.
`.env.example` enumera las variables; Spring no carga el archivo .env automáticamente.

Endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`.
Health no expone detalles y los errores no incluyen mensajes internos ni stack traces.
Los logs usan JSON ECS, que incorpora el MDC (incluido correlationId cuando se configure).
Restringir el acceso de red a Actuator en ambientes no locales.

Verificar con `./mvnw -q -B clean verify` (Windows: `.\mvnw.cmd -q -B clean verify`).
Incluye pruebas HTTP de Actuator, arranque real desde main y JaCoCo mínimo 80%.
Desde la raíz: `python scripts/verify_integracion.py` descubre este componente.

El bootstrap no implementa webhooks ni productores/consumidores. Las tareas posteriores
agregarán firma, límites, idempotencia y contratos antes de habilitar esos endpoints.
Credenciales solo por entorno; Kafka fuera de local requiere TLS, SASL y ACL por topic.
