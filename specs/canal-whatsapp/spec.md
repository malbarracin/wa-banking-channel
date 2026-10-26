---
title: Canal WhatsApp — especificación
status: aprobada
source: https://app.notion.com/p/H3-Servicio-Canal-WhatsApp-38275bb08ca781c0bc08d80366637aa5
updated: 2026-10-26T15:04:05.194Z
---

# Canal WhatsApp — especificación

## Propósito

El Servicio Canal WhatsApp (H3) gestiona el vínculo entre un número de WhatsApp y un cliente **existente** del
banco: verificación de identidad para habilitarlo, estado del vínculo, perfil y preferencias del canal,
bloqueo/desvinculación, re-vinculación y un historial resumido para soporte. No recibe webhooks de Twilio, no
transporta mensajes ni orquesta el ciclo de la conversación (eso es H0/H0.1/H0.2); no da de alta clientes nuevos
(eso es H1) ni emite la credencial de sesión (eso es H2) — tras verificar identidad, entrega un resultado
verificable para que el Orquestador del Canal (H0.2) coordine la emisión de la credencial con H2.

## Fuera de alcance (de la fuente)

- Alta de cliente nuevo del banco (H1 — debe existir previamente y estar activo).
- Emisión o revocación directa de la credencial de sesión (H2); H3 solicita la revocación, no la ejecuta.
- Recepción/envío de mensajes de WhatsApp y adaptación a Twilio (H0 Gateway).
- Transporte asíncrono y tópicos Kafka (H0.1).
- Estado técnico, correlación y coordinación del ciclo del mensaje (H0.2 Orquestador).
- Operación de cuentas, tarjetas o seguros (H4, H5, H6).
- Cambio de datos legales sensibles del cliente.

## Actores

- **Cliente titular**: vincula su número, verifica identidad, consulta/edita perfil y preferencias,
  bloquea/desvincula, re-vincula.
- **Representante autorizado**: solo lo que la política del banco permita, con verificación reforzada (la
  fuente no cierra el detalle — ver Preguntas abiertas).
- **Soporte del banco**: consulta historial resumido y estado del vínculo.
- **Banco (política/riesgo)**: puede bloquear el vínculo, forzar re-verificación y solicitar la revocación de
  la credencial en H2.

## Requisitos

### Requisito: Aceptación de términos del canal antes de identificarse
El sistema NO DEBE solicitar datos de identificación a un cliente que no haya aceptado explícitamente los
términos y condiciones del canal WhatsApp.

#### Escenario: Primer contacto sin aceptar términos
- **DADO** que un cliente escribe por primera vez, o desde un número sin vínculo, y aún no aceptó los términos
- **CUANDO** el canal le presenta los términos y condiciones
- **ENTONCES** el sistema no solicita ningún dato de identificación
- **Y** si el cliente no acepta, se le informa que sin aceptación no se puede continuar y que puede retomar cuando quiera

#### Escenario: Aceptación habilita la verificación
- **DADO** un cliente que acepta explícitamente los términos
- **CUANDO** confirma esa aceptación
- **ENTONCES** el vínculo queda en estado "pendiente de verificación"
- **Y** el sistema continúa solicitando los datos de identificación

### Requisito: Vinculación con cliente existente y activo en H1
El sistema DEBE consultar la existencia y el estado del cliente en el Servicio de Usuarios (H1) antes de
continuar con la verificación, y NO DEBE vincular un número a un cliente que no exista o no esté activo en H1.

#### Escenario: Cliente no encontrado en H1
- **DADO** que el cliente que escribe no tiene registro en H1
- **CUANDO** el sistema consulta su existencia
- **ENTONCES** el sistema no crea ningún vínculo
- **Y** informa que, para operar por WhatsApp, primero debe ser cliente del banco por otro canal (sucursal/app)

#### Escenario: Cliente existente pero no activo en H1
- **DADO** que el cliente existe en H1 pero su estado no es activo (suspendido o baja lógica)
- **CUANDO** el sistema consulta su estado
- **ENTONCES** el sistema no crea ningún vínculo
- **Y** informa que su perfil no está habilitado para operar por este canal, según la política del banco

#### Escenario: Cliente existente y activo en H1
- **DADO** que el cliente existe en H1 y su estado es activo
- **CUANDO** el sistema consulta su estado
- **ENTONCES** el sistema continúa con la verificación de identidad

### Requisito: Verificación de identidad con límite de intentos
El sistema DEBE verificar la identidad de quien escribe antes de activar el vínculo, y NO DEBE permitir
intentos ilimitados: al agotarse el máximo de intentos dentro de una ventana de tiempo definida por la política
del banco, debe pausar temporalmente la verificación y derivar a soporte.

#### Escenario: Verificación exitosa
- **DADO** un cliente existente y activo en H1 que aceptó los términos
- **CUANDO** provee correctamente los datos de verificación requeridos por la política del banco
- **ENTONCES** la verificación se marca exitosa
- **Y** el sistema continúa hacia la vinculación

