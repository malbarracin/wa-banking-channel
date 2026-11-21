# ADR-0001 — Kafka como backbone asíncrono del canal

- **Estado**: Aceptada
- **Fecha**: 2026-11-21
- **Alcance**: todo el canal WhatsApp (H0.1, H0, H0.2 y quien se sume después)

## Contexto

El canal recibe mensajes por un webhook de Twilio, que exige una respuesta rápida, y responderlos requiere
orquestación, identidad, consultas a servicios de dominio y un agente conversacional: segundos, no milisegundos.
Si el ingreso quedara acoplado de forma sincrónica a todo eso, cada componente caído o lento se transformaría en
mensajes perdidos y en reintentos del proveedor.

Además, varios componentes del programa se construyen en paralelo y por equipos distintos (H0, H0.2, H9), con
fechas propias: necesitan un punto de encuentro estable que no sea una llamada directa entre ellos.

## Decisión

Se adopta **Apache Kafka** (modo KRaft, sin ZooKeeper) como columna vertebral asíncrona del canal. El ingreso, la
orquestación y la salida se comunican publicando y consumiendo eventos en topics con prefijo `wa.` y forma
`wa.<entidad>.<hecho>.v<n>`. El acceso se hace con **Spring for Apache Kafka** desde los componentes Java.

Kafka es **mensajería, no base de datos**: no guarda estado de dominio ni de conversación, y ningún componente lo
consulta como fuente de verdad. Los eventos tienen retención acotada (7 días en los topics de negocio); lo que debe
persistir, persiste en la base del componente dueño del dato.

Twilio nunca ve los topics: solo habla con el gateway.

## Alternativas consideradas

- **Llamadas HTTP sincrónicas entre componentes**: más simple de arrancar, pero el ACK del webhook quedaría atado a
  la latencia del agente y de los servicios de dominio, y una caída se propagaría a todo el canal. Descartada.
- **RabbitMQ u otra cola de trabajo**: resuelve el desacople, pero no da orden por clave con particiones ni
  relectura de un rango de offsets, que es lo que hace viable el reproceso desde DLQ del ADR-0005. Descartada.
- **Kafka Streams para la orquestación**: agrega un modelo de programación y un estado distribuido que el piloto no
  necesita; la orquestación es una máquina de estados persistente en H0.2. Descartada.

## Consecuencias

- El webhook puede confirmarse rápido y el procesamiento sigue por detrás, sin reintentos del proveedor.
- Cada componente se despliega y se cae por separado: los mensajes esperan en el topic.
- Aparecen los costos de lo asíncrono: entrega at-least-once (ADR-0002), orden solo por partición (ADR-0003) y
  contratos que hay que versionar con cuidado (ADR-0004).
- Hay un componente de infraestructura más para operar: en la PC es un nodo KRaft; en ambientes compartidos requiere
  TLS, SASL y ACL por topic.
