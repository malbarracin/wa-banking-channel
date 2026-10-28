# wa-twilio-gateway

Base de H0 con Java 21 y Spring Boot 3. Paquete `com.bcra.gateway` y puerto
8090 por convención, ya que project.yaml no define integracion.gateway.

Desde esta carpeta ejecutar `./mvnw spring-boot:run` (Windows:
`.\mvnw.cmd spring-boot:run`). `SERVER_PORT` permite cambiar el puerto.
Requiere `TWILIO_AUTH_TOKEN` y `TWILIO_WEBHOOK_PUBLIC_URL` para arrancar.
Kafka debe estar disponible para confirmar webhooks; bootstrap por `KAFKA_BOOTSTRAP_SERVERS`.
`.env.example` enumera las variables; Spring no carga el archivo .env automáticamente.

Endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`.
Health no expone detalles y los errores no incluyen mensajes internos ni stack traces.
Los logs usan JSON ECS, que incorpora el MDC (incluido correlationId cuando se configure).
Restringir el acceso de red a Actuator en ambientes no locales.

Verificar con `./mvnw -q -B clean verify` (Windows: `.\mvnw.cmd -q -B clean verify`).
Incluye pruebas HTTP de Actuator, arranque real desde main y JaCoCo mínimo 80%.
Desde la raíz: `python scripts/verify_integracion.py` descubre este componente.

Webhook firmado `POST /webhook/twilio`: ver [WEBHOOK.md](WEBHOOK.md).
Normalizacion y publicacion Kafka: ver [NORMALIZATION.md](NORMALIZATION.md).
Los limites HTTP y el flujo outbound corresponden a tareas posteriores.
Credenciales solo por entorno; Kafka fuera de local requiere TLS, SASL y ACL por topic.
