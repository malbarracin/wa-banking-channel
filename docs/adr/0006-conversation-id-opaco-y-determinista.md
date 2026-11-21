# ADR-0006 — `conversationId` opaco y determinístico

- **Estado**: Aceptada
- **Fecha**: 2026-11-21
- **Alcance**: H0 gateway (lo genera), H0.2 orquestador y todo consumidor de eventos `wa.*`

## Contexto

El `conversationId` es la pieza más usada del canal: es la clave de partición que garantiza el orden (ADR-0003), es
el campo por el que el orquestador serializa el trabajo, y aparece en todos los logs y métricas de todos los
componentes. Hace falta que cumpla tres cosas a la vez:

1. **Estable**: el mismo cliente tiene que caer siempre en la misma conversación y en la misma partición, incluso
   después de reiniciar o escalar el gateway.
2. **Sin PII**: si llevara el teléfono, el número quedaría escrito en los logs y en las métricas de todo el canal,
   justo lo que la regla de privacidad prohíbe (en logs los teléfonos van enmascarados).
3. **Disponible desde el primer evento**: el gateway tiene que poder calcularlo al recibir el webhook, sin consultar
   a nadie, porque todavía no hay identidad ni sesión resueltas (eso es de H3 y H2, más adelante en el flujo).

## Decisión

El `conversationId` es `conv-` más un **UUID v5 determinístico** derivado del número del cliente normalizado en
E.164, con una semilla de entorno:

```
conversationId = "conv-" + uuidV5(WA_CONVERSATION_ID_SALT, "whatsapp:" + e164)
patrón = ^conv-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$
```

- Lo calcula **H0 (gateway)** al recibir el webhook y lo pone en el sobre de `MessageReceived`. El resto del canal lo
  propaga sin modificarlo, y lo usa como clave de partición en todos los eventos de esa conversación.
- `WA_CONVERSATION_ID_SALT` vive en el entorno del gateway (nunca en el repo) y debe ser estable: cambiarla parte las
  conversaciones en dos. Rotarla es una decisión planificada, no una de deploy.
- Es apto para logs y métricas **sin enmascarar**, porque es opaco.
- El teléfono sigue viajando en el `payload` de los eventos que lo necesitan (`from`, `to`): es el identificador del
  canal y sin él no se puede responder ni vincular al cliente. Es PII necesaria, y en logs va enmascarada.
- `messageId` es distinto y no se confunde con esto: identifica un mensaje, es la clave de deduplicación (ADR-0002) y
  en `MessageReceived` es el `MessageSid` de Twilio.

Una conversación, con esta definición, es **el hilo de WhatsApp de un número con el banco**: no se corta por sesión
ni por tema. Si más adelante hace falta cortar por sesión, se agrega un campo al contrato y se versiona el evento;
no se cambia el significado de la clave de partición.

## Alternativas consideradas

- **El teléfono en E.164 como `conversationId`**: simple y determinístico, pero escribe el número en los logs y las
  métricas de todos los componentes y en los nombres de clave del topic. Choca con la regla de privacidad.
  Descartada.
- **UUID v4 aleatorio con una tabla de mapeo en el gateway**: opaco, pero obliga a una consulta con estado para cada
  mensaje entrante y convierte al gateway en dueño de un dato de identidad, que no es su responsabilidad.
  Descartada.
- **Hash sin semilla** (SHA-256 del teléfono, truncado): opaco a la vista, pero reversible por fuerza bruta sobre el
  espacio de números telefónicos, que es chico. Descartada.
- **`conversationId` por sesión**, renovado en cada verificación de identidad: rompería la garantía de orden entre
  mensajes del mismo cliente al cambiar la clave de partición en mitad del hilo. Descartada.

## Consecuencias

- Los logs y las métricas de todo el canal pueden correlacionarse por conversación sin exponer un teléfono.
- El gateway necesita una variable de entorno más, y el frente de runtime tiene que garantizar que sea la misma en
  todas sus instancias y a lo largo del tiempo.
- Nadie puede ir del `conversationId` al teléfono sin la semilla: para soporte, el camino es buscar por
  `correlationId` o por `messageId` en los logs del gateway, que es el único que ve el número completo.
- H0 es el único componente que normaliza el número a E.164; una normalización distinta generaría otra conversación
  para el mismo cliente, así que esa lógica queda en el gateway y está cubierta por sus pruebas.
