# Verificación TASK-80 — tester

Se verificaron los cuatro criterios con MockMvc y firma HMAC-SHA1 calculada independientemente del SDK: formulario POST, firma válida, rechazo 403 sin publicar y ACK inferior a un segundo con publicación confirmada. Se agregaron pruebas de cuerpo alterado conservando la firma original y de reintento concurrente durante una publicación pendiente (503, luego 200 y un único efecto).

Resultado: 12 tests correctos; JaCoCo 64/67 líneas (95,52 %), gate de 80 % correcto. No se modificó código de producto ni se usaron esperas fijas.

Esta copia carece de POM y wrapper del gateway (TASK-79). Se ejecutó un arnés temporal en el directorio del gateway, con parent Spring Boot 3.5.16, Java 21, Twilio 10.6.3, starters web/test y JaCoCo 0.8.13, usando el wrapper existente:

```powershell
.\platform\wa-event-platform\mvnw.cmd -q -B -f gateway/wa-twilio-gateway/test-harness.xml "-Dmaven.repo.local=$env:TEMP/task80-maven-repository" clean verify
```

Salida: código 0, 12 tests sin fallos. El arnés se retiró después de verificar; no sustituye la base de TASK-79.

`python scripts/verify_integracion.py`: código 0, «Verificación correcta: 1 componente(s)». Ejecuta la plataforma existente; no detecta el gateway sin POM.

Pendiente de integración: ejecutar `gateway/wa-twilio-gateway/mvnw.cmd -q -B clean verify` tras TASK-79 y medir latencia con Kafka real tras TASK-81. Contratos de eventos, orden y DLQ corresponden al adaptador de TASK-81; no hay productor concreto en este cambio. Los límites HTTP de tamaño y tasa están asignados a TASK-84 según WEBHOOK.md. No se probaron outbound ni servicios externos ajenos a este endpoint.

La garantía local de deduplicación tiene TTL y permite reentrega tras timeout ambiguo, reinicio o cambio de réplica, según el contrato documentado; requiere deduplicación downstream por MessageSid.
