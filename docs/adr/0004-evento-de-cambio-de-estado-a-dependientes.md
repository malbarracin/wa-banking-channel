# 4. Evento de cambio de estado a servicios dependientes

Fecha: 2026-11-21
Estado: Aceptado

## Contexto

La regla 4 de la fuente (y R4) indica que, ante un cambio de estado operativo (suspendido, reactivado, baja lógica),
se debe **notificar a servicios dependientes si aplica**. El acoplamiento directo (llamadas HTTP a cada dependiente)
haría que el alta/cambio de estado fallara si un dependiente no responde.

## Decisión

- Ante un cambio de estado, el servicio publica un evento `wa.users.status-changed` con
  `{userId, estadoAnterior, estadoNuevo, correlationId}` en el broker del programa (prefijo de topics `wa.`).
- La publicación es **desacoplada de la transacción** (patrón outbox / evento tras commit): la operación del
  servicio no depende de que el broker esté disponible.
- Los servicios dependientes se suscriben al topic; este servicio no los conoce ni los llama.

## Consecuencias

- Bajo acoplamiento y resiliencia: un dependiente caído no rompe el cambio de estado.
- Consistencia eventual entre el estado del usuario y las vistas de los dependientes.
- Requiere el broker de plataforma; si aún no existe, el evento se registra en log y el hook queda preparado para
  conectarse. El contrato del evento se documenta en el `handoff.md` del servicio.