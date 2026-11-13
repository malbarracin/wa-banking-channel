---
title: Origen de los requisitos
status: aprobada
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-11-13T20:46:26.217Z
---

# Origen de los requisitos

| Campo | Valor |
|-------|-------|
| Proyecto | mi-proyecto |
| Fuente | Notion |
| Link | https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5 |
| Actualizado | 2026-11-13 |

## Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|----------------|
| Alta de usuario bancario | usuarios | H1 §6 U1, §5 regla 6, §7 AC1 |
| Consulta de usuario | usuarios | H1 §6 U2, §5 regla 4, §7 AC3 |
| Actualización de datos permitidos | usuarios | H1 §6 U3 |
| Cambio de estado operativo | usuarios | H1 §6 U4, §5 regla 3, §7 AC4 |
| Unicidad del cliente | usuarios | H1 §5 regla 2, §7 AC2 |
| Restricción de vínculo de canal según estado | usuarios | H1 §5 regla 3, §7 AC5 |
| Auditoría de altas, cambios de estado y modificaciones | usuarios | H1 §5 regla 5, §3 en alcance |
| Existencia previa del cliente para operar por canal | usuarios | H1 §5 regla 1, §8 |

## Decisiones resueltas (fuera de la fuente, dadas por la persona)

- Datos mínimos obligatorios del alta = conjunto completo de Compliance (tipo y número de documento, nombre, apellido, email, teléfono, dirección, fecha de nacimiento). Resuelto vía decisión de la persona (opción C).
- Regla de unicidad = par tipo + número de documento único Y email único, cada uno de forma independiente. Resuelto vía decisión de la persona (opción C).

## Preguntas abiertas

- Datos visibles por actor en la consulta (área de negocio/operaciones, soporte, otros servicios): el banco debe definir el conjunto de campos autorizados por actor.
