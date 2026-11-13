---
title: Usuarios — especificación
status: borrador
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-11-13T20:45:35.298Z
---

# Usuarios — especificación

## Propósito

Administrar a las personas clientes del banco: dar de alta un nuevo cliente, consultarlo, actualizar los datos que el banco autoriza en este servicio y gestionar su estado operativo (activo, suspendido, baja lógica). Es la base de identidad sobre la cual otros servicios identifican al cliente antes de operar por cualquier canal (WhatsApp, app, sucursal).

Este servicio NO gestiona WhatsApp, NO vincula números de teléfono y NO emite ni revoca credenciales de sesión del canal.

## Actores

| Actor | Qué puede hacer |
|------|-----------------|
| Área de negocio / operaciones | Dar de alta usuarios, consultar, actualizar datos permitidos, cambiar estado |
| Soporte del banco | Consultar estado e historial resumido del usuario (sin datos de verificación del canal) |
| Otros servicios del banco | Consultar existencia y datos básicos del cliente (según política de acceso) |
| Cliente final por WhatsApp | No interactúa directamente con este servicio; lo hace a través del canal (H3) |

## Fuera de alcance

- Vincular número de WhatsApp (H3 — Canal WhatsApp).
- Verificación de identidad por el canal (H3).
- Emisión o revocación de credencial de sesión (H2 — Acceso y Sesión).
- Consulta u operación de productos: cuentas, tarjetas, seguros (H4, H5, H6).
- Cambio de datos legales sensibles (domicilio legal, estado civil, etc.) salvo política expresa del banco.
- Onboarding comercial completo de productos.

## Decisiones resueltas (de la persona)

- **Datos mínimos obligatorios del alta** (conjunto completo de Compliance): tipo de documento, número de documento, nombre, apellido, email de contacto, teléfono de contacto, dirección y fecha de nacimiento. El alta no se completa con información incompleta.
- **Regla de unicidad**: el par tipo + número de documento DEBE ser único y el email DEBE ser único, cada uno de forma independiente. Un alta con documento o email ya existente se rechaza como duplicado.
- **Estados operativos**: activo, suspendido, baja lógica.
- **Transiciones de estado permitidas** (de los flujos U4): activo → suspendido; suspendido → activo (reactivación); activo o suspendido → baja lógica.

## Supuestos

- Los identificadores de consulta acordados son el identificador interno asignado al alta y el par tipo + número de documento. La fuente menciona "identificador acordado (documento, ID interno, etc.)"; el arquitecto puede ampliar el conjunto.
- La actualización del email de contacto está permitida como dato de contacto y debe respetar la regla de unicidad.

## Requisitos

### Requisito: Alta de usuario bancario
El sistema DEBE permitir dar de alta un nuevo cliente recibiendo los datos mínimos obligatorios y, si pasan la validación de unicidad y consistencia, crearlo en estado activo con un identificador asignado.

#### Escenario: alta exitosa con datos mínimos válidos
- **DADO** que no existe un cliente con el mismo documento ni el mismo email
- **CUANDO** se reciben los datos mínimos obligatorios válidos (tipo y número de documento, nombre, apellido, email, teléfono, dirección, fecha de nacimiento)
- **ENTONCES** se crea el cliente en estado activo
- **Y** se asigna un identificador al cliente
- **Y** se registra el alta con fecha, acción y resultado

#### Escenario: alta rechazada por documento duplicado
- **DADO** que ya existe un cliente con el mismo tipo y número de documento
- **CUANDO** se reciben los datos del nuevo cliente
- **ENTONCES** no se crea el cliente
- **Y** se informa el motivo: duplicado por documento

#### Escenario: alta rechazada por email duplicado
- **DADO** que ya existe un cliente con el mismo email
- **CUANDO** se reciben los datos del nuevo cliente
- **ENTONCES** no se crea el cliente
- **Y** se informa el motivo: duplicado por email

