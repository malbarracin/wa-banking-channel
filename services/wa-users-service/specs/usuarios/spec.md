---
title: Usuarios — especificación
status: aprobada
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-10-16T17:42:11.385Z
---

# Usuarios — especificación

## Propósito

Administrar a las personas clientes del banco: alta, consulta, actualización de datos permitidos y gestión de estados operativos (activo, suspendido, baja lógica). Es el registro confiable y único de clientes sobre el cual otros servicios identifican al cliente.

Este servicio **no** gestiona WhatsApp, **no** vincula números de teléfono y **no** entrega credenciales de sesión del canal.

Actores: área de negocio / operaciones (alta, consulta, actualización, cambio de estado); soporte del banco (consulta de estado e historial resumido); otros servicios del banco (consulta de existencia y datos básicos según política). El cliente final por WhatsApp **no** interactúa directamente con este servicio.

## Requisitos

### Requisito: Alta de usuario bancario
El sistema DEBE permitir dar de alta un usuario bancario cuando se reciben los datos mínimos obligatorios definidos por Compliance, validar unicidad y consistencia, y dejar al usuario en estado **activo** con un identificador asignado.

#### Escenario: Alta exitosa con datos mínimos válidos
- **DADO** que se reciben datos mínimos válidos de un cliente nuevo que no viola la regla de unicidad
- **CUANDO** se solicita el alta
- **ENTONCES** el usuario queda creado en estado activo
- **Y** se le asigna un identificador
- **Y** queda registrado el alta con fecha, acción y resultado

#### Escenario: Alta rechazada por datos incompletos
- **DADO** que faltan uno o más datos mínimos obligatorios definidos por Compliance
- **CUANDO** se solicita el alta
- **ENTONCES** el alta NO se completa
- **Y** se informa el motivo (dato inválido o incompleto)

#### Escenario: Alta rechazada por duplicado
- **DADO** que ya existe un usuario que viola la regla de unicidad del banco
- **CUANDO** se solicita el alta con esos mismos datos de unicidad
- **ENTONCES** el alta se rechaza
- **Y** se informa el motivo de duplicado

### Requisito: Consulta de usuario
El sistema DEBE permitir consultar un usuario por el identificador acordado y devolver solo los datos autorizados por política de privacidad junto con el estado actual.

#### Escenario: Consulta exitosa
- **DADO** que existe un usuario con el identificador acordado
- **CUANDO** se consulta por ese identificador
- **ENTONCES** se devuelven los datos permitidos y el estado actual
- **Y** NO se exponen datos no autorizados por la política de privacidad

#### Escenario: Consulta por soporte
- **DADO** que el actor es soporte del banco y existe el usuario
- **CUANDO** consulta el usuario
- **ENTONCES** puede ver estado e historial resumido
- **Y** NO ve datos de verificación del canal (fuera de alcance de este servicio)

#### Escenario: Consulta por otros servicios
- **DADO** que otro servicio del banco consulta según política de acceso
- **CUANDO** solicita existencia y datos básicos del cliente
- **ENTONCES** recibe solo los datos autorizados por política de privacidad

### Requisito: Actualización de datos permitidos
El sistema DEBE permitir actualizar únicamente los campos que el banco autoriza en este servicio (p. ej. nombre para tratamiento, contacto, preferencias generales no sensibles) y auditar la modificación.

#### Escenario: Actualización exitosa de campos permitidos
- **DADO** que existe un usuario identificable
- **CUANDO** se solicitan cambios en campos permitidos
- **ENTONCES** se confirman y guardan los cambios
- **Y** queda auditada la modificación relevante

#### Escenario: Intento de cambiar datos legales sensibles
- **DADO** que se intenta modificar datos legales sensibles (p. ej. domicilio legal, estado civil) sin política expresa del banco que lo autorice en este servicio
- **CUANDO** se solicita la actualización
- **ENTONCES** el sistema NO DEBE aplicar ese cambio como parte del alcance de este servicio

### Requisito: Cambio de estado operativo
El sistema DEBE permitir cambiar el estado del usuario entre los estados operativos **activo**, **suspendido** y **baja lógica**, validando que el cambio cumple la política del banco, y registrar el cambio con fecha, acción y resultado.

#### Escenario: Suspensión de usuario activo
- **DADO** un usuario en estado activo
- **CUANDO** se solicita el cambio a suspendido y el cambio cumple política
- **ENTONCES** el usuario queda en estado suspendido
- **Y** queda registrado el cambio de estado con fecha, acción y resultado

