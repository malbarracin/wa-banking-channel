---
title: H8 — Base de conocimiento RAG (wa-knowledge-rag)
status: borrador
source: https://app.notion.com/p/38375bb08ca781e2accfdd9eff1722a4
updated: 2026-11-27T21:01:12.330Z
---

# H8 — Base de conocimiento RAG (`wa-knowledge-rag`)

## 1. Propósito

Concentrar el conocimiento **aprobado** del banco que el agente conversacional necesita para responder bien: qué
productos ofrece, cómo explicarlos, qué límites y políticas aplican, qué decir ante situaciones frecuentes y cuándo
derivar a una persona. Lo recupera según la pregunta del cliente y lo devuelve **con su cita** y una **señal de
confianza**, para que el agente no improvise ni invente.

Problema de negocio: el cliente pregunta cosas que no son una operación (horarios, requisitos, qué significa un cargo,
qué hacer si perdió la tarjeta). Sin conocimiento estructurado el agente improvisa o responde mal; el banco necesita
respuestas alineadas a la política oficial.

- **Carpeta del componente:** `ai/wa-knowledge-rag` (todo el componente va dentro de esa carpeta).
- **Contrato que publica:** `shared/contracts/wa-knowledge-rag/retrieval.md` (+ el esquema de la respuesta, en la
  misma carpeta). Lo consume el agente conversacional (`ai/wa-conversation-agent`, H9).
- **Puerto en la PC (runtime local):** `8102` (convención del programa).
- **Depende de:** nada para empezar. Puede arrancar el primer día, en paralelo con la capa MCP (H7). Tiene que estar
  lista antes de producción del agente (H9).

### Fuera de alcance

- Ejecutar transferencias, consultas de saldo u otras operaciones bancarias (capa MCP + servicios H4–H6).
- Datos transaccionales del cliente en tiempo real (vienen de los servicios vía la capa MCP).
- Entrenar el modelo base del proveedor de IA.
- Contenido que el banco no aprobó.
- Guardar datos personales del cliente.
- Conversar con el cliente, decidir la intención o redactar la respuesta final: lo hace el agente (H9).
- Transporte del canal (Twilio, Kafka, orquestación): H0, H0.1, H0.2.
- Reemplazar las reglas de negocio de H1–H6: la base las **explica**, no las aplica.

## 2. Actores y permisos

| Actor | Qué puede hacer en la base |
|-------|---------------------------|
| Áreas de negocio / producto | Aportar contenido y aprobarlo como dueñas del tema |
| Compliance / legal | Validar los mensajes y las políticas publicables (aprobación de compliance) |
| Soporte | Proponer nuevas preguntas frecuentes a partir de casos reales (queda como propuesta, no se publica sola) |
| Agente conversacional (H9) | Consultar el conocimiento publicado en cada respuesta informativa; solo lectura |
| Operación del componente | Ejecutar la ingesta, la publicación, la reindexación y la vuelta atrás de versiones |

Nadie consulta la base sin identificarse como consumidor habilitado (ver RF-08). El cliente final **no** consulta la
base directamente: siempre pasa por el agente.

## 3. Requisitos funcionales

### RF-01 — Tipos de contenido y familias

La base guarda cuatro tipos de contenido, que salen de la fuente: **preguntas frecuentes**, **guías de producto**,
**políticas de comunicación** y **mensajes aprobados**. Cada pieza pertenece a una familia: `cuentas`, `tarjetas`,
`seguros` o `canal` (uso del canal WhatsApp: vinculación, verificación de identidad, sesión, ayuda inicial,
derivación). Cada pieza declara su área de origen (negocio, compliance, soporte, documentación de producto).

Metadatos mínimos de cada pieza: identificador estable, título, tipo, familia, audiencia (`publico` / `interno`),
área dueña, versión, estado, fecha de aprobación y aprobadores (rol, no datos personales), vigencia (desde / hasta
opcional), idioma (español).

- **Dado** una pieza sin tipo, familia, audiencia o área dueña, **cuando** se intenta ingerir, **entonces** se rechaza
  con un error que dice qué metadato falta y no entra a la base.
- **Dado** una pieza válida de la familia `tarjetas`, **cuando** se ingiere, **entonces** queda registrada con todos
  sus metadatos y se puede filtrar por familia en la consulta.

### RF-02 — Contenido mínimo

