---
title: Agente conversacional (H9) — especificación
status: borrador
source: null
updated: 2026-10-30T23:53:01.300Z
---

# Agente conversacional (H9) — especificación

## Propósito

El Agente conversacional (H9) es la cara del banco en WhatsApp: conversa con el cliente, entiende qué necesita, responde con información oficial (H8 — RAG) y ejecuta operaciones permitidas a través de la capa de integración (H7 — MCP). Recibe turnos ya validados y normalizados desde el Orquestador (H0.2) y devuelve una respuesta estructurada; no conoce webhooks de Twilio ni consume Kafka directamente.

No reemplaza la plataforma ni los servicios: recibe turnos de H0.2, opera vía H7 (MCP) e informa vía H8 (RAG). No ejecuta operaciones bancarias por sí mismo, no define lógica de negocio de H1–H6, no define el catálogo MCP ni el contenido RAG.

## Fuera de alcance (del roadmap y de la fuente)

- Webhooks, firma y adaptación a Twilio (H0).
- Producción/consumo directo de topics Kafka (H0.1).
- Deduplicación, correlación, retries y máquina de estados del flujo (H0.2).
- Lógica de negocio de cada servicio (permanece en H1–H6).
- Definición del catálogo MCP (H7) ni del contenido RAG (H8).
- Canales distintos de WhatsApp (app, web, sucursal).
- Decisiones de crédito, scoring o aprobaciones que requieran analista humano.

## Actores y permisos

| Actor | Qué puede hacer |
| --- | --- |
| Cliente titular | Conversar, consultar, operar según productos habilitados |
| Representante autorizado | Solo lo que política permita; verificación reforzada vía H3 |
| Operador humano | Recibir derivaciones; retomar conversación |
| Banco (supervisión) | Revisar calidad de respuestas y casos escalados |

## Requisitos

### Requisito: Detección de intención
El agente DEBE clasificar cada turno del cliente en una de tres intenciones: consulta informativa, operación o soporte, y conducir el diálogo en consecuencia.

#### Escenario: Intención informativa
- **DADO** un mensaje del cliente con pregunta sobre productos, políticas, horarios o procedimientos
- **CUANDO** el agente lo procesa
- **ENTONCES** lo clasifica como consulta informativa
- **Y** consulta H8 (RAG) para responder con contenido aprobado y citas

#### Escenario: Intención de operación
- **DADO** un mensaje del cliente solicitando una operación (consulta de saldo, transferencia, bloqueo de tarjeta, aviso de viaje, denuncia de siniestro)
- **CUANDO** el agente lo procesa
- **ENTONCES** lo clasifica como operación
- **Y** valida identidad/credencial antes de invocar la capacidad MCP correspondiente (H7)

#### Escenario: Intención de soporte
- **DADO** un mensaje del cliente con reclamo, insatisfacción o caso complejo fuera del alcance automatizable
- **CUANDO** el agente lo procesa
- **ENTONCES** lo clasifica como soporte
- **Y** ofrece derivación a un operador humano con contexto resumido

### Requisito: Flujo de identidad (H3 → H2 → productos)
El agente DEBE respetar el flujo de identidad: sin identidad verificada (H3) no se operan productos sensibles; sin credencial válida (H2) se deriva a re-verificación. El agente invoca vinculación/verificación vía MCP (H7/H3) para clientes nuevos y coordina la emisión de credencial (H2) tras verificación exitosa.

#### Escenario: Cliente nuevo en el canal
- **DADO** un cliente que escribe por primera vez
- **CUANDO** H0.2 entrega el turno normalizado al agente
- **ENTONCES** el agente saluda, explica el proceso e invoca la capacidad de verificación y vínculo (H3) vía MCP
- **Y** H0.2 conserva el estado del flujo; tras verificación exitosa se coordina la emisión de credencial (H2)
- **Y** el agente devuelve la respuesta; H0.2 la publica y H0 la envía por Twilio