#### Escenario: Verificación fallida con intentos disponibles
- **DADO** un cliente en proceso de verificación
- **CUANDO** los datos provistos no validan correctamente y aún le quedan intentos dentro de la ventana vigente
- **ENTONCES** el sistema informa que la verificación falló y cuántos intentos le quedan, ofreciendo reintentar o contactar soporte
- **Y** el vínculo permanece en estado "pendiente de verificación"

#### Escenario: Intentos agotados
- **DADO** un cliente que agotó el máximo de intentos de verificación dentro de la ventana vigente
- **CUANDO** intenta un nuevo intento
- **ENTONCES** el sistema pausa temporalmente la verificación y deriva al cliente a soporte
- **Y** no se crea ni activa ningún vínculo

### Requisito: Unicidad del vínculo activo por número
El sistema NO DEBE permitir que un mismo número de WhatsApp quede activo para más de un cliente a la vez.

#### Escenario: Número ya vinculado a otro cliente
- **DADO** que un número de WhatsApp ya tiene un vínculo activo con un cliente
- **CUANDO** otro cliente, verificado exitosamente, intenta vincular ese mismo número
- **ENTONCES** el sistema rechaza la vinculación, informa que el número ya está asociado a otra persona y escala el caso a soporte

#### Escenario: Número libre tras verificación exitosa
- **DADO** un cliente verificado exitosamente cuyo número no tiene ningún vínculo activo con otro cliente
- **CUANDO** se completa la verificación
- **ENTONCES** el sistema registra el vínculo entre el número y el cliente en estado activo

### Requisito: Resultado verificable de identidad/vínculo para la credencial de sesión
El sistema DEBE entregar, tras una vinculación o re-verificación exitosa, un resultado verificable de identidad
y vínculo activo para que el Orquestador del Canal (H0.2) coordine la emisión de la credencial de sesión con
H2; el sistema NO DEBE emitir ni gestionar directamente esa credencial.

#### Escenario: Resultado disponible tras vinculación exitosa
- **DADO** que el vínculo entre el número y el cliente queda en estado activo
- **CUANDO** el proceso de verificación concluye exitosamente
- **ENTONCES** el sistema entrega al Orquestador un resultado verificable de identidad y vínculo activo
- **Y** no emite por sí mismo ninguna credencial de sesión

### Requisito: Consulta de perfil en el canal
El sistema DEBE permitir al cliente consultar los datos de su perfil en el canal, limitados a los datos
permitidos, y NO DEBE exponer datos legales sensibles.

#### Escenario: Consulta de perfil con vínculo activo
- **DADO** un cliente con vínculo activo y credencial válida
- **CUANDO** solicita su perfil
- **ENTONCES** el sistema devuelve los datos permitidos del perfil en el canal
- **Y** no incluye datos legales sensibles

#### Escenario: Consulta de perfil sin credencial válida
- **DADO** un cliente sin credencial válida vigente
- **CUANDO** solicita su perfil
- **ENTONCES** el sistema lo deriva al proceso de verificación antes de mostrar cualquier dato

### Requisito: Preferencias del canal
El sistema DEBE permitir consultar y modificar las preferencias del canal del cliente (idioma, notificaciones y
horarios cuando aplique), confirmando cada cambio.

#### Escenario: Modificación de una preferencia del canal
- **DADO** un cliente con vínculo activo
- **CUANDO** solicita cambiar una preferencia del canal (por ejemplo, idioma o notificaciones)
- **ENTONCES** el sistema guarda el cambio
- **Y** confirma al cliente que la preferencia quedó actualizada

### Requisito: Bloqueo del vínculo
El sistema DEBE permitir bloquear el vínculo, a pedido explícito del cliente (con confirmación) o por decisión
de política/riesgo del banco, y DEBE solicitar de inmediato la revocación de la credencial de sesión en H2.

#### Escenario: Bloqueo a pedido del cliente
- **DADO** un cliente con vínculo activo
- **CUANDO** solicita bloquear su canal y confirma explícitamente la acción
- **ENTONCES** el vínculo queda en estado bloqueado
- **Y** el sistema solicita la revocación de la credencial en H2 en el mismo intercambio

#### Escenario: Bloqueo por política del banco
- **DADO** un vínculo activo que el banco decide bloquear por política o riesgo
- **CUANDO** se aplica esa decisión
- **ENTONCES** el vínculo queda en estado bloqueado
- **Y** el sistema solicita la revocación de la credencial en H2 de inmediato

### Requisito: Desvinculación del canal
El sistema DEBE permitir desvincular el canal a pedido explícito del cliente (con confirmación) y DEBE
solicitar de inmediato la revocación de la credencial en H2.

