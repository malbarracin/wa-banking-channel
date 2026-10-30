---
title: Tarjetas — especificación
status: borrador
source: https://app.notion.com/p/H5-Servicio-de-Tarjetas-36875bb08ca781bea9c8cb37c2ccdbbf
updated: 2026-10-30T15:40:54.647Z
---

# Tarjetas — especificación

## Propósito

El Servicio de Tarjetas (H5) permite al cliente del canal WhatsApp listar sus tarjetas, ver consumos recientes, bloquear temporal o definitivamente por robo/extravío, desbloquear bloqueos temporales y registrar avisos de viaje. Solo opera con credencial de sesión válida (H2) y vínculo activo en el canal (H3). Nunca expone datos sensibles de la tarjeta (PAN, CVV, fecha de vencimiento).

## Fuera de alcance (de la fuente)

- Mostrar PAN completo, CVV o fecha de vencimiento.
- Alta de tarjetas nuevas.
- Pago de resumen de tarjeta de crédito.
- Disputa de consumos (derivar a proceso formal).

## Actores

- **Cliente titular con JWT válido**: consulta tarjetas y consumos; bloquea/desbloquea temporal; bloqueo definitivo; aviso de viaje.
- **Banco (riesgo)**: puede forzar bloqueo y revocar operaciones del canal.
- **Cliente sin JWT / vínculo inactivo**: recibe mensaje de derivación a verificación (H3).

## Requisitos

### Requisito: Listado de tarjetas del titular
El sistema DEBE listar las tarjetas del titular autenticado (últimos dígitos, marca, tipo débito/crédito, estado) y NO DEBE mostrar tarjetas de otros titulares, cuando presenta una credencial de sesión válida y el vínculo está activo.

#### Escenario: Listado de tarjetas con credencial válida
- **DADO** un cliente con credencial de sesión válida y vínculo activo en el canal (H3)
- **CUANDO** solicita el listado de sus tarjetas ("mis tarjetas")
- **ENTONCES** el sistema devuelve solo las tarjetas del titular autenticado con últimos dígitos, marca, tipo y estado
- **Y** la operación queda registrada

#### Escenario: Rechazo del listado sin credencial válida
- **DADO** un cliente sin credencial JWT válida, o con credencial expirada o revocada
- **CUANDO** solicita el listado de sus tarjetas
- **ENTONCES** el sistema no muestra tarjetas y deriva al cliente a verificación (H3)

### Requisito: Consulta de consumos recientes por tarjeta
El sistema DEBE mostrar los consumos recientes de una tarjeta (comercio, fecha, monto, moneda) con un tope de cantidad y una ventana de días, sin exponer datos sensibles de la tarjeta.

#### Escenario: Consulta de consumos dentro del tope y ventana
- **DADO** una tarjeta del titular, credencial válida y vínculo activo
- **CUANDO** se solicitan los consumos recientes de esa tarjeta
- **ENTONCES** el sistema devuelve los consumos dentro del tope de cantidad y la ventana de días, con comercio, fecha, monto y moneda
- **Y** no incluye PAN, CVV ni fecha de vencimiento en el mensaje

#### Escenario: Rechazo de consumos sin credencial válida
- **DADO** credencial inexistente, expirada o revocada
- **CUANDO** se solicitan consumos de una tarjeta
- **ENTONCES** el sistema rechaza la consulta sin consultar productos

### Requisito: Bloqueo temporal de tarjeta
El sistema DEBE permitir bloquear temporalmente una tarjeta identificada por sus últimos dígitos, exigiendo resumen y confirmación explícita, dejando la tarjeta en estado temporal y comunicando cómo desbloquearla.

#### Escenario: Bloqueo temporal exitoso con confirmación
- **DADO** una tarjeta activa del titular, credencial válida y vínculo activo
- **CUANDO** el cliente solicita bloquear la tarjeta y confirma explícitamente
- **ENTONCES** el sistema deja la tarjeta en estado temporal y responde con mensaje de bloqueo temporal y cómo desbloquear
- **Y** el bloqueo se refleja en el estado antes del siguiente consumo

#### Escenario: Rechazo de bloqueo temporal sin confirmación
- **DADO** una tarjeta activa del titular
- **CUANDO** el cliente solicita bloquearla sin confirmar explícitamente
- **ENTONCES** el sistema no cambia el estado de la tarjeta

### Requisito: Desbloqueo de bloqueo temporal
El sistema DEBE permitir desbloquear una tarjeta con bloqueo temporal tras validación adicional, y NO DEBE permitir el desbloqueo si la tarjeta está en bloqueo definitivo.

#### Escenario: Desbloqueo temporal exitoso tras validación
- **DADO** una tarjeta con bloqueo temporal, credencial válida y vínculo activo
- **CUANDO** el cliente solicita desbloquear y pasa la validación adicional
- **ENTONCES** el sistema reactiva la tarjeta y confirma el desbloqueo