#### Escenario: Consulta de saldo con identidad válida
- **DADO** un cliente con vínculo activo y credencial válida que pide saldo
- **CUANDO** el agente procesa la intención de operación
- **ENTONCES** valida la sesión (credencial H2 + vínculo H3)
- **Y** invoca la capacidad MCP correspondiente (H4 vía H7)
- **Y** responde con monto y mensaje claro

#### Escenario: Operación sin credencial válida
- **DADO** un cliente sin credencial válida o con vínculo inactivo que intenta operar un producto
- **CUANDO** el agente valida la sesión antes de invocar MCP
- **ENTONCES** no invoca al servicio origen ni consulta productos
- **Y** responde con el mensaje de re-verificación: *"Por seguridad, volvé a verificar tu identidad para continuar"*

#### Escenario: Credencial expirada o revocada
- **DADO** una credencial expirada o revocada
- **CUANDO** el agente valida el contexto antes de una capacidad de producto
- **ENTONCES** rechaza la operación sin consultar productos
- **Y** deriva al flujo de re-verificación (H3)

### Requisito: Respuestas informativas usando RAG (H8)
El agente DEBE responder consultas informativas usando únicamente conocimiento aprobado de H8, con citas cuando corresponda, y no inventar información. Ante baja confianza o sin respuesta suficiente, debe declararlo o escalar a humano.

#### Escenario: Pregunta informativa con respuesta disponible
- **DADO** una pregunta informativa con respuesta en contenido aprobado de H8
- **CUANDO** el agente consulta H8
- **ENTONCES** H8 devuelve fragmentos con cita (fuente, sección/página o identificador) y confianza
- **Y** el agente responde al cliente citando o referenciando el contenido oficial cuando aplique

#### Escenario: Pregunta informativa sin respuesta suficiente
- **DADO** una pregunta sin respuesta suficiente en la base o con confianza por debajo del umbral
- **CUANDO** el agente consulta H8
- **ENTONCES** H8 indica "sin respuesta suficiente" / baja confianza
- **Y** el agente no inventa la respuesta: ofrece derivación a soporte humano o pide aclaración

#### Escenario: Separación público/interno en la respuesta
- **DADO** resultados de H8 que incluyen fragmentos públicos e internos
- **CUANDO** el agente genera la respuesta al cliente
- **ENTONCES** solo utiliza fragmentos públicos para el texto al cliente
- **Y** los fragmentos internos se usan únicamente para razonamiento/derivación, sin exponerlos

### Requisito: Escalamiento a operador humano
El agente DEBE ofrecer derivación a un operador humano cuando el caso es complejo, el cliente está insatisfecho, la confianza es baja o hay riesgo; el operador continúa con contexto resumido sin exponer información interna.

#### Escenario: Escalamiento por insatisfacción o caso complejo
- **DADO** un cliente insatisfecho o un caso complejo que el agente no puede resolver con seguridad
- **CUANDO** el agente lo detecta
- **ENTONCES** ofrece derivación con el mensaje: *"Para ayudarte mejor, te comunico con una persona de nuestro equipo."*
- **Y** el operador humano continúa con contexto resumido (sin PII ni detalles internos)

#### Escenario: Escalamiento por baja confianza
- **DADO** una consulta cuya respuesta en H8 tiene confianza por debajo del umbral
- **CUANDO** el agente evalúa la respuesta
- **ENTONCES** no responde con información no verificada
- **Y** ofrece derivación a soporte humano o pide aclaración al cliente

### Requisito: Contrato de turno con H0.2
El agente DEBE recibir un turno normalizado desde H0.2 (contexto de sesión, historial conversacional, mensaje del cliente) y devolver una respuesta estructurada que H0.2 publique como mensaje outbound. El agente no gestiona Twilio ni Kafka.

#### Escenario: Recepción de turno normalizado
- **DADO** que H0.2 deduplicó, cargó contexto y entregó el turno normalizado
- **CUANDO** el agente lo recibe
- **ENTONCES** procesa el mensaje del cliente con el contexto de sesión e historial provisto
- **Y** no accede directamente a Twilio, Kafka ni al estado técnico del mensaje