#### Escenario: Reactivación
- **DADO** un usuario en estado suspendido (u otro estado del cual la política permita reactivar)
- **CUANDO** se solicita la reactivación y cumple política
- **ENTONCES** el usuario queda en estado activo
- **Y** queda auditado el cambio

#### Escenario: Baja lógica
- **DADO** un usuario existente
- **CUANDO** se solicita baja lógica y el cambio cumple política
- **ENTONCES** el usuario queda en estado baja lógica
- **Y** queda auditado el cambio

#### Escenario: Cambio de estado no permitido por política
- **DADO** un usuario y un nuevo estado solicitado
- **CUANDO** el cambio no cumple la política de estados del banco
- **ENTONCES** el sistema NO aplica el cambio
- **Y** se informa el motivo

### Requisito: Restricción de vínculo de canal según estado
El sistema DEBE exponer o garantizar que un usuario en estado **suspendido** o **baja lógica** no pueda usarse para iniciar nuevos vínculos de canal hasta regularizar su estado. Todo cliente que operará por WhatsApp DEBE existir en este servicio antes de vincular el canal (dependencia hacia H3).

#### Escenario: Usuario suspendido no apto para nuevo vínculo
- **DADO** un usuario en estado suspendido
- **CUANDO** se evalúa si puede usarse para un nuevo vínculo de canal
- **ENTONCES** el resultado indica que no es apto hasta regularizar el estado

#### Escenario: Usuario en baja lógica no apto para nuevo vínculo
- **DADO** un usuario en estado baja lógica
- **CUANDO** se evalúa si puede usarse para un nuevo vínculo de canal
- **ENTONCES** el resultado indica que no es apto hasta regularizar el estado

#### Escenario: Usuario activo existente como prerrequisito de canal
- **DADO** que se pretende vincular un canal (H3) a un cliente
- **CUANDO** el cliente no existe en este servicio
- **ENTONCES** no se cumple el prerrequisito de existencia en el Servicio de Usuarios

### Requisito: Auditoría de altas, estados y modificaciones relevantes
El sistema DEBE registrar toda alta, todo cambio de estado y las modificaciones relevantes, con fecha, acción y resultado.

#### Escenario: Alta auditada
- **DADO** un alta exitosa o fallida con resultado informado
- **CUANDO** finaliza la operación de alta
- **ENTONCES** queda un registro con fecha, acción y resultado

#### Escenario: Cambio de estado auditado
- **DADO** un cambio de estado aplicado
- **CUANDO** finaliza la operación
- **ENTONCES** queda un registro con fecha, acción y resultado

#### Escenario: Modificación relevante auditada
- **DADO** una actualización de datos permitidos aplicada
- **CUANDO** finaliza la operación
- **ENTONCES** queda auditada la modificación relevante

### Requisito: Fuera de alcance explícito
El sistema NO DEBE, en el alcance de este hito, vincular números de WhatsApp; verificar identidad por el canal; emitir o revocar credenciales de sesión; consultar u operar productos (cuentas, tarjetas, seguros); ni realizar onboarding comercial completo de productos.

#### Escenario: Operaciones de canal y sesión fuera de alcance
- **DADO** el Servicio de Usuarios en el alcance H1
- **CUANDO** se evalúa el alcance funcional
- **ENTONCES** vinculación WhatsApp, verificación de identidad por canal y credenciales de sesión quedan fuera de este servicio
- **Y** productos (cuentas, tarjetas, seguros) y onboarding comercial completo quedan fuera de este servicio

## Preguntas abiertas

- ¿Cuáles son exactamente los **datos mínimos obligatorios** de alta definidos por Compliance? (la fuente los exige pero no los enumera)
- ¿Cuál es la **regla de unicidad** definitiva del banco? (la fuente cita como ejemplo el mismo documento)
- ¿Qué **identificador(es) acordados** se usan para consulta: documento, ID interno, ambos u otros?
- ¿Lista cerrada de **campos actualizables** (además de los ejemplos: nombre para tratamiento, contacto, preferencias generales no sensibles)?
- ¿Conjunto exacto de **datos autorizados** en consulta por actor (operaciones, soporte, otros servicios) según política de privacidad?
- ¿Cuáles son las **transiciones de estado permitidas** por política (p. ej. baja lógica → activo)?
- En el flujo U4, ¿la **notificación a servicios dependientes** está en alcance de H1 o se difiere?
- Propiedad del hito en Notion: `Requiere credencial JWT = NO` — ¿implica API sin autenticación en el piloto, o solo que el hito no consume JWT de sesión del canal?
