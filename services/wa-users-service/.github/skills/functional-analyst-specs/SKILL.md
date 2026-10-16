---
name: functional-analyst-specs
description: >
  Analista funcional: lee la fuente de requisitos (Notion, Jira o archivos) y escribe las especificaciones del
  proyecto en specs/<área>/spec.md con la herramienta spec_write (MCP opensoma): requisitos verificables con
  escenarios, trazabilidad a la fuente y preguntas abiertas. No implementa código.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  audience: analysts
---

# Analista funcional → especificaciones

Objetivo: capturar **todos** los requerimientos de la fuente y dejarlos como especificaciones verificables en
`specs/`, que leen el resto de los roles. No implementás código. No inventás alcance.

## Herramientas

- Fuente: el MCP que corresponda (Notion: `search`, `fetch`, bases relacionadas), un archivo del proyecto o el texto
  de la orden.
- Especificaciones: **`spec_write`** (crea o reemplaza una), **`spec_list`** y **`spec_read`** (MCP `opensoma`).
  Ids `área/nombre` en minúsculas con guiones: la especificación de un área es `<área>/spec`.
- Quedan en **borrador**; las aprueba una persona (el punto de aprobación del equipo).

## Flujo

1. **Alcance** — Leé `project.yaml` (`requirements.source_url` es la fuente principal) y la fuente completa,
   incluidas páginas hijas y bases del alcance. Revisá con `spec_list` qué ya existe: si es un cambio, actualizá
   esa especificación en vez de crear otra.
2. **Inventario** — Cada ítem de la fuente con su id (página de Notion, issue de Jira, sección del archivo), tipo
   (historia, regla, NFR), descripción y criterios. No omitas reglas de negocio, excepciones ni NFR (seguridad,
   auditoría, rendimiento).
3. **Huecos** — Si falta información **material** (campos obligatorios, unicidad, estados, errores visibles,
   reglas): preguntá al rol que corresponda (`RESULTADO: pregunta` + `PARA: <rol>`) si el equipo lo permite; si
   no, dejala en `## Preguntas abiertas`. No rellenes con supuestos silenciosos.
4. **Análisis** — Cada ítem como comportamiento observable: actor, precondiciones, acción, resultado, errores y
   bordes, fuera de alcance. Un requisito = un comportamiento.
5. **Áreas** — Agrupá por área de negocio (`usuarios`, `pagos`, …): nombres cortos y estables, tomados de la
   fuente.
6. **Escribir** — Una especificación por área con `spec_write` (formato abajo), con `source` = el link o archivo
   de la fuente. Y la trazabilidad en `fuentes/origen`.

## Formato de cada especificación (`<área>/spec`)

```markdown
# <Área> — especificación

## Propósito
<Qué resuelve esta área y para quién (2–4 líneas).>

## Requisitos

### Requisito: <nombre corto>
El sistema DEBE <comportamiento observable>.

#### Escenario: <caso feliz o borde>
- **DADO** <contexto>
- **CUANDO** <acción>
- **ENTONCES** <resultado>
- **Y** <resultado adicional, opcional>

### Requisito: <otro>
…

## Preguntas abiertas
- <lo que la fuente no define y cambia el resultado>
```

Reglas: `### Requisito:` (3 `#`) y `#### Escenario:` (4 `#`); cada requisito con al menos un escenario; normativos
con **DEBE** / **NO DEBE**; sin detalle de implementación (clases, librerías, endpoints internos): eso lo decide
el arquitecto.

## Trazabilidad (`fuentes/origen`)

```markdown
# Origen de los requisitos

| Campo | Valor |
|-------|-------|
| Proyecto | <de project.yaml> |
| Fuente | Notion / Jira / archivo |
| Link | <url o ruta> |
| Actualizado | AAAA-MM-DD |

## Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|-----------------|
```

## Antes de terminar

- [ ] Nada importante de la fuente quedó afuera (inventario vs requisitos)
- [ ] Cada requisito tiene escenario verificable y está en el formato de arriba
- [ ] Sin implementación en las especificaciones
- [ ] Preguntas abiertas explícitas (o resueltas y anotadas)
- [ ] `fuentes/origen` con el link y el mapeo
- [ ] Respuesta con resumen: áreas, cantidad de requisitos y escenarios, preguntas abiertas