La base publicada incluye **las preguntas frecuentes mínimas de cada familia de producto (cuentas, tarjetas y
seguros) y las del canal**. Los temas mínimos salen de las especificaciones de H3, H4, H5, H6 y H9 (y de la propia de
H8):

| Familia | Temas mínimos (de la fuente) |
|---------|------------------------------|
| Cuentas (H4) | Qué se puede hacer por el canal (cuentas, saldo, movimientos, transferencias); horario para transferir; límites por operación y diarios; transferencias solo a destinatarios ya agendados (no se agendan por chat); confirmación y doble confirmación sobre umbral; ventana y tope de movimientos; qué no se hace por el canal (apertura/cierre, préstamos, extractos) |
| Tarjetas (H5) | Qué hacer si perdiste o te robaron la tarjeta (bloqueo definitivo, irreversible por chat, reposición por canales oficiales); bloqueo y desbloqueo temporal; aviso de viaje; por qué nunca se piden ni se muestran número completo ni código de seguridad; consumos (tope y ventana); qué no se hace (alta, pago de resumen, disputa de consumos → proceso formal) |
| Seguros (H6) | Cómo consultar pólizas y coberturas (el resumen no reemplaza las condiciones generales); cómo denunciar un siniestro y qué es el número de caso; estados del caso; documentación por canales oficiales; qué no se hace (cotizar, contratar, cancelar, montos de liquidación) |
| Canal | Qué es el asistente y qué puede hacer; cómo vincular el número y verificar la identidad; por qué se pide volver a verificar; cuándo y cómo se deriva a una persona; privacidad (solo el titular o personas autorizadas) |

Los valores que el banco todavía no definió (horarios y límites de transferencia, umbral de doble confirmación,
ventana y tope de movimientos/consumos, teléfono de soporte, etc.) **no se escriben en el texto**: se referencian
como parámetros con nombre (`{{horario_transferencias}}`, …) que se resuelven al publicar desde un juego de
parámetros configurable y aprobado (RF-05).

- **Dado** la base publicada, **cuando** se lista el contenido por familia, **entonces** hay al menos una pregunta
  frecuente publicada por cada tema mínimo de la tabla, en cada una de las cuatro familias.
- **Dado** el contenido mínimo, **cuando** se revisa, **entonces** ningún texto contiene montos, horarios o límites
  que no salgan de un parámetro aprobado.

**Origen del texto (decisión D-1, ver §7).** El equipo redacta un borrador de cada pregunta frecuente mínima usando
**solo** lo que dicen las especificaciones del canal, cuentas, tarjetas, seguros y agente (H3–H6, H9). Montos,
horarios y límites quedan como parámetros con nombre, sin cifras. Cada pieza entra como `borrador` y se publica recién
cuando se registra la aprobación del área dueña y de compliance (RF-03). En desarrollo y pruebas esa aprobación la
registra el equipo y queda **marcada como aprobación de prueba**; en un entorno real solo valen aprobaciones del banco.

- **Dado** una pieza del contenido mínimo, **cuando** se revisa su texto, **entonces** cada afirmación se puede
  rastrear a una sección de las especificaciones H3–H6/H9 (la pieza registra de qué sección sale).
- **Dado** una pieza publicada con aprobación de prueba, **cuando** se consulta su metadato de aprobación, **entonces**
  indica que la aprobación es de prueba.
- **Dado** un entorno configurado como real, **cuando** se intenta publicar una pieza cuya única aprobación es de
  prueba, **entonces** se rechaza.

### RF-03 — Flujo de aprobación (solo entra lo aprobado)

Cada pieza pasa por estados: `propuesta` (p. ej. aportada por soporte) → `borrador` → `en_revision` → `aprobada` →
`publicada`; y `retirada` (deja de servirse). Solo el contenido `publicada` se sirve en la consulta.

Aprobación (flujo R3 de la fuente): el área dueña aprueba el contenido y **compliance** valida los mensajes y
políticas publicables. Quiénes aprueban cada tipo de contenido y cuántas aprobaciones hacen falta es una **definición
pendiente del banco** (proceso de aprobación del conocimiento): se implementa como **configuración**, no como valor
fijo en el código. Por defecto (supuesto S-3): área dueña + compliance.

Cada aprobación registra rol del aprobador, fecha y versión aprobada. El proceso de actualización y aprobación queda
**documentado** en la carpeta del componente y **se puede ejecutar** con comandos (ingestar, enviar a revisión,
aprobar, publicar, retirar, volver a una versión anterior).

