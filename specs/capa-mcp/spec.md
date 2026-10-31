---
title: Capa MCP (H7) — especificación
status: aprobada
source: null
updated: 2026-10-30T18:06:32.403Z
---

# Capa MCP (H7) — especificación

## Propósito

La Capa MCP (H7) es el puente estructurado entre el agente conversacional (H9) y los servicios del banco (H1–H6): expone como **capacidades MCP** las operaciones ya definidas en esos servicios, de forma que el agente invoque consultas, validaciones y operaciones permitidas **sin duplicar lógica de negocio**, respetando credencial de sesión (H2) y vínculo activo (H3), con errores traducidos a mensajes de negocio y trazabilidad de cada invocación.

No reemplaza a los servicios backend ni es el agente que habla con el cliente. Es la capa de integración que el agente utiliza para ejecutar acciones.

## Fuera de alcance (de la fuente y del roadmap)

- Lógica de negocio propia de cada servicio (permanece en H1–H6).
- Conversación con el cliente (H9 — Agente).
- Base de conocimiento documental (H8 — RAG).
- Definición de políticas de producto (permanecen en H4, H5, H6).
- Duplicación de lógica de H1–H6 (prohibido por el pack H7).
- Twilio (H0), Kafka (H0.1), deduplicación/reintentos/estado técnico del mensaje (H0.2).

## Actores y permisos

| Actor | Qué puede hacer |
| --- | --- |
| Agente conversacional (H9) | Invocar capacidades MCP autorizadas según contexto de sesión (credencial H2 + vínculo H3) |
| Servicios backend (H1–H6) | Exponer operaciones; validar credencial y reglas propias |
| Operaciones / soporte | Consultar trazas de invocaciones (sin secretos) |
| Banco (seguridad) | Habilitar/deshabilitar capacidades por política |

## Requisitos

### Requisito: Catálogo de capacidades publicadas
El sistema DEBE publicar un catálogo versionado de capacidades MCP, una por operación de H1–H6 que el agente necesita, indicando para cada una: nombre, entrada, salida, servicio origen y si exige credencial de sesión (H2) + vínculo activo (H3).

#### Escenario: Catálogo cubre H1–H6
- **DADO** que los servicios H1–H6 exponen sus contratos acordados
- **CUANDO** se consulta el catálogo MCP
- **ENTONCES** el catálogo lista al menos una capacidad por operación de H1–H6 que el agente necesita, con nombre, entrada, salida, servicio origen y requisito de credencial/vínculo
- **Y** cada capacidad referencia al contrato publicado de su servicio (shared/contracts/)

#### Escenario: Capacidad no publicada es rechazada
- **DADO** un agente que intenta invocar una capacidad que no está en el catálogo
- **CUANDO** se solicita la invocación
- **ENTONCES** el sistema rechaza la invocación con motivo de negocio y no ejecuta ninguna operación

### Requisito: Validación de contexto de sesión (credencial H2 + vínculo H3)
Toda invocación de una capacidad de producto (H4, H5, H6) DEBE validar credencial de sesión válida (H2) y vínculo activo (H3) antes de operar; sin credencial válida el sistema bloquea la operación y deriva a re-verificación.

#### Escenario: Invocación de producto con credencial válida y vínculo activo
- **DADO** una capacidad de producto y un contexto de sesión con credencial válida y vínculo activo
- **CUANDO** el agente la invoca
- **ENTONCES** el sistema ejecuta la operación en el servicio origen y devuelve el resultado formateado para el agente
- **Y** la invocación queda auditada

#### Escenario: Invocación de producto sin credencial válida es bloqueada
- **DADO** una capacidad de producto y un contexto sin credencial válida o con vínculo inactivo
- **CUANDO** el agente la invoca
- **ENTONCES** el sistema no invoca al servicio origen ni consulta productos
- **Y** devuelve un rechazo de negocio que deriva al flujo de re-verificación (H3) con el mensaje: *"Por seguridad, volvé a verificar tu identidad para continuar"*

