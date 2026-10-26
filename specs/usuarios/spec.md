---
title: Usuarios — especificación
status: borrador
source: https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5
updated: 2026-10-26T12:23:02.093Z
---

# Usuarios — especificación

## Propósito

El Servicio de Usuarios administra a las personas clientes del banco: alta, consulta, actualización de datos
permitidos y estados operativos. Es la base de identidad sobre la que se apoyan el resto de los servicios del
canal (incluido WhatsApp, H3) y no gestiona el canal, la verificación de identidad ni las credenciales de sesión.

## Fuera de alcance (de la fuente)

- Vincular número de WhatsApp (H3).
- Verificación de identidad por el canal (H3).
- Emisión o revocación de credencial de sesión (H2).
- Consulta u operación de productos: cuentas, tarjetas, seguros (H4, H5, H6).
- Cambio de datos legales sensibles (domicilio legal, estado civil, etc.) salvo política expresa del banco.
- Onboarding comercial completo de productos.

## Decisión registrada (campos mínimos e identificador único)

La fuente dejaba como supuesto abierto "el banco define datos mínimos de alta" y daba "documento" solo como
ejemplo no cerrado. Se consultó a la persona que dio la orden y se resolvió:

- **Campos mínimos obligatorios de alta**: documento (tipo + número), nombre, apellido, fecha de nacimiento y al
  menos un dato de contacto (email o teléfono).
- **Identificador único** (regla de unicidad y clave de consulta, U1/U2): el documento (tipo + número).

## Requisitos

### Requisito: Alta de usuario bancario
El sistema DEBE permitir dar de alta un usuario bancario con los datos mínimos obligatorios (documento con tipo
y número, nombre, apellido, fecha de nacimiento y al menos un dato de contacto —email o teléfono—), dejándolo en
estado activo con un identificador asignado cuando los datos son válidos y únicos.

#### Escenario: Alta exitosa con datos mínimos completos
- **DADO** que no existe ningún usuario registrado con el mismo documento
- **CUANDO** se solicita el alta con documento, nombre, apellido, fecha de nacimiento y al menos un dato de contacto válidos
- **ENTONCES** el usuario queda creado en estado activo, con un identificador asignado y el documento registrado como clave de unicidad
- **Y** la operación queda auditada con fecha, acción y resultado

#### Escenario: Alta rechazada por datos mínimos incompletos
- **DADO** que falta alguno de los datos mínimos obligatorios (documento, nombre, apellido, fecha de nacimiento, o ningún dato de contacto presente)
- **CUANDO** se solicita el alta
- **ENTONCES** el sistema rechaza el alta
- **Y** informa el motivo del rechazo (dato faltante o inválido)
- **Y** no se crea ningún registro de usuario

### Requisito: Unicidad por documento
El sistema NO DEBE permitir dar de alta dos usuarios con el mismo documento (tipo + número), sin importar el
estado del usuario existente.

#### Escenario: Alta rechazada por documento duplicado
- **DADO** que ya existe un usuario registrado (en cualquier estado) con un documento (tipo + número) determinado
- **CUANDO** se solicita el alta de otro usuario con el mismo documento
- **ENTONCES** el sistema rechaza el alta
- **Y** informa que el motivo es duplicado

### Requisito: Consulta de usuario por identificador
El sistema DEBE permitir consultar un usuario por su documento (identificador único) y devolver los datos
autorizados para el perfil que consulta junto con el estado actual del usuario.

#### Escenario: Consulta de un usuario existente
- **DADO** un usuario existente identificado por su documento
- **CUANDO** se lo consulta por ese documento
- **ENTONCES** el sistema devuelve los datos autorizados para el perfil que consulta y el estado actual del usuario

#### Escenario: Consulta de un usuario inexistente
- **DADO** que no existe ningún usuario registrado con el documento indicado
- **CUANDO** se lo consulta
- **ENTONCES** el sistema informa que no fue encontrado
- **Y** no expone datos de ningún otro usuario