- **Dado** una pieza en `borrador`, **cuando** el agente consulta un tema que solo cubre esa pieza, **entonces** no
  aparece en los resultados.
- **Dado** una pieza con la aprobación del área dueña pero sin la de compliance (con la configuración por defecto),
  **cuando** se intenta publicar, **entonces** se rechaza indicando la aprobación que falta.
- **Dado** una pieza con todas las aprobaciones configuradas, **cuando** se publica, **entonces** se reindexa y queda
  disponible en la consulta sin reiniciar el componente.
- **Dado** una propuesta de soporte, **cuando** entra, **entonces** queda en `propuesta` y no se publica sin pasar
  por revisión y aprobación.
- **Dado** el documento del proceso, **cuando** una persona sigue sus pasos con una pieza de ejemplo, **entonces**
  la pieza termina publicada y consultable.

### RF-04 — Versionado y actualización periódica

Cada cambio en una pieza crea una **versión nueva**; las anteriores se conservan para auditoría y vuelta atrás. La
base tiene además una **versión del conocimiento** publicada (identifica el conjunto servido) que se devuelve en cada
consulta. La actualización periódica es un proceso ejecutable que toma el contenido aprobado nuevo o modificado y
reindexa solo lo cambiado; la frecuencia es configurable (no la fija la fuente).

Regla 5 de la fuente: un cambio de política exige actualizar la base **antes** de publicarla al canal. Por eso cada
pieza tiene vigencia: lo vencido o retirado no se sirve.

- **Dado** una pregunta frecuente publicada en versión 1, **cuando** se aprueba y publica la versión 2, **entonces**
  la consulta devuelve solo la versión 2 y la cita indica la versión 2.
- **Dado** una pieza publicada, **cuando** se retira o vence su vigencia, **entonces** deja de aparecer en la
  consulta.
- **Dado** una publicación con un error, **cuando** se vuelve a la versión anterior, **entonces** la consulta sirve
  otra vez la versión anterior y la versión del conocimiento cambia.
- **Dado** que se corre la actualización sin cambios aprobados, **cuando** termina, **entonces** no se reindexa nada y
  la versión del conocimiento no cambia.

### RF-05 — Parámetros del banco en el contenido

Los valores del banco (montos, horarios, límites, teléfonos de soporte) se definen en un juego de **parámetros**
versionado y aprobado como cualquier otro contenido, y se referencian por nombre en el texto. Al publicar, el texto se
resuelve con los valores vigentes; el agente los comunica tal cual (regla del programa).

- **Dado** una pieza que referencia `{{horario_transferencias}}` y ese parámetro no tiene valor aprobado, **cuando**
  se intenta publicar, **entonces** se rechaza indicando el parámetro faltante (no se publica con un valor inventado
  ni con el marcador a la vista).
- **Dado** que se aprueba un valor nuevo de un parámetro, **cuando** se publica, **entonces** se reindexan las piezas
  que lo usan y la consulta devuelve el valor nuevo.

### RF-06 — Consulta con cita

El agente envía la pregunta del cliente (texto) con filtros opcionales (familia, tipo, audiencia) y recibe los
fragmentos más relevantes del contenido publicado, cada uno con su **cita**, y una **señal de confianza** global. La
recuperación es por contexto (semántica, combinada con coincidencia de términos).

Cada resultado incluye: texto del fragmento (con parámetros resueltos), cita y puntaje. La **cita** incluye como
mínimo: identificador de la pieza, título, tipo, familia, versión, sección (si aplica), área dueña y fecha de
aprobación. Respuesta además: versión del conocimiento, `correlationId` recibido y nivel de confianza.

Flujo R1 de la fuente: "¿Cuál es el horario para transferir?" → recupera la política oficial de horarios de H4 →
el agente responde con el mensaje aprobado.

- **Dado** la pregunta "¿Cuál es el horario para transferir?" y la pregunta frecuente de horarios publicada, **cuando**
  se consulta, **entonces** el primer resultado es esa pregunta frecuente, con su cita completa y confianza alta.
- **Dado** una consulta con filtro de familia `seguros`, **cuando** se consulta, **entonces** todos los resultados
  son de la familia `seguros`.
- **Dado** cualquier resultado, **cuando** se devuelve, **entonces** trae su cita completa; no existe resultado sin
  cita.