#### Escenario: Credencial expirada o revocada
- **DADO** una credencial expirada o revocada
- **CUANDO** se valida el contexto antes de una capacidad de producto
- **ENTONCES** el sistema rechaza la invocación sin consultar productos y deriva a re-verificación

### Requisito: Capacidades de canal e identidad sin credencial de producto
Las capacidades de vinculación y verificación (H3) y de acceso (H2: emitir/renovar/revocar credencial) DEBEN poder ejecutarse sin credencial de producto, según el flujo del canal.

#### Escenario: Vinculación inicial sin credencial de producto
- **DADO** un cliente nuevo en el canal sin credencial de producto
- **CUANDO** el agente invoca la capacidad de verificación y vínculo (H3)
- **ENTONCES** el sistema ejecuta la verificación y, tras éxito, invoca la emisión de credencial (H2)
- **Y** la secuencia queda auditada con correlationId

#### Escenario: Renovación de credencial
- **DADO** una credencial por vencer o vencida bajo condiciones permitidas
- **CUANDO** el agente invoca la capacidad de renovación (H2)
- **ENTONCES** el sistema renueva la credencial sin repetir todo el proceso salvo riesgo o bloqueo

### Requisito: Traducción de errores a mensajes de negocio
Los errores de servicio DEBEN traducirse a mensajes de negocio claros para el agente; no se DEBEN exponer stack traces ni detalles técnicos al cliente.

#### Escenario: Error de servicio traducido
- **DADO** una invocación que el servicio origen rechaza (credencial inválida, regla de negocio, no disponible)
- **CUANDO** el sistema recibe el error
- **ENTONCES** devuelve un mensaje de negocio con código y descripción comprensible para el agente
- **Y** no incluye stack trace, SQL, nombres internos ni detalles técnicos

#### Escenario: Servicio no disponible
- **DADO** que un servicio H1–H6 no responde o falla
- **CUANDO** el agente invoca una capacidad que lo requiere
- **ENTONCES** el sistema devuelve un mensaje empático informando la indisponibilidad y ofreciendo alternativa (soporte, reintento)
- **Y** la invocación queda auditada como fallida

### Requisito: Auditoría de invocaciones
Toda invocación sensible DEBE quedar auditada con quién, qué, cuándo y resultado, incluyendo correlationId.

#### Escenario: Invocación auditada
- **DADO** una invocación de capacidad (exitosa o fallida)
- **CUANDO** se completa
- **ENTONCES** se registra quién invocó (contexto de sesión), qué capacidad, cuándo (timestamp) y el resultado
- **Y** la traza incluye correlationId extremo a extremo

#### Escenario: Consulta de trazas por soporte
- **DADO** personal de operaciones/soporte con permisos
- **CUANDO** consulta trazas de invocaciones
- **ENTONCES** obtiene quién, qué, cuándo y resultado sin secretos

### Requisito: Versionado de capacidades
Cuando un servicio cambia su contrato, la capacidad MCP DEBE versionarse (v1 → v2) y el anterior DEBE mantenerse hasta que el consumidor (H9) migre.

#### Escenario: Versionado ante cambio de contrato
- **DADO** que un servicio H1–H6 publica una nueva versión de su contrato
- **CUANDO** la capacidad MCP se actualiza
- **ENTONCES** se publica la nueva versión (v2) manteniendo la anterior (v1) hasta la migración de H9
- **Y** el catálogo indica las versiones disponibles

### Requisito: Seguridad de acceso — credencial de sesión y vínculo (transversal)
Toda capacidad de producto exige credencial de sesión válida y vínculo activo (H3); sin credencial se deriva a verificación; credencial expirada o revocada se rechaza de inmediato; tras revocación en H2, MCP deja de autorizar operaciones de ese cliente en el mismo minuto operativo.