### Requisito: Actualización de datos permitidos
El sistema DEBE permitir actualizar los datos permitidos del usuario (nombre para tratamiento, datos de
contacto, preferencias generales no sensibles) sin permitir modificar el documento, la fecha de nacimiento ni
otros datos legales sensibles.

#### Escenario: Actualización exitosa de datos de contacto
- **DADO** un usuario existente identificado
- **CUANDO** se solicita actualizar su email, su teléfono o su nombre para tratamiento
- **ENTONCES** el sistema guarda el cambio y confirma el guardado
- **Y** la modificación queda auditada con fecha, acción y resultado

#### Escenario: Intento de modificar un dato no permitido
- **DADO** un usuario existente
- **CUANDO** se solicita modificar el documento, la fecha de nacimiento u otro dato legal sensible
- **ENTONCES** el sistema rechaza el cambio para ese campo
- **Y** informa que el campo no es editable por este servicio

### Requisito: Cambio de estado del usuario
El sistema DEBE permitir cambiar el estado del usuario entre activo, suspendido y baja lógica, validando que el
cambio cumple la política del banco y dejando registro auditable, y debe notificar a los servicios dependientes
cuando aplique.

#### Escenario: Suspensión de un usuario activo
- **DADO** un usuario en estado activo
- **CUANDO** se solicita su suspensión
- **ENTONCES** el usuario queda en estado suspendido
- **Y** la acción queda auditada con fecha, acción y resultado

#### Escenario: Reactivación de un usuario suspendido
- **DADO** un usuario en estado suspendido
- **CUANDO** se solicita su reactivación
- **ENTONCES** el usuario vuelve a estado activo
- **Y** la acción queda auditada

#### Escenario: Baja lógica de un usuario
- **DADO** un usuario existente en estado activo o suspendido
- **CUANDO** se solicita su baja lógica
- **ENTONCES** el usuario queda en estado "baja lógica"
- **Y** la acción queda auditada

### Requisito: Restricción de vínculo de canal para usuarios no activos
El sistema NO DEBE habilitar que un usuario en estado suspendido o en baja lógica se use para iniciar un nuevo
vínculo de canal (por ejemplo, WhatsApp) hasta que su estado vuelva a activo. Este servicio es responsable de
exponer el estado del usuario para que los servicios de canal (H3) puedan aplicar esta regla.

#### Escenario: Estado no habilitante para nuevo vínculo de canal
- **DADO** un usuario en estado suspendido o en baja lógica
- **CUANDO** otro servicio consulta su estado para habilitar un nuevo vínculo de canal
- **ENTONCES** el sistema informa el estado no habilitante del usuario
- **Y** el usuario no puede usarse para ese nuevo vínculo hasta volver a estado activo

### Requisito: Auditoría de operaciones del usuario
El sistema DEBE registrar con fecha, acción y resultado toda alta, cambio de estado y modificación relevante de
datos de un usuario, tanto si la operación fue exitosa como si fue rechazada.

#### Escenario: Registro de auditoría tras cada operación
- **DADO** que se ejecuta un alta, un cambio de estado o una actualización de datos permitidos sobre un usuario
- **CUANDO** la operación finaliza, exitosa o rechazada
- **ENTONCES** queda un registro de auditoría con fecha, acción y resultado, asociado al usuario correspondiente

## Preguntas abiertas

- Qué campos exactos devuelve la consulta (U2) para cada tipo de consultante (área de negocio/operaciones,
  soporte, otros servicios del banco): la fuente delega esto a "la política de privacidad" del banco, todavía no
  definida. No bloquea el resto del hito, pero el arquitecto/TL deben dejarlo parametrizable hasta que Compliance
  la defina.
- Reglas exactas de habilitación de transición entre estados: qué actor puede ejecutar cada transición
  (activo ↔ suspendido ↔ baja lógica) y si la baja lógica admite reactivación — la fuente dice "según política
  del banco" sin cerrarla.
- Validaciones de formato específicas: tipos de documento aceptados por el banco y formato válido de email/
  teléfono — no están definidos en la fuente.
