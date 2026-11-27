---
title: Usuarios — especificación
status: borrador
source: https://app.notion.com/p/36875bb08ca78197a853e85505bf49a5
updated: 2026-11-27T20:56:55.334Z
---

# Usuarios — especificación

## Propósito

El Servicio de Usuarios (`wa-users-service`) es el registro único y confiable de los clientes del banco. Permite dar de alta un nuevo cliente, consultarlo, actualizar los datos que el banco autoriza y gestionar su estado operativo (activo, suspendido, baja lógica). Ningún otro servicio puede vincular un canal o emitir una credencial sin que el cliente exista aquí primero.

**Componente:** `services/wa-users-service`  
**Grupo Maven:** `com.bcra`  
**Paquete base:** `com.bcra.users.ws`  
**Contrato que entrega:** `shared/contracts/wa-users-service/openapi.yaml` y `shared/contracts/wa-users-service/handoff.md`  
**Dependencias de entrada:** ninguna (primer servicio de dominio).  
**Dependencias de salida:** H3 (Canal WhatsApp), H2 (Acceso y Sesión), H4–H6 (Productos) consultan la identidad registrada aquí.

---

## Requisitos

### Requisito: Alta de usuario bancario

El sistema DEBE permitir dar de alta un nuevo cliente del banco con los datos mínimos obligatorios definidos por el banco (parámetro configurable). Al completarse, el usuario queda en estado **activo** y se le asigna un identificador interno.

#### Escenario: Alta exitosa con datos mínimos válidos
- **DADO** que el cliente no existe en el sistema y se reciben todos los campos mínimos obligatorios válidos
- **CUANDO** se solicita el alta
- **ENTONCES** el sistema crea al cliente, le asigna un identificador interno único y lo deja en estado `activo`
- **Y** se registra una entrada de auditoría con fecha, acción (`ALTA`) y resultado (`OK`)

#### Escenario: Alta rechazada por campos obligatorios incompletos
- **DADO** que faltan uno o más campos mínimos obligatorios
- **CUANDO** se solicita el alta
- **ENTONCES** el sistema rechaza la solicitud informando qué campo o campos faltan o son inválidos
- **Y** no se crea ningún registro ni entrada de auditoría de cliente

#### Escenario: Alta rechazada por duplicado
- **DADO** que ya existe un cliente con el mismo valor de identificador de unicidad del banco (p. ej. mismo documento; regla de unicidad configurable)
- **CUANDO** se solicita el alta
- **ENTONCES** el sistema rechaza la solicitud con el motivo `DUPLICADO` indicando el campo que viola la unicidad
- **Y** no se crea ningún registro ni entrada de auditoría de cliente

---

### Requisito: Consulta de usuario por identificador

El sistema DEBE permitir consultar un cliente existente a partir del identificador acordado (identificador interno o identificador de negocio del banco). La respuesta incluye únicamente los datos autorizados por la política de privacidad del banco (configurable).

#### Escenario: Consulta exitosa de cliente activo
- **DADO** que el cliente existe y está en estado `activo`
- **CUANDO** se consulta por su identificador
- **ENTONCES** el sistema devuelve sus datos autorizados y su estado actual

#### Escenario: Consulta exitosa de cliente suspendido o dado de baja
- **DADO** que el cliente existe y está en estado `suspendido` o `baja_logica`
- **CUANDO** se consulta por su identificador
- **ENTONCES** el sistema devuelve sus datos autorizados y su estado actual (el estado informa que no está operativo)

#### Escenario: Consulta de cliente inexistente
- **DADO** que no existe ningún cliente con el identificador dado
- **CUANDO** se consulta
- **ENTONCES** el sistema responde que el cliente no fue encontrado

---

### Requisito: Actualización de datos permitidos

El sistema DEBE permitir actualizar los campos que el banco autoriza modificar en este servicio: nombre de tratamiento, datos de contacto no sensibles y preferencias generales no sensibles. No DEBE permitir cambiar datos legales sensibles (domicilio legal, estado civil, etc.) salvo política expresa del banco.

#### Escenario: Actualización exitosa de campo permitido
- **DADO** que el cliente existe y está en estado `activo`
- **CUANDO** se solicita la modificación de un campo permitido con un valor válido
- **ENTONCES** el sistema aplica el cambio, lo persiste y devuelve la confirmación
- **Y** se registra una entrada de auditoría con fecha, acción (`MODIFICACION`), campo modificado y resultado (`OK`)

#### Escenario: Actualización rechazada por campo no permitido
- **DADO** que el cliente existe
- **CUANDO** se intenta modificar un campo que no está en la lista de campos permitidos (p. ej. un dato legal sensible)
- **ENTONCES** el sistema rechaza la solicitud informando que ese campo no es modificable por este servicio

#### Escenario: Actualización rechazada de cliente inexistente
- **DADO** que no existe ningún cliente con el identificador dado
- **CUANDO** se solicita la actualización
- **ENTONCES** el sistema responde que el cliente no fue encontrado

---

### Requisito: Cambio de estado operativo

El sistema DEBE permitir cambiar el estado de un cliente entre `activo`, `suspendido` y `baja_logica`, siempre que el cambio cumpla la política de estados del banco. Todo cambio de estado queda auditado.

