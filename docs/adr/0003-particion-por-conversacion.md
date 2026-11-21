# ADR-0003 — Partición por `conversationId` para preservar el orden

- **Estado**: Aceptada
- **Fecha**: 2026-11-21
- **Alcance**: todos los topics `wa.*` de negocio

## Contexto

Kafka garantiza orden **dentro de una partición**, no dentro de un topic. En una conversación de WhatsApp el orden sí
importa: dos mensajes seguidos del cliente se tienen que leer en ese orden, y las respuestas tienen que salir en el
orden en que se decidieron. Al mismo tiempo, el canal tiene que atender muchas conversaciones a la vez sin que una
lenta frene a las demás.

## Decisión

**La clave de todo registro de negocio es el `conversationId`**, y nunca es nula. De ahí se desprende que:

- Todos los eventos de una conversación caen siempre en la misma partición y se consumen en orden.
- Una partición la procesa **un solo hilo**: la concurrencia del listener es menor o igual a la cantidad de
  particiones, y está prohibido paralelizar dentro de la partición (sin pool de hilos por registro, sin `@Async` en
  el listener).
- Las conversaciones distintas se reparten entre particiones y avanzan en paralelo.
- El `conversationId` es opaco y determinístico (ADR-0006): el mismo cliente cae siempre en la misma partición.
- Los reintentos son bloqueantes, justamente para no adelantar el registro siguiente de la misma conversación
  (ADR-0005).

Particiones: 3 con replicación 1 en la PC; 6 con replicación 3 y `min.insync.replicas=2` en ambientes compartidos.
La DLQ va con una sola partición, porque su reproceso es manual y conviene ordenado.

## Alternativas consideradas

- **Clave por `messageId`**, o sin clave con reparto round-robin: máximo paralelismo y cero garantías de orden;
  habría que reordenar en el consumidor con estado y ventanas de espera. Descartada.
- **Un topic con una sola partición**: orden total y trivial, pero un único hilo para todo el canal y ninguna
  posibilidad de escalar. Descartada.
- **Orden reconstruido en el orquestador** con números de secuencia y buffer: mueve complejidad real a H0.2 y agrega
  latencia por esperar al mensaje que falta. Descartada.

## Consecuencias

- El throughput de una conversación está limitado a un hilo; el del canal, a la cantidad de particiones. Para
  escalar se agregan particiones: el reparto de las conversaciones existentes cambia, pero el orden futuro por
  conversación se mantiene porque la clave no cambia.
- Una conversación que falla y reintenta retiene su partición unos segundos: el tope del backoff del ADR-0005 está
  elegido por eso.
- Un cliente muy activo puede desbalancear una partición; se acepta en el piloto y se observa con el lag por
  partición.
- Cambiar la cantidad de particiones es una operación planificada, no una decisión de deploy.
