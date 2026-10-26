---
title: Origen de los requisitos
status: aprobada
source: https://app.notion.com/p/H2-Servicio-de-Acceso-y-Sesi-n-38275bb08ca781c8b7d5f5a2c29b8770
updated: 2026-10-26T18:19:37.757Z
---

# Origen de los requisitos

## H1 — Servicio de Usuarios

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

### Decisiones tomadas con la persona

| Pregunta | Resolución | Dónde queda |
|----------|------------|-------------|
| Campos mínimos de alta e identificador único (U1/U2) | Opción A: documento (tipo+número) + nombre + apellido + fecha de nacimiento + un dato de contacto (email o teléfono). Identificador único: documento. | `usuarios/spec` — sección "Decisión registrada" y requisitos "Alta de usuario bancario", "Unicidad por documento", "Consulta de usuario por identificador" |

### Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|-----------------|
| Alta de usuario bancario | usuarios | H1 — sección 6, flujo U1; sección 5, regla 6; sección 7, criterio 1 |
| Unicidad por documento | usuarios | H1 — sección 5, regla 2; sección 7, criterio 2 |
| Consulta de usuario por identificador | usuarios | H1 — sección 6, flujo U2; sección 5, regla 4; sección 7, criterio 3 |
| Actualización de datos permitidos | usuarios | H1 — sección 3 (en alcance); sección 6, flujo U3 |
| Cambio de estado del usuario | usuarios | H1 — sección 6, flujo U4; sección 7, criterio 4 |
| Restricción de vínculo de canal para usuarios no activos | usuarios | H1 — sección 5, regla 1 y regla 3; sección 7, criterio 5; sección 8 (dependencia con H3) |
| Auditoría de operaciones del usuario | usuarios | H1 — sección 5, regla 5; sección 7, criterio 4 |

## H3 — Servicio Canal WhatsApp

| Campo | Valor |
|-------|-------|
| Proyecto | wa-banking-channel (services/wa-channel-service) |
| Fuente | Notion |
| Link | https://app.notion.com/p/H3-Servicio-Canal-WhatsApp-38275bb08ca781c0bc08d80366637aa5 |
| Página | 💬 H3 — Servicio Canal WhatsApp (Hitos — Canal WhatsApp Banco) |
| Actualizado | 2026-10-26 |

La página de Notion es autocontenida para este hito: no tiene subpáginas ni bases de datos hijas en alcance más
allá de las propiedades del hito (Dominio: Canal). El hito depende funcionalmente de H1 (ya especificado en
`usuarios/spec`, consulta de existencia/estado de cliente) y de H2 (emisión/revocación de credencial, fuera de
este hito). Varios puntos quedan explícitamente delegados por la fuente a una política del banco/Compliance aún
no definida (método de verificación, límite de intentos, criterios de bloqueo por riesgo): se dejaron como
`## Preguntas abiertas` en `canal-whatsapp/spec` sin bloquear el resto del hito, siguiendo el mismo criterio que
`usuarios/spec` con la política de privacidad pendiente de Compliance.

### Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|-----------------|
| Aceptación de términos del canal antes de identificarse | canal-whatsapp | H3 — sección 6.1, paso 2; sección 6.1 regla 1; sección 7 (criterio de bienvenida/términos) |
| Vinculación con cliente existente y activo en H1 | canal-whatsapp | H3 — sección 5, regla 2; sección 6.1, paso 4; sección 6.1 flujo alternativo; sección 7 (criterios de cliente inexistente/suspendido) |
| Verificación de identidad con límite de intentos | canal-whatsapp | H3 — sección 5, regla 5; sección 6.1, paso 5; sección 7 (criterio de intentos agotados) |
| Unicidad del vínculo activo por número | canal-whatsapp | H3 — sección 5, regla 1; sección 6.1, paso 6; sección 7 (criterio de segundo vínculo rechazado) |
| Resultado verificable de identidad/vínculo para la credencial de sesión | canal-whatsapp | H3 — sección 1 (resumen); sección 5, regla 3; sección 6.1, paso 8; sección 7 (criterio de credencial coordinada con H2) |
| Consulta de perfil en el canal | canal-whatsapp | H3 — sección 3 (en alcance); sección 5, regla 6; sección 6, flujo F2 |
| Preferencias del canal | canal-whatsapp | H3 — sección 3 (en alcance); sección 6, flujo F2 |
| Bloqueo del vínculo | canal-whatsapp | H3 — sección 3 (en alcance); sección 5, regla 4; sección 6, flujo F3; sección 7 (criterio de bloqueo/desvinculación) |
| Desvinculación del canal | canal-whatsapp | H3 — sección 3 (en alcance); sección 5, regla 4; sección 6, flujo F3; sección 7 (criterio de bloqueo/desvinculación) |
| Re-vinculación tras cambio de dispositivo o número | canal-whatsapp | H3 — sección 3 (en alcance); sección 5, regla 7; sección 6, flujo F4; sección 7 (criterio de re-vinculación) |
| Consulta de vínculo por número (anti-duplicados y fraude) | canal-whatsapp | H3 — sección 3 (en alcance); sección 5, regla 1 |
| Historial resumido de interacciones para soporte | canal-whatsapp | H3 — sección 3 (en alcance); sección 4 (actor soporte) |
| Auditoría de las acciones del vínculo | canal-whatsapp | H3 — sección 5, regla 8; sección 6.1, regla 6; sección 7 (criterio de secuencia auditada) |

## H2 — Servicio de Acceso y Sesión

| Campo | Valor |
|-------|-------|
| Proyecto | wa-banking-channel (services/wa-auth-service) |
| Fuente | Notion |
| Link | https://app.notion.com/p/H2-Servicio-de-Acceso-y-Sesi-n-38275bb08ca781c8b7d5f5a2c29b8770 |
| Página | 🔐 H2 — Servicio de Acceso y Sesión (Hitos — Canal WhatsApp Banco) |
| Actualizado | 2026-10-26 |

La página de Notion es autocontenida para este hito: no tiene subpáginas ni bases de datos hijas en alcance (sin
backlinks en el workspace), y no se encontró ninguna página complementaria de política de vigencia, renovación o
riesgo. El hito depende funcionalmente de H1 (el cliente debe existir) y de H3 (quien gestiona la verificación y
el vínculo activo y solicita la emisión/revocación); H4, H5 y H6 dependen de H2 para validar la credencial antes
de operar. La propia fuente excluye de su alcance el "detalle técnico del formato de la credencial", y deja sin
cerrar valores concretos de vigencia, ventana de renovación, señal de riesgo y alcance de la revocación masiva
("política del banco"/"política de riesgo"): se dejaron como `## Preguntas abiertas` en `acceso-sesion/spec` sin
bloquear el resto del hito, con el mismo criterio que las políticas pendientes de `usuarios/spec` y
`canal-whatsapp/spec`.

### Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|-----------------|
| Emisión de credencial tras verificación exitosa | acceso-sesion | H2 — sección 5, regla 1; sección 6, flujo A1; sección 7, criterio 1 |
| Una credencial activa por vínculo número–cliente | acceso-sesion | H2 — sección 5, regla 2 |
| Renovación de la credencial sin repetir la verificación completa | acceso-sesion | H2 — sección 5, regla 3; sección 6, flujo A2 |
| Revocación inmediata de la credencial | acceso-sesion | H2 — sección 5, regla 4; sección 6, flujo A3; sección 7, criterio 3; sección 4 (actor Banco) |
| Validación de credencial para los servicios de productos | acceso-sesion | H2 — sección 3 (en alcance); sección 4 (actor Servicios de productos); sección 7, criterio 2 |
| La credencial nunca se expone en texto al cliente | acceso-sesion | H2 — sección 5, regla 5; sección 7, criterio 4 |
| Registro de emisiones y revocaciones | acceso-sesion | H2 — sección 3 (en alcance, "Registro de emisiones y revocaciones") |
