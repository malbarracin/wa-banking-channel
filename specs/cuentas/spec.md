---
title: Cuentas — especificación
status: aprobada
source: https://app.notion.com/p/H4-Servicio-de-Cuentas-36875bb08ca7815e8642e555eebdfc6a
updated: 2026-10-27T13:32:57.162Z
---

# Cuentas — especificación

## Propósito

El Servicio de Cuentas (H4) permite al cliente del canal WhatsApp consultar sus cuentas, saldos y movimientos, y transferir dinero entre cuentas propias o a terceros previamente agendados, respetando límites y horarios del banco. Solo opera con credencial de sesión válida (H2) y vínculo activo en el canal (H3). El piloto prioriza la consulta de saldo; las transferencias son para producto.

## Fuera de alcance (de la fuente)

- Apertura o cierre de cuentas.
- Préstamos, plazos fijos u otros productos de crédito.
- Extractos en PDF o envío por correo.
- Agendar nuevos destinatarios desde WhatsApp (solo lista existente de terceros agendados).

## Actores

- **Cliente titular con JWT válido**: consulta cuentas, saldos, movimientos; transfiere dentro de límites.
- **Representante autorizado**: solo si la política del banco lo habilita y con los mismos límites.
- **Cliente sin JWT / vínculo inactivo**: recibe mensaje de derivación a verificación (H3).

## Requisitos

### Requisito: Consulta de cuentas del titular
El sistema DEBE listar las cuentas del titular autenticado (tipo, alias, moneda) y NO DEBE mostrar cuentas de otros titulares, cuando presenta una credencial de sesión válida y el vínculo está activo.

#### Escenario: Listado de cuentas del titular con credencial válida
- **DADO** un cliente con credencial de sesión válida y vínculo activo en el canal (H3)
- **CUANDO** solicita el listado de sus cuentas ("mis cuentas")
- **ENTONCES** el sistema devuelve solo las cuentas del titular autenticado con tipo, alias y moneda
- **Y** la operación queda registrada

#### Escenario: Rechazo del listado sin credencial válida
- **DADO** un cliente sin credencial JWT válida, o con credencial expirada o revocada
- **CUANDO** solicita el listado de sus cuentas
- **ENTONCES** el sistema no muestra cuentas ni saldos y deriva al cliente a verificación (H3)

### Requisito: Consulta de saldo de una cuenta
El sistema DEBE consultar el saldo disponible y el saldo contable de una cuenta (según el producto), siempre con credencial de sesión válida y vínculo activo.

#### Escenario: Consulta exitosa de saldo disponible y contable
- **DADO** una cuenta del titular autenticado y una credencial válida
- **CUANDO** se solicita el saldo de esa cuenta
- **ENTONCES** el sistema devuelve el saldo disponible y el saldo contable según el producto
- **Y** informa el saldo en el mensaje al cliente ("Tu cuenta {{alias_cuenta}} tiene saldo disponible {{monto}}.")

#### Escenario: Rechazo de saldo sin credencial válida o vínculo inactivo
- **DADO** una cuenta del titular y credencial inexistente, expirada o revocada, o vínculo inactivo
- **CUANDO** se solicita el saldo
- **ENTONCES** el sistema rechaza la consulta sin consultar productos y deriva o informa según corresponda

### Requisito: Consulta de movimientos de una cuenta
El sistema DEBE mostrar los últimos movimientos de una cuenta con filtros simples (fecha, tipo), dentro de una ventana máxima y con un tope de registros por mensaje, paginados con opción "ver más".

#### Escenario: Movimientos con filtros simples y tope de registros
- **DADO** una cuenta del titular, credencial válida y vínculo activo
- **CUANDO** se solicitan los últimos movimientos con filtro de fecha y/o tipo
- **ENTONCES** el sistema devuelve los movimientos dentro de la ventana máxima y con el tope de registros por mensaje, con opción "ver más"
- **Y** muestra el resumen paginado (ej. últimos 10)

#### Escenario: Rechazo de movimientos sin credencial válida
- **DADO** credencial inexistente, expirada o revocada
- **CUANDO** se solicitan movimientos
- **ENTONCES** el sistema rechaza la consulta sin consultar productos

### Requisito: Transferencia entre cuentas propias
El sistema DEBE ejecutar transferencias entre cuentas propias del mismo titular, validando límites diarios/mensuales y horarios operativos, exigiendo confirmación explícita antes de ejecutar, emitiendo comprobante resumido, y respetando la moneda de la cuenta origen salvo productos multi-moneda definidos.

