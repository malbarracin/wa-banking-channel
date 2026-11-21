# Topics del piloto

| Topic | Particiones locales | Retención | Productor | Consumidor |
| --- | ---: | --- | --- | --- |
| `wa.message.received.v1` | 3 | 7 días | Gateway | Orquestador; consumidor de referencia en local, dev y test |
| `wa.message.ready-to-send.v1` | 3 | 7 días | Orquestador | Gateway |
| `wa.message.status.v1` | 3 | 7 días | Gateway | Orquestador / observabilidad |
| `wa.message.dlq.v1` | 1 | 30 días | Consumidor que agota reintentos o rechaza un registro inválido | Soporte técnico, para reproceso |

En local la replicación es 1 y la política de limpieza es `delete`. En ambientes compartidos se usan 6 particiones, factor de replicación 3 y `min.insync.replicas=2`. No hay topics de retry: los reintentos bloqueantes preservan el orden por conversación.

La DLQ conserva la clave y los bytes del registro original, incluso si no es JSON válido, y agrega headers de falla saneados. No tiene esquema de payload ni se valida al publicarse.