#### Escenario: Desbloqueo no aplicable en bloqueo definitivo
- **DADO** una tarjeta en bloqueo definitivo
- **CUANDO** el cliente solicita desbloquear por chat
- **ENTONCES** el sistema rechaza el desbloqueo y deriva a los canales formales del banco

### Requisito: Bloqueo definitivo por robo o extravío
El sistema DEBE permitir bloquear definitivamente una tarjeta por robo o extravío, exigiendo doble confirmación y motivo (robo/extravío), advirtiendo irreversibilidad por WhatsApp, y NO DEBE permitir desbloqueo por chat.

#### Escenario: Bloqueo definitivo exitoso con doble confirmación
- **DADO** una tarjeta del titular, credencial válida y vínculo activo
- **CUANDO** el cliente indica robo/extravío y confirma dos veces con motivo válido
- **ENTONCES** el sistema aplica el bloqueo definitivo y sugiere reposición por canales oficiales
- **Y** revoca el uso del canal para esa tarjeta aunque la credencial siga válida para otras operaciones

#### Escenario: Rechazo de bloqueo definitivo sin doble confirmación
- **DADO** una tarjeta del titular
- **CUANDO** el cliente solicita bloqueo definitivo sin completar la doble confirmación
- **ENTONCES** el sistema no aplica el bloqueo definitivo

#### Escenario: Advertencia de irreversibilidad
- **DADO** un cliente que solicita bloqueo definitivo
- **CUANDO** inicia el flujo
- **ENTONCES** el sistema advierte que el bloqueo es irreversible por WhatsApp antes de pedir confirmación

### Requisito: Aviso de viaje
El sistema DEBE registrar un aviso de viaje con país/región y fechas, confirmar el registro al cliente y entregar recomendaciones de seguridad, sin reemplazar políticas internacionales del emisor.

#### Escenario: Aviso de viaje registrado y confirmado
- **DADO** una tarjeta del titular, credencial válida y vínculo activo
- **CUANDO** el cliente indica país/región y fechas (desde-hasta) y confirma
- **ENTONCES** el sistema registra el aviso y responde con confirmación del destino y fechas, más recomendaciones de seguridad

#### Escenario: Aviso de viaje no reemplaza límites del banco
- **DADO** un aviso de viaje registrado
- **CUANDO** el cliente opera la tarjeta en el exterior
- **ENTONCES** las políticas internacionales del emisor y los límites del banco siguen aplicando

### Requisito: Seguridad de acceso — credencial de sesión y vínculo
Toda operación de Tarjetas exige credencial de sesión JWT válida y vínculo activo (H3): sin credencial se deriva a verificación; credencial expirada o revocada se rechaza de inmediato sin consultar productos; tras una revocación en H2, Tarjetas deja de responder operaciones en el mismo minuto operativo. Los mensajes nunca incluyen datos sensibles de la tarjeta.

#### Escenario: Operación sin JWT válido
- **DADO** un cliente sin credencial JWT válida o con vínculo inactivo
- **CUANDO** intenta listar tarjetas, consultar consumos, bloquear, desbloquear o registrar aviso de viaje
- **ENTONCES** el sistema no realiza la operación y deriva al cliente a verificación (H3) con el mensaje de seguridad correspondiente

#### Escenario: Credencial expirada o revocada
- **DADO** una credencial expirada o revocada
- **CUANDO** un servicio de producto valida la credencial antes de una operación de Tarjetas
- **ENTONCES** el sistema informa que no es válida y no autoriza la operación, derivando al flujo de re-verificación en el canal (H3)

#### Escenario: Efectividad inmediata de la revocación en H2
- **DADO** que H2 revoca una credencial de sesión
- **CUANDO** se completa la revocación
- **ENTONCES** Tarjetas deja de responder operaciones de ese cliente en el mismo minuto operativo

#### Escenario: Ausencia de datos sensibles en mensajes
- **DADO** cualquier operación de Tarjetas
- **CUANDO** el sistema genera el mensaje al cliente
- **ENTONCES** el mensaje no contiene PAN completo, CVV ni fecha de vencimiento

## Preguntas abiertas

- Tope de cantidad y ventana de días para consumos recientes (regla 6 de la fuente): la fuente dice "tope de cantidad y ventana de días" sin valores. No bloquea el hito; el arquitecto/TL deben dejarlos parametrizables.
- Validación adicional requerida para el desbloqueo temporal (regla 4): la fuente no la define (ej. OTP, pregunta de seguridad). No bloquea el hito.
- Recomendaciones de seguridad exactas para el aviso de viaje (sección 8): la fuente no las detalla.
- Coordinación con H4 (Cuentas) si la política unifica límites de gasto (sección 9): la fuente la marca como opcional.
