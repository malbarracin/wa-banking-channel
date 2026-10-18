---
title: Origen de los requisitos
status: borrador
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-10-18T19:55:52.200Z
---

# Origen de los requisitos

| Campo | Valor |
|-------|-------|
| Proyecto | wa-users-service |
| Fuente | Notion — H1 Servicio de Usuarios |
| Link | https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5 |
| Roadmap (project.yaml) | https://app.notion.com/p/Canal-WhatsApp-Banco-Roadmap-Backend-36875bb08ca781ccb11ffff55847b197 |
| Actualizado | 2026-10-18 |

## Decisiones de persona

| Tema | Decisión | Nota |
|------|----------|------|
| Datos mínimos de alta + unicidad | Tipo y número de documento + nombre + contacto + fecha de nacimiento; unicidad por tipo+número de documento | Resolved via user decision: opción 3 (2026-10-18) |

## Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|-----------------|
| Alta con datos mínimos | usuarios | H1 §3 En alcance (alta); §5 reglas 1 y 6; §6 U1; §7 AC alta |
| Unicidad por documento | usuarios | H1 §3 En alcance (unicidad); §5 regla 2; §7 AC duplicado; decisión persona 2026-10-18 |
| Consulta por identificador | usuarios | H1 §3 En alcance (consulta); §6 U2; §7 AC consulta; H3 §6.1 paso 4 (consulta H1 por documento/ID) |
| Actualización de datos permitidos | usuarios | H1 §3 En alcance (actualización); §3 Fuera de alcance (datos legales); §6 U3 |
| Cambio de estado operativo | usuarios | H1 §3 En alcance (estados); §5 regla 5; §6 U4; §7 AC estado auditado |
| Aptitud para vínculo de canal | usuarios | H1 §5 reglas 1 y 3; §7 AC suspendido/baja; H3 §5 regla 2 y §6.1 paso 4 |
| Auditoría de altas y cambios relevantes | usuarios | H1 §3 En alcance (auditoría); §5 regla 5; §7 AC cambio de estado auditado |

## Referencias cruzadas (no amplían alcance de H1)

| Fuente | Uso |
|--------|-----|
| H3 — Servicio Canal WhatsApp | Confirma que H1 se consulta por documento/ID y que solo cliente **activo** se vincula; el vínculo WhatsApp sigue fuera de alcance de este servicio |