### RF-07 — Confianza baja y sin respuesta

Si la mejor coincidencia no llega al **umbral de confianza** (configurable), la respuesta lo dice explícitamente con
nivel `baja` y una sugerencia para quien consulta: `derivar` (a soporte humano) o `aclarar` (pedir aclaración), que
son las dos salidas que da la fuente (regla 2). Si no hay nada relevante, devuelve cero resultados con nivel `sin_respuesta`
y sugerencia `derivar`. La base nunca completa ni genera texto: solo devuelve contenido aprobado.

Flujo R2 de la fuente: consulta no documentada → baja confianza → el agente ofrece derivación a soporte humano.

- **Dado** una pregunta sobre un tema que no está en la base (p. ej. un producto que el banco no documentó),
  **cuando** se consulta, **entonces** la respuesta trae nivel `sin_respuesta` o `baja`, sugerencia `derivar` y
  ningún texto inventado.
- **Dado** una pregunta ambigua que coincide parcialmente con varias piezas por debajo del umbral, **cuando** se
  consulta, **entonces** el nivel es `baja`, la sugerencia es `aclarar` y los candidatos vienen con sus citas.
- **Dado** que se cambia el umbral por configuración, **cuando** se repite la misma consulta, **entonces** el nivel
  de confianza refleja el umbral nuevo sin cambiar código.

### RF-08 — Separación público / interno

Cada pieza es `publico` (se puede mostrar al cliente) o `interno` (solo agente y soporte: guía de cómo responder, por
ejemplo). Por defecto la consulta devuelve **solo contenido público**. El contenido interno se devuelve solo si el
consumidor lo pide explícitamente **y** está habilitado para eso, y viene marcado como no publicable al cliente.
La habilitación la decide la configuración del componente por consumidor, no el texto de la pregunta.

- **Dado** una consulta sin indicar audiencia, **cuando** coincide con una pieza interna, **entonces** esa pieza no
  aparece.
- **Dado** un consumidor no habilitado para interno que pide audiencia `interno`, **cuando** consulta, **entonces**
  se rechaza la consulta con un error de permiso (sin devolver contenido).
- **Dado** el agente habilitado que pide audiencia `interno`, **cuando** consulta, **entonces** recibe la pieza
  interna marcada `publicable: false`.
- **Dado** un consumidor no identificado, **cuando** consulta, **entonces** se rechaza.

### RF-09 — Sin datos personales en la base

La base no guarda datos personales de clientes (regla 6). La ingesta revisa cada pieza y **rechaza** las que
contienen datos con forma de dato personal (documento de identidad, teléfono, correo, número de tarjeta, CBU/número
de cuenta) que no sean parámetros aprobados del banco (p. ej. el teléfono de soporte). Las consultas no se guardan
con su texto completo asociado a una persona: el componente no recibe ni guarda identidad del cliente.

- **Dado** una pieza cuyo texto incluye un número de documento o de tarjeta, **cuando** se ingiere, **entonces** se
  rechaza indicando el motivo y no entra a la base.
- **Dado** una consulta que incluye un dato personal en el texto de la pregunta, **cuando** se registra en logs,
  **entonces** el dato aparece enmascarado (`***1234`).
- **Dado** la base publicada, **cuando** se corre la verificación de datos personales sobre todo el contenido,
  **entonces** no encuentra ninguno.

### RF-10 — Tono y claridad del contenido

Los mensajes recuperados cumplen el tono del banco: claros, empáticos y sin jerga (regla 4 y regla transversal del
programa). Lo garantiza la validación de compliance en la aprobación (RF-03); la ingesta además avisa (sin bloquear)
textos que superen una longitud configurable para WhatsApp.

- **Dado** una pieza publicada, **cuando** se revisa su historial, **entonces** tiene registrada la validación de
  compliance (con la configuración por defecto).

## 4. Reglas de negocio (de la fuente)

1. Solo contenido **aprobado** entra a la base publicada al agente.
2. Ante duda o baja confianza, el agente **no inventa**: deriva a soporte o pide aclaración. La base da la señal
   (RF-07).
3. El conocimiento de productos debe estar alineado con los límites y políticas de H4, H5 y H6 (los valores, como
   parámetros aprobados).