#### Escenario: alta rechazada por dato inválido o incompleto
- **DADO** que falta alguno de los datos mínimos obligatorios o alguno es inválido
- **CUANDO** se reciben los datos del nuevo cliente
- **ENTONCES** no se crea el cliente
- **Y** se informa el motivo: dato inválido o información incompleta

### Requisito: Consulta de usuario
El sistema DEBE permitir consultar un cliente por un identificador acordado y devolver únicamente los datos autorizados para quien consulta, junto con su estado actual.

#### Escenario: consulta por identificador interno
- **DADO** que existe un cliente con el identificador interno asignado
- **CUANDO** se lo consulta por ese identificador
- **ENTONCES** se devuelven los datos autorizados para quien consulta y su estado actual

#### Escenario: consulta por documento
- **DADO** que existe un cliente con ese tipo y número de documento
- **CUANDO** se lo consulta por documento
- **ENTONCES** se devuelven los datos autorizados para quien consulta y su estado actual

#### Escenario: consulta de cliente inexistente
- **DADO** que no existe un cliente con el identificador indicado
- **CUANDO** se lo consulta
- **ENTONCES** se informa que no se encontró

#### Escenario: consulta por otro servicio del banco devuelve solo datos básicos
- **DADO** que otro servicio del banco consulta existencia y datos del cliente
- **CUANDO** realiza la consulta según la política de acceso
- **ENTONCES** se devuelven solo los datos básicos autorizados por la política de privacidad
- **Y** no se exponen datos de verificación del canal

### Requisito: Actualización de datos permitidos
El sistema DEBE permitir actualizar los campos autorizados de un cliente identificado y registrar la modificación.

#### Escenario: actualización de datos de tratamiento y contacto
- **DADO** que existe el cliente
- **CUANDO** se actualizan campos permitidos (nombre para tratamiento, datos de contacto, preferencias generales no sensibles)
- **ENTONCES** se guardan los cambios
- **Y** se confirma el guardado al solicitante
- **Y** se audita la modificación con fecha, acción y resultado

#### Escenario: actualización de email respeta unicidad
- **DADO** que se actualiza el email de contacto del cliente
- **CUANDO** el nuevo email ya pertenece a otro cliente
- **ENTONCES** no se guarda el cambio
- **Y** se informa el motivo: email duplicado

#### Escenario: actualización de dato sensible rechazada
- **DADO** que se intenta modificar un dato legal sensible (domicilio legal, estado civil, etc.)
- **CUANDO** no hay política expresa del banco que lo permita en este servicio
- **ENTONCES** no se guarda el cambio
- **Y** se informa que el campo no es actualizable en este servicio

### Requisito: Cambio de estado operativo
El sistema DEBE permitir cambiar el estado operativo del cliente entre activo, suspendido y baja lógica, validando que el cambio cumpla la política del banco, y registrarlo.

#### Escenario: suspensión de un cliente activo
- **DADO** que el cliente está activo
- **CUANDO** se solicita pasarlo a suspendido
- **ENTONCES** su estado pasa a suspendido
- **Y** se registra el cambio con fecha, acción y resultado
- **Y** se notifica a los servicios dependientes si aplica

#### Escenario: reactivación de un cliente suspendido
- **DADO** que el cliente está suspendido
- **CUANDO** se solicita reactivarlo
- **ENTONCES** su estado pasa a activo
- **Y** se registra el cambio con fecha, acción y resultado

#### Escenario: baja lógica de un cliente
- **DADO** que el cliente está activo o suspendido
- **CUANDO** se solicita la baja lógica
- **ENTONCES** su estado pasa a baja lógica
- **Y** se registra el cambio con fecha, acción y resultado
- **Y** se notifica a los servicios dependientes si aplica

