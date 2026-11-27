---
title: Origen de los requisitos
status: borrador
source: https://app.notion.com/p/36875bb08ca78197a853e85505bf49a5
updated: 2026-11-27T20:57:04.908Z
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
