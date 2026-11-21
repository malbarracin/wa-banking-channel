---
title: Trazabilidad de fuentes — Servicio de Usuarios
status: borrador
source: Página de Notion
  https://app.notion.com/36875bb08ca78197a853e85505bf49a5; orden del paso de
  analista; reglas del rol analista; skill ia-specs
updated: 2026-11-21T03:48:36.913Z
---

# Trazabilidad de fuentes — Servicio de Usuarios (H1)

## Fuente de requisitos

| Fuente | URL / ubicación | Qué se leyó |
|---|---|---|
| Página de Notion | https://app.notion.com/36875bb08ca78197a853e85505bf49a5 | Especificación completa del hito H1 — Servicio de Usuarios (propósito, alcance, actores, reglas de negocio, flujos, criterios de aceptación, dependencias, riesgos y supuestos). |
| Orden del equipo (paso de analista) | `.opensoma/runs/20261121-e564/steps/001-analista/prompt.md` | Tarea P-4, carpeta del componente, dominio, tecnologías y contratos que debe dejar escritos este trabajo. |
| Reglas del equipo (rol analista) | `.agents/opensoma/team-analista.md` | Método de trabajo, prohibiciones y checklist de entrega de la especificación. |
| Skill `ia-specs` | `.agents/skills/ia-specs/SKILL.md` | Estructura y secciones obligatorias de la especificación. |

## Origen de cada requisito

| Requisito (spec.md) | Sección de la fuente | Base |
|---|---|---|
| Carpeta del componente y contratos | Datos del componente (callout) y orden del equipo | `services/wa-users-service`, `shared/contracts/wa-users-service/openapi.yaml`, `shared/contracts/wa-users-service/handoff.md` |
| Fuera de alcance | Sección 3 (Fuera de alcance) | Vinculación WhatsApp, verificación de canal, credencial de sesión, productos, datos legales sensibles, onboarding comercial |
| Actores y permisos | Sección 4 (Actores y permisos) | Cuadro de actores y qué puede hacer cada uno |
| R1 Alta de usuario | Sección 3 (En alcance) + Flujo U1 + Regla 2, 6 | Alta con datos mínimos, unicidad, datos incompletos |
| R2 Consulta de usuario | Sección 3 (En alcance) + Flujo U2 | Consulta por identificador acordado, datos permitidos |
| R3 Actualización de datos | Sección 3 (En alcance) + Flujo U3 + Regla 5 | Campos permitidos, auditoría |
| R4 Cambio de estado | Sección 3 (En alcance) + Flujo U4 | Estados activo/suspendido/baja lógica, notificación a dependientes |
| R5 Validación de unicidad | Sección 3 (En alcance) + Regla 2 | No duplicar documento |
| R6 Auditoría | Sección 3 (En alcance) + Regla 5 | Fecha, acción y resultado |
| Reglas de negocio 1-6 | Sección 5 (Reglas de negocio) | Copiadas tal cual de la fuente |
| Requisitos no funcionales | Reglas `ia-boundaries.md` (trazabilidad con `correlationId`, privacidad, mensajes sin jera) + fuente | Trazabilidad, privacidad, auditoría, mensajes claros |
| Supuestos | Sección 9 (Supuestos) | Datos mínimos de Compliance, política de estados, sincronización de otros sistemas |
| Preguntas abiertas | Análisis del analista (datos faltantes materiales) | Datos mínimos de alta, identificador de consulta, campos permitidos, contrato existente |
