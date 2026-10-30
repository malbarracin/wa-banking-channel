---
title: Base de conocimiento RAG (H8) — especificación
status: borrador
source: null
updated: 2026-10-30T20:57:41.140Z
---

# Base de conocimiento RAG (H8) — especificación

## Propósito

La Base de conocimiento RAG (H8) centraliza el conocimiento oficial del banco (productos, políticas, FAQs, guías de respuesta y mensajes aprobados) para que el agente conversacional (H9) responda con precisión, citas y sin inventar información. 

Complementa a H9 con contexto documental. **No** ejecuta operaciones bancarias (eso corresponde a H7 + H1–H6), **no** reemplaza las reglas de negocio de los servicios y **no** gestiona el ciclo técnico del mensaje (H0.2).

## Fuera de alcance (del roadmap y de la fuente)

- Ejecutar transferencias, consultas de saldo u operaciones bancarias (corresponde a H7/MCP y H4–H6).
- Datos transaccionales en tiempo real del cliente (provienen de los servicios vía H7/MCP).
- Entrenamiento del modelo base del proveedor de IA.
- Contenido no aprobado por el banco.
- Lógica de negocio propia de H1–H6.
- Conversación con el cliente, orquestación o consumo directo de Kafka/Twilio (H9/H0/H0.1/H0.2).
- Definición de capacidades MCP (H7).

## Actores y permisos

| Actor | Qué puede hacer |
| --- | --- |
| Áreas de negocio / producto | Aportar y proponer contenido de conocimiento para el canal. |
| Compliance / legal | Validar y aprobar contenido publicable al cliente o interno. |
| Agente conversacional (H9) | Consultar conocimiento contextual en cada respuesta informativa. |
| Soporte | Proponer nuevas FAQs a partir de casos reales. |
| Banco (operaciones/seguridad) | Controlar publicación, versionado y baja de contenido. |

## Requisitos

### Requisito: Solo contenido aprobado entra a la base publicada
El sistema DEBE garantizar que **únicamente** contenido aprobado pase a la base consultada por el agente (H9). El contenido no aprobado no debe estar indexado para respuestas al cliente.

#### Escenario: Contenido aprobado indexado
- **DADO** contenido validado por Compliance/Legal y marcado como aprobado
- **CUANDO** se ejecuta el proceso de publicación/indexación
- **ENTONCES** queda disponible para consulta por el agente con su versión correspondiente
- **Y** se registra quién aprobó, cuándo y la versión

#### Escenario: Contenido en revisión no es consultable
- **DADO** contenido en estado "en revisión" o "rechazado"
- **CUANDO** el agente consulta la base
- **ENTONCES** no es recuperado para respuestas al cliente
- **Y** no se expone en el resultado de retrieval

### Requisito: Recuperación con citas y confianza
El RAG DEBE devolver, por cada resultado relevante, **citas** (fuente/origen del fragmento) y un **nivel de confianza**. El agente DEBE usar estas citas en respuestas informativas cuando corresponda.

#### Escenario: Respuesta informativa con citas
- **DADO** una pregunta informativa con respuesta disponible en contenido aprobado
- **CUANDO** el agente consulta H8
- **ENTONCES** H8 devuelve fragmentos con su cita (fuente, sección/página o identificador) y confianza
- **Y** el agente responde al cliente citando o referenciando el contenido oficial cuando aplique

#### Escenario: Baja confianza o sin resultados
- **DADO** una pregunta sin respuesta suficiente en la base o con confianza por debajo del umbral
- **CUANDO** el agente consulta H8
- **ENTONCES** H8 indica "sin respuesta suficiente" / baja confianza
- **Y** el agente **no inventa** la respuesta: ofrece derivación a soporte humano o pide aclaración, conforme a las reglas transversales

### Requisito: Separación público / interno
El contenido DEBE distinguir entre **público al cliente** e **interno** (solo agente/soporte). Solo el contenido público debe utilizarse para respuestas directas al cliente; el contenido interno solo puede usarse para guiar al agente o derivar, nunca exponer detalles internos al cliente.

#### Escenario: Consulta con contenido mixto
- **DADO** resultados que incluyen fragmentos públicos e internos
- **CUANDO** el agente genera la respuesta al cliente
- **ENTONCES** solo se utilizan fragmentos públicos para el texto al cliente
- **Y** los fragmentos internos se usan únicamente para razonamiento/derivación, sin exponerlos

#### Escenario: Respuesta basada solo en interno
- **DADO** que la única respuesta disponible es conocimiento interno
- **CUANDO** el agente consulta
- **ENTONCES** no responde con ese contenido al cliente
- **Y** deriva a soporte humano con contexto adecuado (sin exponer información interna)

### Requisito: Alineación con políticas de producto (H4–H6)
El conocimiento de productos DEBE estar alineado con límites, horarios y políticas definidos por los servicios H4 (Cuentas), H5 (Tarjetas) y H6 (Seguros). Ante contradicción entre RAG y regla vigente del producto, **prevalece la regla del banco vigente** y el agente debe derivar o aclarar sin inventar excepciones.

