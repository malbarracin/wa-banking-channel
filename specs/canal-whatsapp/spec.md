---
title: Canal WhatsApp — especificación
status: borrador
source: https://app.notion.com/p/38275bb08ca781c0bc08d80366637aa5
updated: 2026-11-27T23:00:27.345Z
---

# Canal WhatsApp — especificación

## Propósito

Gestionar la relación entre un número de WhatsApp y un cliente del banco: vincular el número con un cliente existente y activo, verificar su identidad, mantener los estados del vínculo, exponer el perfil y las preferencias del canal, bloquear o desvincular el acceso, habilitar la re-vinculación y emitir el resultado verificable que habilita la credencial de sesión (H2). No recibe webhooks de Twilio, no transporta mensajes y no orquesta el ciclo de conversación.

## Datos del proyecto

| Campo | Valor | Fuente |
|-------|-------|--------|
| Carpeta del componente | `services/wa-channel-service` | Hito H3, propiedad «Carpeta del componente» |
| groupId Maven | `com.bcra` | Roadmap, sección «Convenciones técnicas» |
| Paquete base | `com.bcra.channel.ws` | Roadmap, tabla de paquetes por hito |
| Nombre del artefacto | `wa-channel-service` | Roadmap, tabla «Orden de desarrollo» |
| Contrato a publicar | `shared/contracts/wa-channel-service/openapi.yaml` y `handoff.md` | Hito H3 |
| Contrato a respetar | `shared/contracts/wa-users-service/openapi.yaml` y `handoff.md` (H1) | Hito H3 |
| Puerto dentro del contenedor | `8080` (configurable con `SERVER_PORT`) | Roadmap, convenciones técnicas |
| Pull request contra | `main` | Roadmap |

## Definiciones configurables del banco

Las siguientes definiciones no tienen valores fijos en la especificación; se implementan como parámetros en `application.yml`:

| Parámetro | Descripción |
|-----------|-------------|
| `bank.channel.verification.policy` | Método de verificación de identidad (documento + código SMS, preguntas, etc.) |
| `bank.channel.verification.max-attempts` | Tope de intentos de verificación por ventana |
| `bank.channel.verification.window-minutes` | Ventana de tiempo para contar los intentos |
| `bank.channel.name` | Nombre del banco para los mensajes al cliente |
| `bank.channel.support-phone` | Teléfono de soporte para mensajes de error |
| `bank.channel.onboarding-url` | Canal/URL de alta bancaria para derivación |

## Requisitos

### Requisito: consulta del estado del vínculo

El sistema DEBE responder si un número de WhatsApp dado está vinculado y en qué estado, para que el Orquestador del Canal (H0.2) pueda determinar el flujo a seguir.

#### Escenario: número sin vínculo previo
- **DADO** que el número de WhatsApp no tiene vínculo registrado
- **CUANDO** H0.2 consulta el estado del vínculo para ese número
- **ENTONCES** el sistema responde con estado `SIN_VINCULO`

#### Escenario: número con vínculo activo
- **DADO** que el número tiene vínculo en estado `ACTIVO`
- **CUANDO** H0.2 consulta el estado del vínculo
- **ENTONCES** el sistema responde con estado `ACTIVO` y el identificador del cliente vinculado

#### Escenario: número con vínculo bloqueado
- **DADO** que el número tiene vínculo en estado `BLOQUEADO`
- **CUANDO** H0.2 consulta el estado del vínculo
- **ENTONCES** el sistema responde con estado `BLOQUEADO`

---

### Requisito: registro de aceptación de términos y condiciones

El sistema DEBE registrar la aceptación explícita de las condiciones del canal antes de solicitar datos de identificación, y NO DEBE avanzar al paso de verificación sin esa aceptación.

#### Escenario: cliente acepta los términos
- **DADO** que el número está en estado `SIN_VINCULO` o `DESVINCULADO`
- **CUANDO** el cliente confirma explícitamente la aceptación de los términos
- **ENTONCES** el sistema registra la aceptación, crea el vínculo en estado `PENDIENTE_VERIFICACION` y habilita el paso de verificación