#### Escenario: cambio de estado no permitido se rechaza
- **DADO** que se solicita un cambio que no cumple la política del banco (ej. reactivar un cliente en baja lógica sin regularización)
- **CUANDO** se procesa la solicitud
- **ENTONCES** no se cambia el estado
- **Y** se informa el motivo

### Requisito: Unicidad del cliente
El sistema DEBE garantizar que ningún cliente duplique el par tipo + número de documento ni el email, cada uno de forma independiente.

#### Escenario: dos clientes con mismo documento no coexisten
- **DADO** que existe un cliente con un tipo y número de documento
- **CUANDO** se intenta crear o dejar a otro cliente con ese mismo documento
- **ENTONCES** el sistema lo rechaza como duplicado

#### Escenario: dos clientes con mismo email no coexisten
- **DADO** que existe un cliente con un email
- **CUANDO** se intenta crear o dejar a otro cliente con ese mismo email
- **ENTONCES** el sistema lo rechaza como duplicado

### Requisito: Restricción de vínculo de canal según estado
El sistema DEBE reflejar el estado operativo de forma que un cliente suspendido o en baja lógica no pueda iniciar nuevos vínculos de canal hasta regularizar su estado.

#### Escenario: cliente suspendido no habilita nuevo vínculo de canal
- **DADO** que el cliente está suspendido
- **CUANDO** otro servicio (H3 — Canal WhatsApp) consulta si puede vincular un nuevo número
- **ENTONCES** el estado reportado impide iniciar el nuevo vínculo hasta regularizar

#### Escenario: cliente en baja lógica no habilita nuevo vínculo de canal
- **DADO** que el cliente está en baja lógica
- **CUANDO** otro servicio consulta si puede vincular un nuevo número
- **ENTONCES** el estado reportado impide iniciar el nuevo vínculo hasta regularizar

#### Escenario: cliente activo habilita nuevo vínculo de canal
- **DADO** que el cliente está activo
- **CUANDO** otro servicio consulta si puede vincular un nuevo número
- **ENTONCES** el estado reportado permite iniciar el vínculo

### Requisito: Auditoría de altas, cambios de estado y modificaciones
El sistema DEBE registrar toda alta, cambio de estado y modificación relevante con fecha, acción y resultado.

#### Escenario: alta queda registrada
- **DADO** que se da de alta un cliente
- **CUANDO** se completa el alta
- **ENTONCES** queda un registro con fecha, acción y resultado

#### Escenario: cambio de estado queda registrado
- **DADO** que se cambia el estado de un cliente
- **CUANDO** se completa el cambio
- **ENTONCES** queda un registro con fecha, acción y resultado

#### Escenario: modificación queda registrada
- **DADO** que se modifica un campo permitido de un cliente
- **CUANDO** se completa la modificación
- **ENTONCES** queda un registro con fecha, acción y resultado

### Requisito: Existencia previa del cliente para operar por canal
El sistema DEBE ser la base de identidad: todo cliente que operará por WhatsApp DEBE existir en este servicio antes de vincular el canal.

#### Escenario: el canal verifica existencia del cliente
- **DADO** que se intenta vincular un número de WhatsApp a un cliente
- **CUANDO** el canal (H3) verifica la existencia del cliente en este servicio
- **ENTONCES** solo se permite continuar si el cliente existe y está en estado que habilita el vínculo

## Riesgos y supuestos (de la fuente)

- **Riesgos**: datos inconsistentes con el core legacy; duplicados no detectados; baja de usuario con vínculos activos en canal.
- **Supuestos**: el banco define los datos mínimos de alta; existe una política de estados de usuario; otros sistemas pueden sincronizarse con este registro.

## Preguntas abiertas

- **Datos visibles por actor en la consulta**: la fuente indica que la consulta devuelve "solo datos autorizados" por política de privacidad, pero no enumera qué campos ve cada actor (área de negocio/operaciones, soporte del banco, otros servicios). El banco debe definir el conjunto de campos autorizados por actor antes del modelado del contrato de consulta.
