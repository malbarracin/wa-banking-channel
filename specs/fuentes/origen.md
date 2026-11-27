---
title: Trazabilidad de requisitos a la fuente
status: borrador
source: Notion — Canal WhatsApp Banco — Roadmap Backend
updated: 2026-11-27T21:01:24.284Z
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
