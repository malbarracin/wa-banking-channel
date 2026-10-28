# wa-twilio-gateway

Base de H0 con Java 21 y Spring Boot 3. Paquete `com.bcra.gateway` y puerto
8090 por convención, ya que project.yaml no define integracion.gateway.

Desde esta carpeta ejecutar `./mvnw spring-boot:run` (Windows:
`.\mvnw.cmd spring-boot:run`). `SERVER_PORT` permite cambiar el puerto.
Tras integrar el webhook, el arranque requiere `TWILIO_AUTH_TOKEN` y
`TWILIO_WEBHOOK_PUBLIC_URL` por entorno. El envío outbound requiere además cuenta,
emisor WhatsApp, URL pública del callback y Kafka; ver [OUTBOUND.md](OUTBOUND.md)
para configuración, persistencia de deduplicación, reintentos y reconciliación.
Con `gateway.outbound.enabled=false`, el arranque no requiere Kafka.
Kafka debe estar disponible para confirmar webhooks; bootstrap por `KAFKA_BOOTSTRAP_SERVERS`.
`.env.example` enumera las variables; Spring no carga el archivo .env automáticamente.

Endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`.
Health no expone detalles y los errores no incluyen mensajes internos ni stack traces.
Los logs usan JSON ECS, que incorpora el MDC (incluido correlationId cuando se configure).
Restringir el acceso de red a Actuator en ambientes no locales.

Verificar con `./mvnw -q -B clean verify` (Windows: `.\mvnw.cmd -q -B clean verify`).
Incluye pruebas HTTP de Actuator, arranque real desde main y JaCoCo mínimo 80%.
Desde la raíz: `python scripts/verify_integracion.py` descubre este componente.

## Docker (local)

No hay compose en la raíz del programa; el stack del gateway está en
`docker-compose.yml` (Kafka KRaft 3.9.1, `kafka-init` según
`shared/contracts/events/topics.md`, app con profile `app`). Puerto **8090**.
El gateway usa H2 en volumen para outbound; no necesita PostgreSQL.

```powershell
# Compose desde gateway/wa-twilio-gateway (copiá .env.example → .env y completá secretos)
docker compose up -d --wait
docker compose --profile app up -d --wait
# Imagen desde la raíz del repo:
# docker build -f gateway/wa-twilio-gateway/Dockerfile -t wa-twilio-gateway:local .
```

Webhooks reales: exponé `:8090` con un túnel (ngrok u similar) y poné la URL
HTTPS pública en `TWILIO_WEBHOOK_PUBLIC_URL` y
`TWILIO_STATUS_CALLBACK_PUBLIC_URL` (mismas rutas que en Twilio).

Webhook firmado `POST /webhook/twilio`: ver [WEBHOOK.md](WEBHOOK.md).
Normalizacion y publicacion Kafka: ver [NORMALIZATION.md](NORMALIZATION.md).
Envío outbound: ver [OUTBOUND.md](OUTBOUND.md).
El webhook integrado en `/webhook/twilio` valida firma y deduplica publicaciones
confirmadas. Sin un adaptador `InboundWebhookPublisher` responde 503 para permitir
el reintento. Las pruebas de arranque usan configuración sintética local al test;
no se incluyen credenciales por defecto en producción.
Credenciales solo por entorno; Kafka fuera de local requiere TLS, SASL y ACL por topic.