#### Escenario: Desvinculación confirmada
- **DADO** un cliente con vínculo activo o bloqueado
- **CUANDO** solicita desvincular su canal y confirma explícitamente
- **ENTONCES** el vínculo queda en estado desvinculado
- **Y** el sistema solicita la revocación de la credencial en H2 en el mismo intercambio

### Requisito: Re-vinculación tras cambio de dispositivo o número
El sistema DEBE exigir el mismo proceso completo de verificación de identidad que en la vinculación inicial
cuando un cliente con vínculo desvinculado o bloqueado, o con un número nuevo, quiera volver a operar por
WhatsApp, y NO DEBE reutilizar ninguna credencial anterior.

#### Escenario: Re-vinculación tras desvinculación o bloqueo
- **DADO** un cliente cuyo vínculo anterior está desvinculado o bloqueado
- **CUANDO** vuelve a escribir y pide re-vincularse
- **ENTONCES** el sistema exige el proceso completo de verificación de identidad, igual que en la vinculación inicial
- **Y** una verificación exitosa resulta en un nuevo vínculo activo y una nueva credencial, sin reutilizar la anterior

### Requisito: Consulta de vínculo por número (anti-duplicados y fraude)
El sistema DEBE permitir consultar si un número de WhatsApp ya tiene un vínculo activo, para prevenir
duplicados y fraude, sin exponer los datos del cliente vinculado a quien no esté autorizado a verlos.

#### Escenario: Consulta de número ya vinculado
- **DADO** un número de WhatsApp con un vínculo activo
- **CUANDO** se consulta si ese número ya está vinculado
- **ENTONCES** el sistema informa que el número está vinculado
- **Y** no expone los datos del cliente vinculado a quien no esté autorizado

### Requisito: Historial resumido de interacciones para soporte
El sistema DEBE permitir a soporte del banco consultar un historial resumido de interacciones y el estado
actual del vínculo de un cliente, para asistencia.

#### Escenario: Consulta de historial por soporte
- **DADO** un cliente con vínculo registrado, en cualquier estado
- **CUANDO** soporte consulta su historial resumido
- **ENTONCES** el sistema devuelve el historial resumido de interacciones y el estado actual del vínculo

### Requisito: Auditoría de las acciones del vínculo
El sistema DEBE registrar en auditoría toda acción relevante del ciclo de vida del vínculo (aceptación de
términos, intentos de verificación exitosos y fallidos, vinculación, bloqueo, desvinculación, re-vinculación), y
NO DEBE incluir secretos ni datos de verificación sensibles en esos registros ni en los mensajes al cliente.

#### Escenario: Registro de auditoría de la secuencia de onboarding
- **DADO** que se ejecuta la secuencia de aceptación de términos, verificación y vinculación
- **CUANDO** cada paso relevante concluye, exitoso o rechazado
- **ENTONCES** queda un registro de auditoría de esa acción, asociado al número y al cliente cuando corresponda
- **Y** el registro no contiene secretos ni datos sensibles de verificación

#### Escenario: Registro de auditoría de bloqueo, desvinculación o re-vinculación
- **DADO** que se ejecuta un bloqueo, una desvinculación o una re-vinculación
- **CUANDO** la acción concluye
- **ENTONCES** queda un registro de auditoría de esa acción

## Preguntas abiertas

- Método exacto de verificación de identidad (documento, código por SMS/email, preguntas de validación, o
  combinación): la fuente lo deja "según política, a definir con Compliance". No bloquea el resto del hito; el
  arquitecto/TL deben dejarlo parametrizable.
- Número máximo de intentos de verificación y duración de la ventana/pausa temporal: la fuente dice "política
  del banco" sin un valor concreto.
- Alcance exacto de "representante autorizado": qué puede hacer exactamente y qué verificación reforzada
  aplica — la fuente lo deja abierto ("solo lo que la política permita").
- Condiciones exactas bajo las cuales el banco bloquea un vínculo por política o riesgo (criterios de riesgo):
  no están definidas en la fuente.
- Campos exactos del "perfil permitido" en el canal y de las "preferencias del canal" más allá de
  idioma/notificaciones/horarios: la fuente no los detalla.
- Contenido exacto del "historial resumido" para soporte (qué eventos y qué período): no está definido en la
  fuente.
- Relación entre los estados descriptivos del onboarding ("sin vínculo", "verificación fallida", sección 6.1 de
  la fuente) y los estados persistidos del vínculo (pendiente de verificación, activo, bloqueado, desvinculado,
  sección 3 de la fuente): se interpretó que "sin vínculo" es la ausencia de registro, que "verificación
  fallida" es un evento transitorio dentro de "pendiente de verificación", y que el agotamiento de intentos
  deriva en una pausa temporal equivalente a un bloqueo. Si esta interpretación no es correcta, el modelo de
  estados debe ajustarse (decisión para arquitecto/TL).
