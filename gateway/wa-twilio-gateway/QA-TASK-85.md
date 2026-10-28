# Verificación TASK-85 — handoff

Tester, 2026-10-28. Alcance: documentación de entrega en
`shared/contracts/wa-twilio-gateway/handoff.md`.

| Criterio | Verificación | Resultado |
| --- | --- | --- |
| 1. Endpoints, configuración, DLQ/replay y verificación | Contraste con controllers, application.yml, configuración/consumidor/publicador DLQ outbound y contratos de eventos/topics. Los 12 enlaces locales resuelven; UTF-8 sin BOM. | Cumple |
| 2. Anti-replay, firma, idempotencia y correlationId | Contraste con AntiReplayService, controllers, normalización y flujo outbound. Documenta TTL/capacidad, confirmación Kafka, deduplicación downstream, persistencia y reconciliación de envíos ambiguos. | Cumple |
| 3. Dependencias de implementación | task_view TASK-85 confirma TASK-79 a TASK-84; también están enumeradas en el handoff. | Cumple |

Desde `gateway/wa-twilio-gateway`, `.\mvnw.cmd -q -B clean verify`
terminó con código 0: 91 pruebas, 0 fallos, 0 errores, 3 omitidas.
JaCoCo: 494 líneas cubiertas de 524 (94,27 %), gate de 80 % aprobado.
Las suites KRaft embebidas verificaron inbound y outbound, duplicados,
orden, reintentos y DLQ validada contra schema. Las suites existentes también
cubren firma, correlación, WireMock y recuperación persistente.

Docker no accesible: se omitieron 1 caso de InboundKafkaIntegrationTest y
2 de OutboundKafkaIntegrationTest. Para verificarlos con Docker accesible:

```powershell
.\mvnw.cmd -q -B "-Dtest=InboundKafkaIntegrationTest,OutboundKafkaIntegrationTest" test
```

Comprobar cero omisiones en los reportes. Los límites inbound que requieren
controles del borde y el uso de una instancia con volumen persistente están
explicitados en la entrega. No se modificó código de producto ni fue necesario
añadir tests de comportamiento para este cambio documental.

Desde la raíz, `python scripts/verify_integracion.py` terminó con código 0:
`Verificación correcta: 2 componente(s).` (gateway y plataforma de eventos).

Veredicto: criterios 1, 2 y 3 verificados, sin defectos en el handoff.