#### Escenario: Suspensión de cliente activo
- **DADO** que el cliente está en estado `activo`
- **CUANDO** se solicita cambiarlo a `suspendido`
- **ENTONCES** el sistema aplica el nuevo estado y registra la auditoría con fecha, acción (`CAMBIO_ESTADO`), estado anterior, estado nuevo y resultado (`OK`)

#### Escenario: Reactivación de cliente suspendido
- **DADO** que el cliente está en estado `suspendido`
- **CUANDO** se solicita cambiarlo a `activo`
- **ENTONCES** el sistema aplica el nuevo estado y registra la auditoría correspondiente

#### Escenario: Baja lógica de cliente
- **DADO** que el cliente está en estado `activo` o `suspendido`
- **CUANDO** se solicita cambiarlo a `baja_logica`
- **ENTONCES** el sistema aplica el nuevo estado y registra la auditoría correspondiente

#### Escenario: Cambio de estado inválido
- **DADO** que el cliente está en algún estado
- **CUANDO** se solicita una transición de estado que viola la política del banco (p. ej. reactivar desde `baja_logica` si la política no lo permite)
- **ENTONCES** el sistema rechaza el cambio informando el motivo

---

### Requisito: Restricción de uso para clientes no activos

El sistema NO DEBE permitir que un cliente en estado `suspendido` o `baja_logica` sea utilizado como base para un nuevo vínculo de canal (H3). Este servicio lo hace observable a través del estado devuelto en la consulta.

#### Escenario: Consulta de elegibilidad de cliente suspendido
- **DADO** que el cliente está en estado `suspendido` o `baja_logica`
- **CUANDO** otro servicio consulta su existencia y estado
- **ENTONCES** el sistema devuelve el estado actual, que indica que el cliente no está en condición de iniciar vínculos nuevos

---

### Requisito: Auditoría de operaciones relevantes

El sistema DEBE registrar una entrada de auditoría para cada alta, cambio de estado y modificación de datos relevantes. El registro incluye al menos: identificador del cliente, fecha y hora, tipo de acción, resultado y el actor que realizó la operación.

#### Escenario: Registro de auditoría completo tras alta
- **DADO** que se completa un alta exitosa
- **CUANDO** se consulta el historial de auditoría del cliente
- **ENTONCES** existe al menos una entrada con acción `ALTA`, resultado `OK` y timestamp de la operación

#### Escenario: Registro de auditoría completo tras cambio de estado
- **DADO** que se realiza un cambio de estado exitoso
- **CUANDO** se consulta el historial de auditoría del cliente
- **ENTONCES** existe una entrada con acción `CAMBIO_ESTADO`, los estados anterior y nuevo, resultado y timestamp

---

### Requisito: Validación de unicidad

El sistema DEBE garantizar que no existan dos clientes con el mismo valor en el campo o conjunto de campos que el banco define como clave de unicidad (configurable; valor por defecto: número de documento). La validación ocurre al momento del alta.

#### Escenario: Unicidad respetada
- **DADO** que el campo de unicidad del cliente nuevo no coincide con ningún cliente existente
- **CUANDO** se da de alta
- **ENTONCES** el alta se completa sin error de unicidad

#### Escenario: Unicidad violada
- **DADO** que ya existe un cliente con el mismo valor del campo de unicidad
- **CUANDO** se intenta dar de alta uno nuevo con ese mismo valor
- **ENTONCES** el alta se rechaza con motivo `DUPLICADO` y referencia al campo

---

## Definiciones configurables del banco

Las siguientes definiciones son parámetros del banco que **no** tienen valor fijo en la especificación. Cada una se implementa como parámetro configurable; su valor concreto lo define el banco en cada despliegue.

| Definición | Descripción |
|---|---|
| Datos mínimos del alta | Campos obligatorios para completar el alta de un cliente |
| Regla de unicidad | Campo o conjunto de campos que identifica de forma única a un cliente (p. ej. número de documento) |
| Campos permitidos para actualización | Lista de campos que se pueden modificar desde este servicio |
| Política de privacidad (datos consultables) | Qué campos puede devolver la consulta a otros servicios |
| Transiciones de estado permitidas | Qué cambios de estado son válidos y desde qué estado previo |

---

## Fuera de alcance

- Vincular número de WhatsApp con un cliente (H3 — Canal WhatsApp).
- Verificación de identidad por el canal (H3).
- Emisión o revocación de credencial de sesión (H2 — Acceso y Sesión).
- Consulta u operación de productos: cuentas, tarjetas, seguros (H4, H5, H6).
- Cambio de datos legales sensibles salvo política expresa del banco.
- Onboarding comercial completo de productos.
- Interacción directa con el cliente final por WhatsApp (el cliente interactúa vía H3).

---

## Preguntas abiertas

- **[CONFIGURABLE-1]** ¿Cuáles son los campos mínimos del alta que el banco define? La implementación los recibe como parámetro; su valor concreto no está en la especificación. Decisión registrada: se implementa configurable y no se bloquea la construcción.
- **[CONFIGURABLE-2]** ¿Cuál es el campo o conjunto de campos que define la unicidad (p. ej. número de documento, CUIL/CUIT, o combinación)? Mismo tratamiento que CONFIGURABLE-1.
- **[CONFIGURABLE-3]** ¿Qué transiciones de estado están permitidas (p. ej. ¿se puede reactivar desde `baja_logica`?)? Mismo tratamiento.
- **[CONFIGURABLE-4]** ¿Qué campos son consultables por otros servicios según la política de privacidad? Mismo tratamiento.
