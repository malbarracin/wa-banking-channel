---
title: Usuarios — especificación
status: borrador
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-10-18T19:55:42.809Z
---

# Usuarios — especificación

## Propósito
Administrar personas clientes del banco: alta, consulta, actualización de datos permitidos y estados operativos (activo, suspendido, baja lógica). Es la base de identidad que otros servicios consultan; **no** gestiona WhatsApp, vínculos de canal ni credenciales de sesión.

**Decisión resuelta (persona):** datos mínimos de alta = tipo de documento + número de documento + nombre para tratamiento + contacto (email y/o teléfono) + fecha de nacimiento; unicidad = tipo + número de documento.

**Fuera de alcance (H1):** vincular WhatsApp (H3); verificación de identidad por canal; emisión/revocación de sesión (H2); productos (cuentas/tarjetas/seguros); cambio de datos legales sensibles; onboarding comercial completo de productos.

## Assumptions
- Contacto: al menos uno entre email y teléfono es obligatorio (la decisión dice «email y/o teléfono»).
- Identificadores de consulta acordados: ID interno asignado en el alta **o** tipo + número de documento.
- Datos autorizados en consulta (otros servicios / soporte): ID interno, tipo y número de documento, nombre para tratamiento, contacto, fecha de nacimiento y estado operativo.
- Reactivación: transición suspendido → activo permitida. Baja lógica → activo queda en preguntas abiertas.
- Preferencias generales no sensibles: se pueden actualizar si se envían; el catálogo concreto lo define el banco (pregunta abierta).
- Notificación a servicios dependientes ante cambio de estado: no es requisito verificable de este servicio en H1 (H1 dice «si aplica»).

## Requisitos

### Requisito: Alta con datos mínimos
El sistema DEBE dar de alta un usuario bancario cuando se reciben todos los datos mínimos válidos, asignar un identificador interno y dejarlo en estado **activo**.

#### Escenario: Alta exitosa
- **DADO** que no existe un usuario con el mismo tipo y número de documento
- **CUANDO** se solicita el alta con tipo de documento, número de documento, nombre para tratamiento, al menos un contacto (email o teléfono) y fecha de nacimiento válidos
- **ENTONCES** el usuario queda registrado en estado activo
- **Y** se asigna un identificador interno único

#### Escenario: Alta incompleta
- **DADO** un pedido de alta al que le falta alguno de los datos mínimos
- **CUANDO** se intenta registrar
- **ENTONCES** el sistema NO DEBE crear el usuario
- **Y** DEBE informar que faltan datos obligatorios

#### Escenario: Dato inválido
- **DADO** un pedido de alta con algún dato mínimo en formato o valor inválido
- **CUANDO** se intenta registrar
- **ENTONCES** el sistema NO DEBE crear el usuario
- **Y** DEBE informar el motivo de dato inválido

### Requisito: Unicidad por documento
El sistema NO DEBE permitir un alta duplicada según la regla de unicidad del banco: mismo tipo y número de documento.

#### Escenario: Rechazo por duplicado
- **DADO** un usuario ya registrado con tipo y número de documento D
- **CUANDO** se solicita un alta con el mismo tipo y número de documento D
- **ENTONCES** el sistema NO DEBE crear un segundo usuario
- **Y** DEBE informar que el usuario ya existe (duplicado)

### Requisito: Consulta por identificador
El sistema DEBE permitir consultar un usuario por identificador interno o por tipo y número de documento, devolviendo solo los datos autorizados y el estado actual.

#### Escenario: Consulta encontrada
- **DADO** un usuario existente
- **CUANDO** se consulta por su ID interno o por su tipo y número de documento
- **ENTONCES** se devuelven los datos autorizados y el estado operativo actual
- **Y** NO se exponen datos legales sensibles ni datos de verificación de canal

#### Escenario: Consulta no encontrada
- **DADO** un identificador que no corresponde a ningún usuario
- **CUANDO** se consulta
- **ENTONCES** el sistema indica que el usuario no existe