#### Escenario: Transferencia propia exitosa con comprobante
- **DADO** cuentas propias del mismo titular, credencial válida, vínculo activo, monto dentro de límites y dentro de horario bancario
- **CUANDO** el cliente indica origen, destino y monto y confirma explícitamente (ej. "CONFIRMAR")
- **ENTONCES** el sistema ejecuta la transferencia sin débito indebido y envía comprobante en chat con fecha, origen, destino, monto y referencia
- **Y** la operación queda auditada

#### Escenario: Transferencia rechazada por límite superado sin débito
- **DADO** una transferencia propia cuyo monto o acumulado diario supera el límite del producto o del canal
- **CUANDO** se solicita la ejecución
- **ENTONCES** el sistema rechaza la operación sin efectuar débito y informa el límite superado

#### Escenario: Transferencia rechazada fuera de horario bancario
- **DADO** una transferencia propia solicitada fuera del horario bancario
- **CUANDO** se solicita la ejecución
- **ENTONCES** el sistema rechaza la operación con mensaje de horario permitido y no efectúa débito

#### Escenario: Doble confirmación para montos sobre umbral
- **DADO** una transferencia propia con monto sobre el umbral configurable de la política
- **CUANDO** se solicita la ejecución
- **ENTONCES** el sistema exige doble confirmación explícita antes de ejecutar

#### Escenario: Moneda de operación distinta a la de la cuenta origen
- **DADO** una transferencia cuya moneda no coincide con la cuenta origen y el producto no es multi-moneda definido
- **CUANDO** se solicita la ejecución
- **ENTONCES** el sistema rechaza la operación

### Requisito: Transferencia a tercero agendado
El sistema DEBE permitir transferencias a destinatarios de la lista de terceros agendados del banco, con las mismas validaciones y confirmación que las transferencias propias, y DEBE rechazar transferencias a destinatarios no agendados.

#### Escenario: Transferencia a tercero agendado exitosa
- **DADO** un destinatario de la lista de terceros agendados, credencial válida, vínculo activo y monto dentro de límites y horario
- **CUANDO** el cliente elige ese destinatario y confirma explícitamente
- **ENTONCES** el sistema ejecuta la transferencia y envía comprobante con referencia

#### Escenario: Rechazo de transferencia a destinatario no agendado
- **DADO** un destinatario que no está en la lista de terceros agendados
- **CUANDO** el cliente intenta transferir a ese destinatario por chat
- **ENTONCES** el sistema rechaza con mensaje claro y ofrece alternativa (sucursal/app)

### Requisito: Seguridad de acceso — credencial de sesión y vínculo
Toda operación de Cuentas exige credencial de sesión JWT válida y vínculo activo (H3): sin credencial se deriva a verificación; credencial expirada o revocada se rechaza de inmediato sin consultar productos; tras una revocación en H2, Cuentas deja de responder operaciones en el mismo minuto operativo.

#### Escenario: Operación sin JWT válido
- **DADO** un cliente sin credencial JWT válida o con vínculo inactivo
- **CUANDO** intenta consultar cuentas, saldos, movimientos o ejecutar una transferencia
- **ENTONCES** el sistema no realiza la operación y deriva al cliente a verificación (H3) con el mensaje de seguridad correspondiente

#### Escenario: Credencial expirada o revocada
- **DADO** una credencial expirada o revocada
- **CUANDO** un servicio de producto valida la credencial antes de una operación de Cuentas
- **ENTONCES** el sistema informa que no es válida y no autoriza la operación, derivando al flujo de re-verificación en el canal (H3)

#### Escenario: Efectividad inmediata de la revocación en H2
- **DADO** que H2 revoca una credencial de sesión
- **CUANDO** se completa la revocación
- **ENTONCES** Cuentas deja de responder operaciones de ese cliente en el mismo minuto operativo

## Preguntas abiertas

- Valores concretos de los límites diarios y mensuales por producto y canal (regla 3): la fuente los deja en "límites del producto y del canal" sin valores. No bloquea el hito; el arquitecto/TL deben dejarlos parametrizables hasta que el banco los defina.
- Horario bancario exacto y el mensaje de "horario permitido" (regla 4, criterio de aceptación 5): no definido en la fuente.
- Umbral configurable para la doble confirmación de montos altos (regla 5, criterio de aceptación 6): la fuente dice "umbral configurable" sin valor.
- Ventana máxima de movimientos (ejemplo: últimos 90 días) y tope de registros por mensaje (regla 6): la fuente da ejemplos, no valores cerrados.
- Definición de productos multi-moneda y qué monedas aplican para la regla de coincidencia de moneda (regla 7): no definido en la fuente.
- Formato y generación de la referencia de transferencia incluida en el comprobante (sección 8): no definido en la fuente.