#### Escenario: Operación sin JWT válido (transversal)
- **DADO** un cliente sin credencial JWT válida o con vínculo inactivo
- **CUANDO** intenta invocar una capacidad de producto
- **ENTONCES** el sistema no realiza la operación y deriva al cliente a verificación (H3) con el mensaje de seguridad correspondiente

#### Escenario: Efectividad inmediata de la revocación en H2 (transversal)
- **DADO** que H2 revoca una credencial de sesión
- **CUANDO** se completa la revocación
- **ENTONCES** MCP deja de autorizar operaciones de ese cliente en el mismo minuto operativo

## Reglas de negocio

1. El agente **solo** puede invocar capacidades publicadas en el catálogo MCP.
2. Operaciones de productos (H4, H5, H6) exigen credencial válida (H2) y vínculo activo (H3).
3. Vinculación y verificación (H3) pueden ejecutarse sin credencial de producto, según flujo del canal.
4. Errores de servicio se traducen a mensajes de negocio; no se exponen stack traces al cliente.
5. Toda invocación sensible queda auditada (quién, qué, cuándo, resultado).
6. Si un servicio no está disponible, el agente debe informar con mensaje empático y ofrecer alternativa (soporte, reintento).
7. Sin identidad verificada (H3) no se operan productos sensibles; sin credencial válida (H2) se deriva a re-verificación.
8. El agente **no inventa**: datos de producto salen de H8 (con cita) o de H7; con baja confianza lo dice o escala.
9. Montos, horarios y límites los define el banco: se comunican tal cual.
10. Toda operación sensible queda trazada (agente → MCP → servicio) con `correlationId`.
11. Mensajes claros, empáticos y sin jerga; nunca stack traces ni detalles técnicos al cliente.
12. Privacidad: solo el titular o personas expresamente autorizadas.

## Requisitos no funcionales

- **Latencia:** las cadenas de invocación deben mantener latencia acumulada acotada; sin valores cerrados en la fuente (ver Preguntas abiertas).
- **Trazabilidad:** extremo a extremo con `correlationId`, incluyendo flujos síncronos.
- **Privacidad:** logs y trazas sin PII (documento, teléfono, nombre enmascarados); sin datos reales de clientes en código, tests ni fixtures.
- **Auditoría:** registro de invocaciones sensibles (quién, qué, cuándo, resultado) sin secretos.
- **Seguridad:** secretos solo por variables de entorno; nunca en archivos del repo ni en logs.

## Supuestos

- Los servicios H1–H6 exponen APIs estables y publican contratos en `shared/contracts/<servicio>/` (OpenAPI).
- Políticas de acceso (qué capacidades habilitar/deshabilitar) definidas por el banco.
- H9 consume MCP como principal vía de acción; H8 complementa con conocimiento documental.
- Los valores concretos de latencia, ventanas y umbrales no están en la fuente; se dejan parametrizables.

## Preguntas abiertas

- Cuáles son las operaciones exactas de H1, H2, H3 y H6 que el agente necesita exponer como capacidades (la fuente H7 las enumera como "H1 a H6" sin detalle por servicio); requiere cruzar con las specs aprobadas de cada hito y el catálogo acordado.
- Valores concretos de latencia máxima acumulada en cadenas de invocación (regla de NFR): no definido en la fuente.
- Granularidad de la auditoría (qué invocaciones son "sensibles" vs. solo traza) y retención de trazas: no definido en la fuente.
- Mecanismo de habilitar/deshabilitar capacidades por política (endpoint, config, feature flag) y quién lo opera: no definido en la fuente.
- Formato exacto del catálogo publicado (Markdown, OpenAPI, JSON Schema) en `shared/contracts/wa-mcp-layer/`: a definir por el arquitecto; la fuente pide "catálogo de capabilities + handoff".
- Estrategia de versionado (semver, fecha, v1/v2) y política de deprecación: no definida en la fuente.
