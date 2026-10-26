---
title: Origen de los requisitos
status: borrador
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-10-26T12:23:14.196Z
---

# Origen de los requisitos

| Campo | Valor |
|-------|-------|
| Proyecto | wa-banking-channel (services/wa-users-service) |
| Fuente | Notion |
| Link | https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5 |
| Página | 👤 H1 — Servicio de Usuarios (Hitos — Canal WhatsApp Banco) |
| Actualizado | 2026-10-26 |

La página de Notion es autocontenida para este hito: no tiene subpáginas ni bases de datos hijas en alcance más
allá de las propiedades del hito (Dominio: Usuarios). Se buscó en el workspace una política de estados de
usuario o de privacidad de datos que complementara el hito y no se encontró ninguna: queda registrado como
pregunta abierta en `usuarios/spec`.

## Decisiones tomadas con la persona

| Pregunta | Resolución | Dónde queda |
|----------|------------|-------------|
| Campos mínimos de alta e identificador único (U1/U2) | Opción A: documento (tipo+número) + nombre + apellido + fecha de nacimiento + un dato de contacto (email o teléfono). Identificador único: documento. | `usuarios/spec` — sección "Decisión registrada" y requisitos "Alta de usuario bancario", "Unicidad por documento", "Consulta de usuario por identificador" |

## Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|-----------------|
| Alta de usuario bancario | usuarios | H1 — sección 6, flujo U1; sección 5, regla 6; sección 7, criterio 1 |
| Unicidad por documento | usuarios | H1 — sección 5, regla 2; sección 7, criterio 2 |
| Consulta de usuario por identificador | usuarios | H1 — sección 6, flujo U2; sección 5, regla 4; sección 7, criterio 3 |
| Actualización de datos permitidos | usuarios | H1 — sección 3 (en alcance); sección 6, flujo U3 |
| Cambio de estado del usuario | usuarios | H1 — sección 6, flujo U4; sección 7, criterio 4 |
| Restricción de vínculo de canal para usuarios no activos | usuarios | H1 — sección 5, regla 1 y regla 3; sección 7, criterio 5; sección 8 (dependencia con H3) |
| Auditoría de operaciones del usuario | usuarios | H1 — sección 5, regla 5; sección 7, criterio 4 |