#### Escenario: Respuesta estructurada para outbound
- **DADO** que el agente procesó el turno
- **CUANDO** genera la respuesta
- **ENTONCES** devuelve una respuesta estructurada (texto al cliente, acciones realizadas, estado de escalamiento si aplica)
- **Y** H0.2 la publica y H0 la envía por Twilio

### Requisito: Memoria entre turnos
El agente DEBE mantener continuidad conversacional usando el historial recibido desde H0.2, sin exponer secretos ni datos sensibles en los mensajes al cliente.

#### Escenario: Continuidad de conversación
- **DADO** un cliente que continúa una conversación previa
- **CUANDO** H0.2 entrega el turno con historial conversacional
- **ENTONCES** el agente usa el historial para mantener continuidad (referencias a temas previos, preferencias)
- **Y** no expone secretos, credenciales ni datos sensibles en los mensajes al cliente

#### Escenario: Sin exposición de secretos
- **DADO** información sensible en el contexto de sesión
- **CUANDO** el agente genera la respuesta
- **ENTONCES** no incluye stack traces, tokens, credenciales ni datos internos en el mensaje al cliente

### Requisito: Mensajes al cliente alineados a política del banco
El agente DEBE usar mensajes claros, empáticos y sin jerga, alineados a las reglas transversales del programa, con los mensajes de ejemplo definidos en la fuente.

#### Escenario: Mensaje de bienvenida
- **DADO** un cliente que inicia conversación
- **CUANDO** el agente responde por primera vez
- **ENTONCES** usa un saludo alineado a la política del banco: *"Hola, soy el asistente del banco por WhatsApp. ¿En qué te puedo ayudar hoy?"*

#### Escenario: Mensaje de límite del banco
- **DADO** una operación que supera el monto permitido para el canal
- **CUANDO** el agente informa al cliente
- **ENTONCES** usa el mensaje: *"Esa operación supera el monto permitido para este canal. Podés hacerla desde la app o sucursal."*

#### Escenario: Mensaje de re-verificación
- **DADO** un cliente sin credencial válida
- **CUANDO** el agente deriva a re-verificación
- **ENTONCES** usa el mensaje: *"Por seguridad, necesitamos verificar tu identidad de nuevo para continuar."*

### Requisito: Trazabilidad de operaciones vía MCP
Toda operación sensible ejecutada vía MCP DEBE quedar trazada (agente → MCP → servicio) con correlationId extremo a extremo.

#### Escenario: Operación trazada
- **DADO** una operación sensible ejecutada por el agente vía MCP
- **CUANDO** se completa (exitosa o fallida)
- **ENTONCES** se registra quién invocó (contexto de sesión), qué capacidad, cuándo (timestamp) y el resultado
- **Y** la traza incluye correlationId extremo a extremo

#### Escenario: Servicio no disponible
- **DADO** que un servicio H1–H6 no responde o falla
- **CUANDO** el agente invoca una capacidad que lo requiere
- **ENTONCES** devuelve un mensaje empático informando la indisponibilidad y ofreciendo alternativa (soporte, reintento)
- **Y** la invocación queda auditada como fallida

### Requisito: Alcance de operaciones (H4, H5, H6)
El agente DEBE poder consultar y operar productos según las capacidades publicadas por H7 para cuentas (H4), tarjetas (H5) y seguros (H6), respetando credencial y vínculo, y comunicando montos, horarios y límites tal cual los define el banco.

#### Escenario: Operación de tarjeta (aviso de viaje)
- **DADO** un cliente con credencial válida que solicita aviso de viaje
- **CUANDO** el agente procesa la intención
- **ENTONCES** invoca la capacidad MCP correspondiente (H5 vía H7)
- **Y** responde con confirmación y mensaje claro

#### Escenario: Operación de seguros (denuncia de siniestro)
- **DADO** un cliente con credencial válida que reporta un siniestro
- **CUANDO** el agente procesa la intención
- **ENTONCES** invoca la capacidad MCP correspondiente (H6 vía H7)
- **Y** responde con confirmación y próximos pasos