#### Escenario: Consulta sobre límite de producto
- **DADO** una pregunta sobre límite/horario de un producto
- **CUANDO** el agente responde
- **ENTONCES** usa el contenido aprobado de H8 alineado a la política vigente
- **Y** si hay duda de vigencia, el agente indica que confirma con soporte o deriva, sin inventar montos/horarios

#### Escenario: Política desactualizada detectada
- **DADO** contenido desactualizado respecto a política vigente
- **CUANDO** se detecta o se actualiza
- **ENTONCES** el contenido se retira/reindexa antes de seguir publicándolo
- **Y** se registra la actualización con trazabilidad

### Requisito: Actualización, aprobación y versionado
El proceso de actualización DEBE requerir aprobación (Negocio + Compliance/Legal según corresponda), generar una nueva versión del conocimiento y quedar trazado. Debe existir mecanismo de reindexación.

#### Escenario: Publicación con aprobación
- **DADO** nuevos o modificados fragmentos con aprobación registrada
- **CUANDO** se publican
- **ENTONCES** se crea una nueva versión de la base/conjunto de conocimiento
- **Y** queda registrado quién, cuándo, qué cambió y versión resultante

#### Escenario: Rollback o baja
- **DADO** contenido que debe retirarse por política
- **CUANDO** se aprueba la baja
- **ENTONCES** deja de estar disponible para consulta
- **Y** queda trazado con motivo

### Requisito: Sin PII ni datos transaccionales en la base
La base de conocimiento NO DEBE contener datos personales del cliente, ni saldos, movimientos, números de tarjeta completos, ni otra información transaccional en tiempo real.

#### Escenario: Carga de contenido con PII
- **DADO** contenido que contiene documento/teléfono/nombre/saldo
- **CUANDO** se intenta publicar
- **ENTONCES** el proceso rechaza la carga
- **Y** solicita corrección

#### Escenario: Consulta no requiere datos transaccionales
- **DADO** una consulta informativa
- **CUANDO** el agente consulta H8
- **ENTONCES** solo se recupera conocimiento documental/aprobado, nunca datos transaccionales en tiempo real (esos vienen de H7/MCP)

## Reglas de negocio

1. **Solo contenido aprobado** entra a la base publicada al agente.
2. **No inventar**: ante duda o baja confianza, el agente no inventa. Deriva a soporte humano o pide aclaración.
3. **Conocimiento alineado a productos**: el contenido de H4–H6 debe estar alineado con límites y políticas vigentes.
4. **Mensajes claros y empáticos**: tono del banco, sin jerga, sin stack traces ni detalles técnicos al cliente.
5. **Separación público/interno**: solo contenido público al cliente; contenido interno no se expone.
6. **Sin PII**: no se almacenan datos personales ni transaccionales en la base de conocimiento.
7. **Citas obligatorias cuando aplica**: para respuestas basadas en conocimiento aprobado, usar referencias/citas provistas por H8.
8. **Derivación ante ambigüedad o riesgo**: si la consulta implica operación sensible, derivación correcta según flujo (identidad/credencial) o a humano.
9. **Actualización requiere aprobación**: cambios de política exigen actualización y aprobación antes de publicar al canal.
10. **Trazabilidad**: aprobaciones, versiones, reindexaciones y consultas relevantes quedan trazadas.

## Requisitos no funcionales

- **Recuperación con trazabilidad**: cada resultado debe incluir cita/origen y confianza para auditoría.
- **Versionado**: la base publicada debe tener versión identificable; cambios generan nueva versión.
- **Privacidad**: sin PII en contenido, logs y trazas; enmascarado si aplica.
- **Disponibilidad para H9**: debe estar disponible cuando el agente requiera conocimiento informativo (en paralelo con H7).
- **Actualización controlada**: proceso de aprobación y reindexación documentado.
- **Alineación a contratos**: publicar contrato de retrieval/citas en `shared/contracts/wa-knowledge-rag/` para consumo de H9.

## Supuestos

- Áreas de negocio aportan contenido y Compliance/Legal definen el proceso de aprobación.
- Políticas de H4–H6 están definidas o se irán alineando; el conocimiento debe referirse a versiones vigentes.
- H9 consume el contrato de retrieval de H8 tal como se publique en `shared/contracts/`.
- Los umbrales de confianza y el formato exacto de citas se definirán en el contrato/arquitectura de H8, manteniéndose parametrizables.
- El contenido inicial se proveerá por las áreas correspondientes antes de habilitar respuestas informativas masivas.

## Preguntas abiertas

- **Umbral de confianza**: ¿qué valor/criterio define "baja confianza" que obliga a derivar (numérico o categórico)? No está definido en la fuente.
- **Formato exacto de citas**: ¿qué campos obligatorios debe tener la cita (documento, sección, URL, hash, fecha de vigencia)? A definir en `shared/contracts/wa-knowledge-rag/retrieval.md`.
- **Categorías público/ interno**: ¿criterios precisos para clasificar cada tipo de fragmento? Requiere acuerdo con Negocio/Compliance.
- **Frecuencia de actualización**: ¿actualización continua o por lote? ¿quién aprueba cambios menores vs mayores?
- **Retención/versionado**: ¿cuántas versiones se mantienen y por cuánto tiempo?
- **Alcance inicial de FAQs**: ¿lista mínima obligatoria por producto para pasar a integración con H9? Cruzar con H9/H4–H6.