#### Escenario: cliente rechaza los términos
- **DADO** que el número está sin vínculo activo
- **CUANDO** el cliente responde sin aceptar los términos
- **ENTONCES** el sistema NO crea vínculo ni solicita datos, y devuelve el mensaje de no-aceptación (sección 8.2 de la especificación)

---

### Requisito: verificación de existencia y estado del cliente en H1

El sistema DEBE consultar el Servicio de Usuarios (H1) para confirmar que el cliente existe y está activo antes de proceder a la vinculación.

#### Escenario: cliente existente y activo
- **DADO** que el vínculo está en estado `PENDIENTE_VERIFICACION` y el cliente proveyó datos de identificación
- **CUANDO** el sistema consulta H1 con esos datos
- **ENTONCES** H1 responde que el cliente existe y está activo, y el flujo continúa a verificación de identidad

#### Escenario: cliente no encontrado en H1
- **DADO** que el vínculo está en estado `PENDIENTE_VERIFICACION`
- **CUANDO** H1 no encuentra el cliente con los datos provistos
- **ENTONCES** el sistema no crea el vínculo, devuelve el mensaje de derivación al alta bancaria (sección 8.4), y el flujo termina

#### Escenario: cliente suspendido o dado de baja en H1
- **DADO** que el vínculo está en estado `PENDIENTE_VERIFICACION`
- **CUANDO** H1 encuentra el cliente pero su estado es `SUSPENDED` o `INACTIVE`
- **ENTONCES** el sistema no crea el vínculo y devuelve el mensaje correspondiente (sección 8.9)

---

### Requisito: verificación de identidad con tope de intentos

El sistema DEBE validar que quien escribe es el titular del cliente encontrado en H1, con un número máximo de intentos configurables por ventana de tiempo.

#### Escenario: verificación exitosa en primer intento
- **DADO** que el cliente existe y está activo en H1
- **CUANDO** el cliente provee los datos de verificación correctos
- **ENTONCES** la verificación resulta exitosa y el flujo continúa a la validación anti-fraude

#### Escenario: verificación fallida con intentos restantes
- **DADO** que el cliente no ha agotado el tope de intentos configurado (`bank.channel.verification.max-attempts`)
- **CUANDO** el cliente provee datos de verificación incorrectos
- **ENTONCES** el sistema registra el intento fallido, decrementa el contador y devuelve el mensaje de verificación fallida con la cantidad de intentos restantes (sección 8.5)

#### Escenario: intentos de verificación agotados
- **DADO** que el cliente alcanzó el tope de intentos dentro de la ventana de tiempo configurada
- **CUANDO** se produce un nuevo intento fallido
- **ENTONCES** el sistema bloquea temporalmente el vínculo, registra el evento en auditoría y devuelve el mensaje de intentos agotados con derivación a soporte (sección 8.6)

---

### Requisito: validación anti-duplicados y anti-fraude

El sistema DEBE verificar que el número de WhatsApp no esté ya vinculado activamente a otro cliente antes de completar la vinculación.

#### Escenario: número libre
- **DADO** que la identidad fue verificada exitosamente
- **CUANDO** el sistema comprueba si el número ya tiene un vínculo activo con otro cliente
- **ENTONCES** no se encuentra conflicto y el flujo continúa a la vinculación

#### Escenario: número ya vinculado a otro cliente
- **DADO** que la identidad fue verificada exitosamente
- **CUANDO** el sistema detecta que el número ya tiene un vínculo en estado `ACTIVO` con un cliente distinto
- **ENTONCES** rechaza la vinculación, escala a soporte y devuelve el mensaje de número ya vinculado (sección 8.7)

---

### Requisito: vinculación y emisión del resultado verificable

El sistema DEBE registrar el vínculo en estado `ACTIVO` tras verificación exitosa y emitir el resultado verificable que H0.2 usa para coordinar la solicitud de credencial con H2.

#### Escenario: vinculación exitosa
- **DADO** que la identidad fue verificada y el número está libre de conflictos
- **CUANDO** el sistema completa la vinculación
- **ENTONCES** registra el vínculo en estado `ACTIVO`, registra la acción en auditoría, devuelve el resultado verificable `{identityVerified: true, bindingId: ..., clientId: ...}` a H0.2, y el mensaje de bienvenida al cliente (sección 8.8)
- **Y** la credencial de sesión la emite H2 coordinada por H0.2, no H3 directamente