#### Escenario: Comunicación de límites del banco
- **DADO** una consulta o respuesta que involucra montos, horarios o límites de producto
- **CUANDO** el agente comunica al cliente
- **ENTONCES** usa los valores definidos por el banco sin modificarlos ni inventar excepciones
- **Y** si hay duda de vigencia, indica que confirma con soporte o deriva

## Reglas de negocio

1. Sin identidad verificada (H3), no se operan productos sensibles.
2. Sin credencial válida (H2), se deriva a re-verificación.
3. El agente no inventa datos de producto: consulta RAG (H8) o MCP (H7).
4. Montos, horarios y límites los define el banco; el agente los comunica tal cual.
5. Ante ambigüedad o riesgo, escala a humano.
6. Toda operación sensible queda trazada (agente → MCP → servicio) con correlationId.
7. Mensajes alineados a reglas transversales del programa (claridad, privacidad, auditoría).
8. Privacidad: solo el titular o personas expresamente autorizadas.
9. Idempotencia en Gateway, Orquestador y consumidores Kafka (entrega at-least-once) — el agente no duplica operaciones.
10. Orden por conversación mediante partición por conversationId.
11. Trazabilidad extremo a extremo con correlationId, incluyendo flujos síncronos.
12. El agente solo puede invocar capacidades publicadas en el catálogo MCP (H7).
13. Escalamiento humano cuando el canal no puede resolver con seguridad.
14. El agente no expone stack traces ni detalles técnicos al cliente.

## Requisitos no funcionales

- **Latencia:** la cadena de invocación (turno → agente → MCP/RAG → respuesta) debe mantener latencia acumulada acotada; valores concretos no definidos en la fuente (parametrizable).
- **Trazabilidad:** extremo a extremo con correlationId, incluyendo flujos síncronos.
- **Privacidad:** logs y trazas sin PII (documento, teléfono, nombre enmascarados); sin datos reales de clientes en código, tests ni fixtures.
- **Auditoría:** registro de operaciones sensibles y consultas relevantes (quién, qué, cuándo, resultado) sin secretos.
- **Seguridad:** secretos solo por variables de entorno; nunca en archivos del repo ni en logs.
- **Disponibilidad:** H9 depende de H0.2 (turnos), H7 (MCP) y H8 (RAG); debe degradarse con mensaje empático si alguno no está disponible.

## Supuestos

- Twilio activo e integrado mediante H0; Kafka y H0.2 operativos; MCP (H7) disponible; RAG (H8) disponible.
- Políticas de mensajes aprobadas por el banco.
- Los umbrales de confianza de H8 se definirán en el contrato/arquitectura de H8 y se consumen por H9.
- El formato exacto del contrato de turno (shared/contracts/wa-conversation-agent/turn.md) se publicará por H9; H0.2 lo consume.
- El modelo de lenguaje es gemini-2.5-flash y los embeddings text-embedding-004 (según project.yaml), sin otro valor en la fuente.
- El historial conversacional lo provee H0.2; H9 no persiste estado técnico del mensaje.

## Preguntas abiertas

- **Intenciones cubiertas:** ¿lista cerrada de intenciones soportadas en la primera versión (más allá de informativa/operación/soporte)? Requiere acuerdo con Negocio.
- **Escalamiento:** ¿qué criterios exactos disparan el escalamiento automático (número de intentos fallidos, sentimiento, tipo de consulta)? No definido en la fuente.
- **Memoria:** ¿cuántos turnos de historial se incluyen por defecto y cómo se truncan? No definido en la fuente.
- **Contrato de turno:** formato exacto de los esquemas de turno y respuesta en shared/contracts/wa-conversation-agent/turn.md — a definir por el arquitecto; no existe aún en el repo (nota de H8).
- **Latencia:** valores concretos de latencia máxima acumulada en la cadena de invocación — no definido en la fuente.
- **Auditoría:** granularidad (qué invocaciones son "sensibles" vs. solo traza) y retención de trazas — no definido en la fuente.
- **Modelo de lenguaje:** si el proyecto no lo dice, gemini-2.5-flash según project.yaml; confirmar con la persona si hay otro preferido.