### Requisito: Actualización de datos permitidos
El sistema DEBE permitir actualizar solo campos permitidos: nombre para tratamiento, contacto y preferencias generales no sensibles.

#### Escenario: Actualización permitida
- **DADO** un usuario existente
- **CUANDO** se actualizan nombre para tratamiento, contacto y/o preferencias generales no sensibles con valores válidos
- **ENTONCES** los cambios quedan guardados
- **Y** la modificación queda registrada en auditoría

#### Escenario: Intento de cambiar dato no permitido
- **DADO** un usuario existente
- **CUANDO** se intenta modificar un dato legal sensible (p. ej. domicilio legal, estado civil) u otro campo no autorizado
- **ENTONCES** el sistema NO DEBE aplicar ese cambio
- **Y** DEBE informar que el campo no es actualizable en este servicio

### Requisito: Cambio de estado operativo
El sistema DEBE permitir cambiar el estado del usuario entre **activo**, **suspendido** y **baja lógica** según política, y DEBE auditar cada cambio.

#### Escenario: Suspender usuario activo
- **DADO** un usuario en estado activo
- **CUANDO** se solicita suspenderlo
- **ENTONCES** el estado pasa a suspendido
- **Y** queda registrado con fecha, acción y resultado

#### Escenario: Reactivar usuario suspendido
- **DADO** un usuario en estado suspendido
- **CUANDO** se solicita reactivarlo
- **ENTONCES** el estado pasa a activo
- **Y** queda registrado en auditoría

#### Escenario: Baja lógica
- **DADO** un usuario existente (activo o suspendido)
- **CUANDO** se solicita baja lógica
- **ENTONCES** el estado pasa a baja lógica
- **Y** queda registrado en auditoría

### Requisito: Aptitud para vínculo de canal
El sistema DEBE exponer el estado de forma que otros servicios puedan determinar que un usuario **suspendido** o en **baja lógica** no puede usarse para un nuevo vínculo de canal hasta regularizar su estado. Solo un usuario **activo** es apto para nuevo vínculo.

#### Escenario: Usuario activo apto
- **DADO** un usuario en estado activo
- **CUANDO** otro servicio consulta existencia y estado
- **ENTONCES** el resultado indica que el cliente existe y está activo (apto para vínculo)

#### Escenario: Usuario no apto
- **DADO** un usuario en estado suspendido o baja lógica
- **CUANDO** otro servicio consulta existencia y estado
- **ENTONCES** el resultado indica el estado no apto
- **Y** NO se interpreta como apto para nuevo vínculo de canal

### Requisito: Auditoría de altas y cambios relevantes
El sistema DEBE registrar toda alta, todo cambio de estado y toda modificación relevante de datos permitidos, con fecha, acción y resultado.

#### Escenario: Alta auditada
- **DADO** un alta exitosa
- **CUANDO** se completa el registro
- **ENTONCES** existe un registro de auditoría con fecha, acción de alta y resultado exitoso

#### Escenario: Cambio de estado auditado
- **DADO** un cambio de estado aplicado
- **CUANDO** se confirma el nuevo estado
- **ENTONCES** existe un registro de auditoría con fecha, acción, estado resultante y resultado

#### Escenario: Actualización relevante auditada
- **DADO** una actualización de datos permitidos aplicada
- **CUANDO** se confirma el guardado
- **ENTONCES** existe un registro de auditoría de la modificación

## Preguntas abiertas
- Catálogo exacto de tipos de documento aceptados (DNI, pasaporte, etc.).
- ¿El contacto exige email y teléfono a la vez, o basta con uno de los dos? (asumido: al menos uno).
- ¿Se permite reactivar un usuario desde **baja lógica** hacia activo, o la baja es terminal?
- Catálogo concreto de «preferencias generales no sensibles» permitidas en actualización.
- Lista definitiva de campos consultables por política de privacidad (si difiere del set asumido).