---

### Requisito: consulta del perfil del canal

El sistema DEBE exponer el perfil del cliente en el canal (datos permitidos sin información legal sensible) y sus preferencias.

#### Escenario: consulta de perfil con vínculo activo
- **DADO** que el vínculo del número está en estado `ACTIVO`
- **CUANDO** se solicita el perfil del canal para ese número
- **ENTONCES** el sistema devuelve los datos del perfil (nombre, estado del vínculo, preferencias de idioma y notificaciones) sin incluir datos legales sensibles

#### Escenario: consulta de perfil sin vínculo activo
- **DADO** que el número no tiene vínculo en estado `ACTIVO`
- **CUANDO** se solicita el perfil
- **ENTONCES** el sistema devuelve `404 NOT_FOUND`

---

### Requisito: actualización de preferencias del canal

El sistema DEBE permitir al cliente actualizar sus preferencias en el canal (idioma, notificaciones, horarios si aplica).

#### Escenario: actualización exitosa de preferencias
- **DADO** que el vínculo está en estado `ACTIVO`
- **CUANDO** el cliente envía preferencias actualizadas (idioma, activación de notificaciones)
- **ENTONCES** el sistema guarda los cambios y los confirma en la respuesta

---

### Requisito: bloqueo del vínculo

El sistema DEBE bloquear el vínculo a pedido del cliente o por política del banco, cortando el acceso en el mismo intercambio.

#### Escenario: bloqueo a pedido del cliente
- **DADO** que el vínculo está en estado `ACTIVO`
- **CUANDO** el cliente solicita bloquear su acceso y confirma explícitamente
- **ENTONCES** el sistema cambia el estado del vínculo a `BLOQUEADO`, registra la acción en auditoría y devuelve el mensaje de bloqueo (sección 8.10)
- **Y** H0.2 coordina la revocación de la credencial en H2 con el resultado devuelto por H3

#### Escenario: bloqueo por política del banco
- **DADO** que el banco (riesgo/política) solicita bloquear el vínculo de un número
- **CUANDO** se llama al endpoint de bloqueo con autorización del banco
- **ENTONCES** el sistema cambia el estado a `BLOQUEADO`, registra en auditoría y el acceso queda cortado de inmediato

---

### Requisito: desvinculación del canal

El sistema DEBE desvincular el número a pedido del cliente o por política del banco, cortando el acceso en el mismo intercambio.

#### Escenario: desvinculación a pedido del cliente
- **DADO** que el vínculo está en estado `ACTIVO` o `BLOQUEADO`
- **CUANDO** el cliente solicita desvincular su número y confirma explícitamente
- **ENTONCES** el sistema cambia el estado a `DESVINCULADO`, registra en auditoría y devuelve el mensaje de desvinculación (sección 8.10)
- **Y** H0.2 coordina la revocación de la credencial en H2

---

### Requisito: re-vinculación con verificación completa

El sistema DEBE exigir verificación de identidad completa para re-vincular un número que fue desvinculado o bloqueado.

#### Escenario: cliente re-vincula tras desvinculación
- **DADO** que el número tiene vínculo en estado `DESVINCULADO` o `BLOQUEADO`
- **CUANDO** el cliente inicia el proceso de re-vinculación
- **ENTONCES** el sistema ejecuta el flujo completo de onboarding (términos, verificación, anti-fraude) como si fuera primera vez
- **Y** tras la verificación exitosa emite un nuevo vínculo `ACTIVO` y un nuevo resultado verificable para H0.2

---

### Requisito: historial resumido para soporte

El sistema DEBE exponer un historial resumido de las interacciones del vínculo (eventos de vinculación, bloqueos, desvinculaciones, intentos fallidos) para uso del equipo de soporte del banco.

#### Escenario: consulta de historial por soporte
- **DADO** que el soporte del banco consulta el historial de un vínculo por `bindingId` o por número
- **CUANDO** la solicitud incluye autorización de soporte
- **ENTONCES** el sistema devuelve la secuencia de eventos del vínculo ordenados por fecha, sin secretos ni datos de verificación

