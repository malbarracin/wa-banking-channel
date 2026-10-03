# Usuarios Specification

## Purpose

El dominio `usuarios` administra las personas clientes del banco: su alta, consulta, actualización de datos permitidos y gestión de estado operativo (activo, suspendido, baja lógica). Es la base de identidad del cliente sobre la que operan todos los demás servicios del canal WhatsApp y del banco.

## Assumptions

- Los campos mínimos obligatorios de alta quedan abiertos hasta que Compliance los defina formalmente (ver Open Questions OQ-1).
- La clave de unicidad es el documento de identidad del cliente (la fuente menciona "ej. mismo documento" como regla de negocio central); se asume único por tipo+número de documento (ver OQ-2).
- Los campos actualizables son: nombre para tratamiento, email de contacto y preferencias generales no sensibles (ver OQ-3).
- Los datos que devuelve la consulta son los mismos campos del alta (excepto datos sensibles de verificación del canal); la política exacta está abierta (ver OQ-4).
- Las transiciones de estado permitidas son: activo → suspendido, suspendido → activo (reactivación), activo/suspendido → baja lógica. Baja lógica es terminal (ver OQ-5).
- No se requiere credencial JWT para los endpoints de este servicio (Requiere credencial JWT = NO según metadatos del hito).

---

## Requirements

### Requirement: Alta de usuario bancario

The system SHALL registrar un nuevo cliente bancario cuando se reciban los datos mínimos obligatorios válidos, asignándole un identificador interno único y dejándolo en estado **activo**.

#### Scenario: Alta exitosa con datos mínimos válidos
- **GIVEN** que no existe ningún cliente con el mismo documento en el sistema
- **WHEN** se envía una solicitud de alta con todos los campos mínimos obligatorios completos y válidos
- **THEN** el sistema crea el cliente con un identificador interno único
- **AND** el cliente queda en estado **activo**
- **AND** el evento de alta queda registrado en auditoría con fecha, acción y resultado

#### Scenario: Rechazo por duplicado de documento
- **GIVEN** que ya existe un cliente con el mismo tipo y número de documento en el sistema
- **WHEN** se envía una solicitud de alta con ese mismo documento
- **THEN** el sistema rechaza la solicitud con error de duplicado
- **AND** no se crea ningún registro nuevo
- **AND** el intento queda registrado en auditoría

#### Scenario: Rechazo por datos mínimos incompletos
- **GIVEN** que se envía una solicitud de alta con uno o más campos mínimos obligatorios ausentes o inválidos
- **WHEN** el sistema valida los datos recibidos
- **THEN** el sistema rechaza la solicitud indicando qué campos son inválidos o faltantes
- **AND** no se crea ningún registro

---

### Requirement: Consulta de usuario por identificador

The system SHALL devolver los datos autorizados y el estado actual de un cliente cuando se consulte por su identificador acordado.

#### Scenario: Consulta exitosa por identificador interno
- **GIVEN** que existe un cliente con el identificador solicitado
- **WHEN** se realiza una consulta por ese identificador interno
- **THEN** el sistema devuelve los datos autorizados del cliente y su estado actual

#### Scenario: Consulta exitosa por documento
- **GIVEN** que existe un cliente con el tipo y número de documento solicitado
- **WHEN** se realiza una consulta por tipo y número de documento
- **THEN** el sistema devuelve los datos autorizados del cliente y su estado actual

#### Scenario: Cliente no encontrado
- **GIVEN** que no existe ningún cliente con el identificador o documento indicado
- **WHEN** se realiza la consulta
- **THEN** el sistema responde que el cliente no existe (recurso no encontrado)

---

### Requirement: Actualización de datos permitidos

The system SHALL permitir modificar únicamente los campos autorizados de un cliente existente y registrar el cambio en auditoría.

#### Scenario: Actualización exitosa de campos permitidos
- **GIVEN** que existe un cliente activo o suspendido con el identificador indicado
- **WHEN** se envía una solicitud de actualización con valores válidos para uno o más campos permitidos
- **THEN** el sistema aplica los cambios a esos campos
- **AND** confirma la actualización
- **AND** registra en auditoría qué campos fueron modificados, con fecha y resultado

#### Scenario: Intento de modificar campos no permitidos
- **GIVEN** que se envía una solicitud de actualización con campos fuera de los permitidos (ej. datos legales sensibles)
- **WHEN** el sistema valida el payload
- **THEN** el sistema rechaza la solicitud indicando que esos campos no son modificables por este servicio

#### Scenario: Cliente inexistente en actualización
- **GIVEN** que el identificador enviado no corresponde a ningún cliente
- **WHEN** se intenta la actualización
- **THEN** el sistema responde que el cliente no existe (recurso no encontrado)

---

### Requirement: Cambio de estado operativo del usuario

The system SHALL permitir cambiar el estado de un cliente (suspendido, reactivado, baja lógica) cuando el cambio sea válido según la política del banco, y SHALL registrar el cambio en auditoría.