4. Los mensajes al cliente son claros, empáticos y sin jerga.
5. Un cambio de política exige actualizar la base antes de publicarlo al canal.
6. No se almacenan datos personales del cliente en la base de conocimiento.
7. (Programa) Montos, horarios y límites los define el banco; se comunican tal cual.
8. (Programa) El componente informa; no opera productos ni reemplaza reglas de los servicios.

## 5. Requisitos no funcionales

- **Interfaz:** HTTP en el puerto `8102` del runtime local; contrato en `shared/contracts/wa-knowledge-rag/retrieval.md`
  con versión (`v1`). Si se cambia una vez consumido, se versiona y se mantiene el anterior.
- **Latencia:** la fuente no la fija. Supuesto S-5.
- **Trazabilidad:** cada consulta recibe y devuelve el `correlationId` y queda registrada (sin PII) con versión del
  conocimiento, piezas citadas y nivel de confianza.
- **Auditoría:** cada ingesta, aprobación, publicación, retiro y vuelta atrás queda registrada con rol, fecha y versión.
- **Privacidad:** logs sin datos personales (enmascarados); ningún secreto en el repositorio ni en logs.
- **Salud:** endpoint de salud que indica si la base y el índice están disponibles.
- **Disponibilidad ante falla:** si el índice o la base no están disponibles, la consulta devuelve un error técnico
  claro (no resultados vacíos que se confundan con `sin_respuesta`), para que el agente derive.

## 6. Supuestos

- **S-1.** El consumidor es el agente (H9) por HTTP; la identificación del consumidor es una credencial de servicio
  configurada por entorno (no es la credencial de sesión del cliente: la base no requiere JWT, según la fila del hito).
- **S-2.** Modelo de embeddings y almacenamiento: los del stack del equipo (PostgreSQL + pgvector), configurables; los
  define la arquitectura.
- **S-3.** Mientras el banco no defina el proceso de aprobación, la configuración por defecto exige aprobación del
  área dueña + compliance (lo que dice el flujo R3).
- **S-4.** Frecuencia de actualización periódica y umbral de confianza: configurables, con un valor por defecto que
  fija la arquitectura y queda documentado.
- **S-5.** Latencia objetivo por consulta: la propone la arquitectura y queda documentada; no es un requisito de la
  fuente.
- **S-6.** Idioma del contenido: español (el canal es en español según H9).
- **S-7.** El archivo `specs/scrum/backlog.md` con la tabla de definiciones pendientes que cita la orden **no está en el
  repositorio**; se tomaron las definiciones pendientes de la memoria del proyecto (todas configurables). La que aplica
  directamente acá es el proceso de aprobación del conocimiento; los límites/horarios de H4 y ventanas de H5 aplican
  como parámetros del contenido.

## 7. Preguntas abiertas

- **P-1 — resuelta (D-1).** ¿De dónde sale el texto del contenido mínimo (RF-02)? **Respuesta de la persona: opción
  1.** El equipo redacta los borradores solo a partir de H3–H6/H9, sin cifras (parámetros), entran como `borrador` y se
  publican únicamente con la aprobación registrada del área dueña y de compliance; en desarrollo y pruebas la aprueba
  el equipo y queda marcada como aprobación de prueba. Descartadas: base vacía hasta que el banco entregue textos
  (opción 2) y contenido de ejemplo inventado (opción 3).
- **P-2.** Valores de los parámetros del banco (horarios, límites, umbrales, ventanas, teléfono de soporte): pendientes
  del banco; mientras tanto las piezas que los usan no se pueden publicar en un entorno real (en desarrollo y pruebas
  se usan valores de prueba marcados como tales).

## 8. Criterios de aceptación (de la orden y la fuente)

- [ ] La base incluye las preguntas frecuentes mínimas de cada familia de producto (cuentas, tarjetas y seguros) y las
      del canal (RF-02).
- [ ] Una consulta informativa devuelve el contenido aprobado junto con su cita (RF-06).
- [ ] Ante baja confianza devuelve esa señal, para que quien consulta no invente la respuesta (RF-07).
- [ ] El proceso de actualización y aprobación de contenido queda documentado y se puede ejecutar (RF-03, RF-04).
- [ ] Solo entra a la base publicada el contenido aprobado, y no hay datos personales de clientes en ella (RF-03,
      RF-09).
- [ ] Los mensajes recuperados cumplen tono y claridad del banco (RF-10).
- [ ] Contrato publicado en `shared/contracts/wa-knowledge-rag/retrieval.md`.