---

### Requisito: auditoría de la secuencia completa

El sistema DEBE registrar en auditoría cada acción relevante del ciclo de vida del vínculo, sin incluir secretos ni datos de verificación en los registros.

#### Escenario: auditoría del onboarding
- **DADO** que se completa el flujo de onboarding
- **CUANDO** la secuencia finaliza (con éxito o con error)
- **ENTONCES** el registro de auditoría incluye: consulta de estado, aceptación de términos, resultado de verificación de identidad (sin los datos en sí), resultado anti-fraude, alta del vínculo y emisión del resultado verificable

#### Escenario: auditoría de bloqueo o desvinculación
- **DADO** que se ejecuta un bloqueo o desvinculación
- **CUANDO** la acción se completa
- **ENTONCES** el registro incluye quién la solicitó (cliente o banco), fecha y estado resultante, sin datos de verificación

---

### Requisito: mensajes al cliente sin secretos

El sistema NO DEBE incluir secretos, tokens, credenciales ni datos sensibles en los mensajes textuales dirigidos al cliente.

#### Escenario: mensaje de bienvenida tras vinculación
- **DADO** que la vinculación fue exitosa
- **CUANDO** el sistema prepara el mensaje al cliente (sección 8.8 de la especificación)
- **ENTONCES** el mensaje incluye el nombre del cliente pero no el `bindingId`, token ni ningún dato de verificación

---

## Estados del vínculo

| Estado | Significado | Transiciones posibles |
|--------|-------------|-----------------------|
| `SIN_VINCULO` | Número desconocido; primera interacción | → `PENDIENTE_VERIFICACION` (al aceptar términos) |
| `PENDIENTE_VERIFICACION` | Aceptó términos; identidad aún no verificada | → `ACTIVO` (verificación OK) · → `BLOQUEADO` (intentos agotados) |
| `ACTIVO` | Identidad verificada; número vinculado | → `BLOQUEADO` · → `DESVINCULADO` |
| `BLOQUEADO` | Acceso cortado por cliente o por política | → `ACTIVO` (re-verificación) · → `DESVINCULADO` |
| `DESVINCULADO` | Número desvinculado explícitamente | → `PENDIENTE_VERIFICACION` (re-vinculación) |

## Interfaz con otros componentes

| Componente | Tipo de interacción | Qué hace H3 |
|------------|---------------------|-------------|
| H0.2 Orquestador | Síncrona (invocado por H0.2) | Recibe solicitudes de onboarding, estado y operaciones; devuelve resultados verificables |
| H1 Usuarios | Síncrona (H3 llama a H1) | Consulta existencia y estado del cliente por documento/ID |
| H2 Acceso y Sesión | No directa (H0.2 coordina) | H3 no llama a H2; entrega resultado verificable a H0.2, que solicita la credencial |

## Fuera de alcance de H3

- Alta de cliente nuevo del banco (H1).
- Emisión directa de credencial de sesión (H2).
- Recepción o envío de mensajes de Twilio (H0).
- Transporte asíncrono y topics Kafka (H0.1).
- Coordinación del ciclo del mensaje (H0.2).
- Operación de productos: cuentas, tarjetas, seguros (H4, H5, H6).
- Cambio de datos legales sensibles del cliente.

## Preguntas abiertas

1. **Política de verificación de identidad**: la especificación enumera métodos posibles (documento + código SMS/email, preguntas de validación) pero delega la elección a Compliance. Se implementa como parámetro configurable. No bloquea el spec; bloquearía solo si se requiere un contrato de verificación distinto por método.
2. **Autorización del endpoint de soporte**: la especificación menciona que soporte consulta el historial, pero no define el mecanismo de autorización (cabecera interna, rol JWT). Se asume cabecera `X-Internal-Role: support` configurable; a confirmar con arquitecto al definir el contrato.
3. **Autorización del endpoint de bloqueo por política del banco**: similar al punto anterior. Se asume operación autenticada con rol `BANK_POLICY`; a confirmar con arquitecto.
