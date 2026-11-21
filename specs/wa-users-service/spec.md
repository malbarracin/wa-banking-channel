---
title: Especificación — Servicio de Usuarios (H1)
status: borrador
source: https://app.notion.com/36875bb08ca78197a853e85505bf49a5 (página "👤 H1 —
  Servicio de Usuarios") y orden del equipo en el paso de analista
updated: 2026-11-21T03:48:27.195Z
---

# Especificación — Servicio de Usuarios (H1)

## Propósito

Administrar a las personas clientes del banco: dar de alta un nuevo cliente, consultarlo, actualizar los datos que el banco autoriza en este servicio y gestionar su estado operativo (activo, suspendido, baja lógica). Es el primer servicio de dominio del piloto; es la base sobre la cual otros servicios identifican al cliente.

## Carpeta del componente y de sus contratos

- Componente: `services/wa-users-service` (Java, paquete base `com.bcra.users.ws`, escucha en el puerto 8080 dentro de su contenedor)
- Dominio: Usuarios
- Carpeta de contratos: `shared/contracts/wa-users-service/` (contratos: `openapi.yaml` y `handoff.md`)
- Depende de: nada (primer servicio de dominio; puede empezar junto con H0.1)
- Entregable del componente: el servicio funcionando y su contrato publicado

## Fuera de alcance

Copiado de los límites de la fuente (no invado otro componente):

- Vincular número de WhatsApp (H3 — Canal WhatsApp).
- Verificación de identidad por el canal (H3).
- Emisión o revocación de credencial de sesión del canal (H2 — Acceso y Sesión).
- Consulta u operación de productos: cuentas, tarjetas, seguros (H4, H5, H6).
- Cambio de datos legales sensibles (domicilio legal, estado civil, etc.) salvo política expresa del banco.
- Onboarding comercial completo de productos.

## Actores y permisos

| Actor | Qué puede hacer |
|---|---|
| Área de negocio / operaciones | Dar de alta usuarios, consultar, actualizar datos permitidos, cambiar estado |
| Soporte del banco | Consultar estado e historial resumido del usuario (sin datos de verificación del canal) |
| Otros servicios del banco | Consultar existencia y datos básicos del cliente (según política de acceso) |
| Cliente final por WhatsApp | No interactúa directamente con este servicio; lo hace a través del canal (H3) |

## Requisitos (cada uno con escenarios Dado / Cuando / Entonces)

### R1 — Alta de usuario bancario

Escenario 1 (éxito):
- Dado que se reciben los datos mínimos obligatorios de un nuevo cliente y no existe duplicado por regla de unicidad.
- Cuando se valida unicidad y consistencia y el resultado es OK.
- Entonces el usuario queda en estado activo con un identificador asignado.

Escenario 2 (duplicado):
- Dado que se intenta dar de alta un cliente con un documento ya registrado.
- Cuando se valida unicidad y se detecta duplicado.
- Entonces se rechaza el alta y se informa el motivo (duplicado).

Escenario 3 (datos incompletos):
- Dado que los datos mínimos definidos por Compliance están incompletos.
- Cuando se intenta dar de alta el usuario.
- Entonces no se completa el alta y se informa el motivo (datos incompletos).

### R2 — Consulta de usuario

Escenario:
- Dado un identificador acordado de un usuario existente (documento o ID interno).
- Cuando se consulta el usuario por ese identificador.
- Entonces se devuelven solo los datos autorizados y el estado actual del usuario.

### R3 — Actualización de datos permitidos

Escenario:
- Dado un usuario identificado y cambios solo en campos permitidos (nombre para tratamiento, contacto, preferencias generales no sensibles).
- Cuando se aplican los cambios.
- Entonces se confirma el guardado y se audita la modificación.

### R4 — Cambio de estado operativo

Escenario:
- Dado un usuario identificado y un nuevo estado (suspendido, reactivado o baja lógica).
- Cuando se valida que el cambio cumple la política de estados del banco.
- Entonces se registra el cambio y se notifica a servicios dependientes si aplica.

### R5 — Validación de unicidad

Escenario:
- Dado un documento de identidad.
- Cuando se intenta registrar un usuario con ese documento ya existente.
- Entonces se rechaza el alta (no se permiten duplicados según la regla de unicidad del banco).

### R6 — Auditoría de altas y cambios de estado

Escenario:
- Dado que se ejecuta una alta de usuario o un cambio de estado.
- Cuando la operación finaliza.
- Entonces queda registrado con fecha, acción y resultado.

## Reglas de negocio (de la fuente)

1. Todo cliente que operará por WhatsApp debe existir en este servicio antes de vincular el canal.
2. No se permiten duplicados según la regla de unicidad del banco (ej. mismo documento).
3. Un usuario suspendido o en baja lógica no puede iniciar nuevos vínculos de canal hasta regularizar su estado.
4. Los datos consultables por otros servicios son solo los autorizados por política de privacidad.
5. Toda alta y cambio de estado queda registrado con fecha, acción y resultado.
6. El alta exige los datos mínimos definidos por Compliance; no se completa con información incompleta.

## Requisitos no funcionales

- Trazabilidad: cada operación sensible queda trazada con `correlationId` a lo largo de la cadena agente → capa MCP → servicio.
- Privacidad: cada usuario accede solo a lo suyo; lo decide el contexto de sesión, no el modelo.
- Auditoría: altas, cambios de estado y modificaciones relevantes quedan registradas.
- Mensajes al usuario claros, empáticos y sin jerga; nunca stack traces ni detalles técnicos.

## Supuestos

- El banco define los datos mínimos obligatorios de alta (Compliance).
- Existe una política de estados de usuario (activo, suspendido, baja lógica).
- Otros sistemas pueden sincronizarse con este registro de clientes.

## Preguntas abiertas

- ¿Cuáles son exactamente los datos mínimos obligatorios de alta definidos por Compliance? (material: define el DTO de entrada y las validaciones de alta).
- ¿Cuál es el identificador acordado para la consulta de usuario (documento, ID interno, etc.)? (material: define el parámetro del endpoint de consulta).
- ¿Qué campos exactos considera el banco como "permitidos" para actualización (nombre para tratamiento, contacto, preferencias generales no sensibles)? (material: define los campos actualizables).
- ¿Existe ya un contrato OpenAPI o definición de endpoint para este servicio en el repositorio que deba respetarse? (material: afecta al contrato publicado).