#### Scenario: Suspensión de usuario activo
- **GIVEN** que el cliente está en estado **activo**
- **WHEN** se solicita cambiar el estado a **suspendido**
- **THEN** el sistema actualiza el estado a suspendido
- **AND** registra el cambio en auditoría con fecha, estado anterior, estado nuevo y resultado

#### Scenario: Reactivación de usuario suspendido
- **GIVEN** que el cliente está en estado **suspendido**
- **WHEN** se solicita cambiar el estado a **activo**
- **THEN** el sistema actualiza el estado a activo
- **AND** registra el cambio en auditoría

#### Scenario: Baja lógica de usuario
- **GIVEN** que el cliente está en estado **activo** o **suspendido**
- **WHEN** se solicita dar de baja lógicamente al cliente
- **THEN** el sistema actualiza el estado a **baja lógica**
- **AND** registra el cambio en auditoría

#### Scenario: Transición de estado no permitida
- **GIVEN** que el cliente está en estado **baja lógica**
- **WHEN** se solicita cualquier cambio de estado
- **THEN** el sistema rechaza el cambio indicando que la baja lógica es un estado terminal

#### Scenario: Cliente en baja lógica o suspendido no puede iniciar vínculo de canal
- **GIVEN** que un cliente está en estado **suspendido** o **baja lógica**
- **WHEN** un servicio externo (ej. H3 — Canal WhatsApp) consulta si el cliente puede iniciar un nuevo vínculo
- **THEN** el sistema indica que el cliente no está habilitado para nuevos vínculos de canal

---

### Requirement: Validación de unicidad de documento

The system SHALL rechazar el alta de un cliente cuyo documento ya se encuentre registrado en el sistema, sin importar el estado del cliente existente.

#### Scenario: Documento ya registrado en cliente activo
- **GIVEN** que existe un cliente activo con el documento indicado
- **WHEN** se intenta dar de alta otro cliente con el mismo documento
- **THEN** el sistema rechaza el alta con error de duplicado

#### Scenario: Documento ya registrado en cliente dado de baja
- **GIVEN** que existe un cliente en baja lógica con el documento indicado
- **WHEN** se intenta dar de alta otro cliente con el mismo documento
- **THEN** el sistema rechaza el alta con error de duplicado (el documento sigue siendo único en el sistema)

---

### Requirement: Auditoría de eventos del ciclo de vida del usuario

The system SHALL registrar automáticamente toda alta, cambio de estado y modificación de datos relevantes, incluyendo fecha, tipo de acción y resultado.

#### Scenario: Registro de alta
- **WHEN** se completa exitosamente el alta de un cliente
- **THEN** el sistema persiste un registro de auditoría con: identificador del cliente, tipo de evento (ALTA), fecha/hora y resultado (OK)

#### Scenario: Registro de cambio de estado
- **WHEN** se completa un cambio de estado (suspensión, reactivación, baja lógica)
- **THEN** el sistema persiste un registro de auditoría con: identificador del cliente, tipo de evento (CAMBIO_ESTADO), estado anterior, estado nuevo, fecha/hora y resultado

#### Scenario: Registro de modificación de datos
- **WHEN** se completa una actualización de datos permitidos
- **THEN** el sistema persiste un registro de auditoría con: identificador del cliente, tipo de evento (MODIFICACION_DATOS), campos modificados, fecha/hora y resultado

#### Scenario: Registro de intento fallido de alta duplicada
- **WHEN** se rechaza un alta por duplicado de documento
- **THEN** el sistema persiste un registro de auditoría con: tipo de evento (ALTA_RECHAZADA), motivo y fecha/hora

---

## Open Questions

| ID | Pregunta | Impacto |
|----|----------|---------|
| OQ-1 | ¿Cuáles son los campos mínimos obligatorios para el alta? (nombre legal, apellido, tipo documento, número documento, fecha nacimiento, email…) | Define el payload de U1 y la validación de completitud |
| OQ-2 | ¿La unicidad es solo por número de documento, o incluye el tipo de documento? ¿El documento puede existir en baja lógica sin bloquear un nuevo alta? | Afecta Requirement de unicidad y Scenario de documento en baja lógica |
| OQ-3 | ¿Cuál es la lista exacta de campos actualizables por este servicio? (nombre para tratamiento, email, teléfono, preferencias…) | Define el payload de U3 y el rechazo de campos no permitidos |
| OQ-4 | ¿Qué campos se exponen en la respuesta de consulta a otros servicios vs. a operaciones/soporte? ¿Hay diferencia por actor? | Afecta Requirement de consulta y política de privacidad |
| OQ-5 | ¿Existen más transiciones de estado? ¿Puede reactivarse un usuario en baja lógica mediante proceso especial? | Afecta Requirement de cambio de estado y Scenario de baja lógica terminal |
