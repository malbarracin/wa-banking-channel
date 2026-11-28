---

title: Trazabilidad de requisitos a la fuente
status: aprobada
source: Notion — Canal WhatsApp Banco — Roadmap Backend
updated: 2026-11-27T21:04:07.498Z
---

# Trazabilidad de requisitos a la fuente

## Páginas leídas

| Página | Link |
|--------|------|
| Canal WhatsApp Banco — Roadmap Backend (convenciones, reglas transversales, límites entre componentes) | https://app.notion.com/p/36875bb08ca781ccb11ffff55847b197 |
| H8 — Base de conocimiento RAG | https://app.notion.com/p/38375bb08ca781e2accfdd9eff1722a4 |
| H9 — Agente conversacional WhatsApp (consumidor del contrato) | https://app.notion.com/p/38375bb08ca7813499c9d738fad34ebe |
| H4 — Servicio de Cuentas (temas y políticas del contenido) | https://app.notion.com/p/36875bb08ca7815e8642e555eebdfc6a |
| H5 — Servicio de Tarjetas (temas y políticas del contenido) | https://app.notion.com/p/36875bb08ca781bea9c8cb37c2ccdbbf |
| H6 — Servicio de Seguros (temas y políticas del contenido) | https://app.notion.com/p/36875bb08ca781129b47cd3ced785221 |

## `specs/wa-knowledge-rag/spec.md`

| Requisito | Sale de |
|-----------|---------|
| Propósito, carpeta, contrato, dependencias | H8 «Datos del componente», §1, §2; Roadmap «Reglas de construcción» |
| Puerto 8102 | Roadmap «Convenciones técnicas» |
| Fuera de alcance | H8 §3 «Fuera de alcance»; Roadmap «Límites entre componentes»; orden de la tarea («No incluye») |
| Actores y permisos | H8 §4 |
| RF-01 Tipos y familias | H8 §3 «En alcance» (FAQs, guías, políticas, mensajes; fuentes oficiales) y §7 (familias) |
| RF-02 Contenido mínimo | H8 §7; temas: H4 §3/§5, H5 §3/§5, H6 §3/§5, H9 §3/§8, Roadmap «Seguridad transversal»; origen del texto: decisión de la persona sobre P-1 (opción 1, borrador del equipo con aprobación registrada) |
| RF-03 Aprobación | H8 §5 regla 1, §6 R3, §4, §9 supuestos; memoria del proyecto (proceso de aprobación pendiente → configurable) |
| RF-04 Versionado | H8 §3 «actualización periódica con control de versiones», §5 regla 5, §9 riesgos |
| RF-05 Parámetros | Roadmap «Límites del banco»; H9 §5 regla 4; memoria del proyecto (definiciones pendientes configurables) |
| RF-06 Consulta con cita | H8 §3, §6 R1; H9 §7 «usa RAG con citas» |
| RF-07 Confianza baja | H8 §3, §5 regla 2, §6 R2, §7; H9 §7 |
| RF-08 Público / interno | H8 §3 «Separación entre conocimiento público al cliente e interno (solo agente/soporte)» |
| RF-09 Sin datos personales | H8 §5 regla 6; Roadmap «Privacidad»; orden de la tarea |
| RF-10 Tono | H8 §5 regla 4, §7; Roadmap «Mensajes claros» |
| No funcionales | Roadmap «Trazabilidad extremo a extremo», «Auditoría»; H9 §10 riesgos |
=======
title: Origen de los requisitos
status: aprobada
source: https://app.notion.com/p/36875bb08ca78197a853e85505bf49a5
updated: 2026-11-27T20:59:05.914Z
---

# Origen de los requisitos

| Campo | Valor |
|-------|-------|
| Proyecto | Canal WhatsApp Banco (`wa-banking-channel`) |
| Fuente | Notion |
| Link | https://app.notion.com/p/36875bb08ca78197a853e85505bf49a5 |
| Hito | H1 — Servicio de Usuarios |
| Roadmap | https://app.notion.com/p/36875bb08ca781ccb11ffff55847b197 |
| Actualizado | 2026-11-27 |

## Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|----------------|
| Alta de usuario bancario | usuarios | H1 § 3 (En alcance), § 5 Reglas 1–6, § 6 flujo U1, § 7 CA |
| Consulta de usuario por identificador | usuarios | H1 § 3 (En alcance), § 5 Regla 4, § 6 flujo U2, § 7 CA |
| Actualización de datos permitidos | usuarios | H1 § 3 (En alcance), § 5 Regla 5, § 6 flujo U3 |
| Cambio de estado operativo | usuarios | H1 § 3 (En alcance), § 5 Regla 3, § 6 flujo U4, § 7 CA |
| Restricción de uso para clientes no activos | usuarios | H1 § 5 Regla 3, § 7 CA |
| Auditoría de operaciones relevantes | usuarios | H1 § 3 (En alcance), § 5 Regla 5, § 7 CA |
| Validación de unicidad | usuarios | H1 § 3 (En alcance), § 5 Regla 2, § 6 flujo U1, § 7 CA |

## Decisiones del banco (configurables, no inventadas)

Las siguientes definiciones están explícitamente fuera de los valores que fija la especificación. Se implementan como parámetros configurables, según la decisión del equipo del programa (anotada en la memoria del proyecto):

- Datos mínimos obligatorios del alta
- Regla de unicidad del cliente
- Campos permitidos para actualización
- Política de privacidad (datos consultables por otros servicios)
- Transiciones de estado permitidas

## Datos del componente (fuente: roadmap Notion)

| Dato | Valor |
|------|-------|
| Carpeta | `services/wa-users-service` |
| Grupo Maven | `com.bcra` |
| Paquete base | `com.bcra.users.ws` |
| Contrato OpenAPI | `shared/contracts/wa-users-service/openapi.yaml` |
| Contrato handoff | `shared/contracts/wa-users-service/handoff.md` |
| Puerto (contenedor) | `8080` (el puerto en la PC lo define el runtime local) |
| Dependencias | Ninguna (primer servicio de dominio) |

