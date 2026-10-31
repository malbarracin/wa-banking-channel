# Observabilidad del gateway

`/actuator/health` informa disponibilidad del proceso sin detalles internos;
no garantiza disponibilidad de Kafka ni Twilio. `/actuator/prometheus` expone
métricas JVM, HTTP y las métricas nativas del productor Kafka.

`gateway_webhook_requests_total{endpoint,status}` cuenta solicitudes inbound y
callbacks, incluidos rechazos de firma (403), límites (413/429) y fallos de
publicación (503). `gateway_webhook_duration_seconds` mide el tiempo hasta la
respuesta, incluidos duplicados. HTTP 200 incluye duplicados: no es un contador
de eventos publicados. Las métricas Kafka permiten observar envíos y errores.

El filtro genera o propaga un UUID `correlationId`, lo devuelve en el header y
lo coloca en MDC antes de medir y registrar la solicitud. El publicador inbound
propaga MDC a su executor y ambos productores incluyen la correlación en los
headers Kafka y en el evento. Los callbacks sin header reutilizan el UUID del
filtro. No se usan identificadores, direcciones ni cuerpos como etiquetas.

Los logs JSON de finalización contienen solo endpoint, estado y correlación.
Los errores inesperados devuelven 500 vacío y registran un mensaje fijo, sin
excepción, cuerpo, firma, token ni teléfonos. Actuator mantiene ocultos los
endpoints sensibles. Restringir el acceso a métricas en el despliegue; usar
TLS/SASL y ACL por topic para Kafka fuera del entorno local.

Verificación: `mvnw.cmd -q -B clean verify` en este componente y
`python scripts/verify_integracion.py` desde la raíz. El script ya descubre
`gateway/*/pom.xml` y sus tests cubren el descubrimiento y los fallos.
