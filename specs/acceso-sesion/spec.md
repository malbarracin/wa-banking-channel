# Acceso y Sesión — especificación

## Propósito
El Servicio de Acceso y Sesión (H2) gestiona la credencial de sesión del cliente en el canal WhatsApp: la emite cuando el canal (H3) confirma que la identidad fue verificada y el vínculo quedó activo, la renueva mientras ese vínculo siga activo y sin señal de riesgo, y la revoca de inmediato ante bloqueo, desvinculación o fraude. Es el servicio que permite a Cuentas, Tarjetas y Seguros (H4, H5, H6) saber que quien opera por WhatsApp está autenticado, sin tener que confiar únicamente en que llegó un mensaje desde ese número.

## Fuera de alcance (de la fuente)
- Alta de usuario bancario (H1) — el cliente debe existir previamente en H1.
- El flujo conversacional de verificación de identidad y la gestión del vínculo número–cliente (H3).
- Operación de productos: cuentas, tarjetas, seguros (H4, H5, H6) — estos servicios solo validan la credencial.
- Detalle técnico del formato de la credencial (algoritmo, estructura, firma): la fuente da solo reglas de negocio sobre cuándo se emite, renueva y revoca, no el formato — es una decisión de arquitectura.

## Actores
- **Canal WhatsApp (H3)**: solicita la emisión de la credencial tras verificar identidad y activar el vínculo; solicita la revocación ante bloqueo, desvinculación o sospecha de fraude detectada en el canal.
- **Servicios de productos (H4, H5, H6)**: validan que una credencial presentada es vigente y corresponde al cliente, antes de autorizar cualquier operación.
- **Banco (política/riesgo)**: puede forzar la revocación de una credencial puntual o de forma masiva, y puede impedir la renovación automática ante una señal de riesgo.

## Requisitos

### Requisito: Emisión de credencial tras verificación exitosa
El sistema DEBE emitir la credencial de sesión únicamente cuando el canal (H3) confirma que la identidad fue verificada y el vínculo quedó activo, y NO DEBE emitir ninguna credencial sin esa confirmación.

#### Escenario: Emisión tras verificación e identidad confirmadas
- **DADO** que el canal (H3) confirma identidad verificada y vínculo activo para un cliente
- **CUANDO** se solicita la emisión de la credencial de sesión
- **ENTONCES** el sistema emite la credencial con una vigencia definida
- **Y** el canal queda habilitado para permitir que el cliente opere productos

#### Escenario: Solicitud de emisión sin verificación confirmada
- **DADO** que no existe una confirmación de verificación de identidad exitosa y vínculo activo para ese cliente
- **CUANDO** se solicita la emisión de una credencial de sesión
- **ENTONCES** el sistema rechaza la solicitud
- **Y** no emite ninguna credencial

### Requisito: Una credencial activa por vínculo número–cliente
El sistema NO DEBE mantener más de una credencial activa a la vez para el mismo vínculo número–cliente: una nueva emisión para un vínculo que ya tiene una credencial activa invalida la anterior.

#### Escenario: Nueva emisión reemplaza la credencial activa anterior
- **DADO** un vínculo número–cliente con una credencial activa
- **CUANDO** se emite una nueva credencial para ese mismo vínculo (por ejemplo, tras una re-verificación)
- **ENTONCES** la credencial anterior queda invalidada
- **Y** solo la nueva credencial es válida para operar

### Requisito: Renovación de la credencial sin repetir la verificación completa
El sistema DEBE renovar automáticamente la credencial próxima a expirar mientras el vínculo siga activo y sin señal de riesgo, sin exigirle al cliente repetir el proceso completo de verificación de identidad.

#### Escenario: Renovación automática sin señal de riesgo
- **DADO** una credencial próxima a expirar cuyo vínculo sigue activo y sin señal de riesgo asociada
- **CUANDO** corresponde renovarla
- **ENTONCES** el sistema la renueva sin requerir que el cliente repita la verificación de identidad
- **Y** el cliente no percibe interrupción en su operatoria

#### Escenario: Señal de riesgo impide la renovación automática
- **DADO** una credencial próxima a expirar con una señal de riesgo asociada al vínculo, según la política del banco
- **CUANDO** correspondería renovarla automáticamente
- **ENTONCES** el sistema no la renueva de forma automática
- **Y** deriva al cliente al flujo de re-verificación de identidad en el canal (H3)

### Requisito: Renovación debe entregar token sustituto sin invalidar el anterior prematuramente
Al renovar una credencial, el sistema DEBE entregar el token sustituto a quien corresponda (H3/consumidor autorizado) de forma confiable antes o de modo que no se interrumpa la operatoria; la rotación NO DEBE invalidar la credencial en uso antes de que el sustituto esté disponible. Si la entrega del token sustituye no puede garantizarse, el mecanismo de renovación DEBE fallar de forma segura o coordinarse con H3.

#### Escenario: Renovación exitosa con entrega del token sustituto
- **DADO** una credencial próxima a expirar y renovación automática habilitada
- **CUANDO** se ejecuta la renovación
- **ENTONCES** se genera el nuevo token y se entrega al canal/consumidor autorizado de forma confiable
- **Y** el token anterior se revoca o deja de ser válido solo tras garantizar la entrega/disponibilidad del sustituto según el contrato acordado
- **Y** la operación no interrumpe la operatoria del cliente

### Requisito: Revocación inmediata de la credencial
El sistema DEBE revocar la credencial de inmediato ante un bloqueo, una desvinculación o una señal de fraude, a pedido del canal (H3) o por decisión de política/riesgo del banco, y esa revocación DEBE ser efectiva para cualquier operación posterior desde el mismo instante.

#### Escenario: Revocación solicitada por el canal (bloqueo o desvinculación)
- **DADO** que el canal (H3) informa un bloqueo o una desvinculación del vínculo
- **CUANDO** solicita la revocación de la credencial asociada
- **ENTONCES** el sistema la revoca de inmediato
- **Y** cualquier operación posterior con esa credencial queda rechazada desde ese momento

#### Escenario: Revocación por decisión de política o riesgo del banco
- **DADO** que el banco decide revocar una credencial puntual, o de forma masiva, por política o riesgo (incluida una sospecha de fraude)
- **CUANDO** se aplica esa decisión
- **ENTONCES** el sistema revoca de inmediato la o las credenciales alcanzadas
- **Y** cualquier operación posterior con esas credenciales queda rechazada desde ese momento

### Requisito: Autenticación y autorización de llamadas internas
Las operaciones internas privilegiadas (emisión, revocación individual, revocación masiva, validación con fines administrativos) DEBEN estar protegidas por autenticación servicio-a-servicio y autorización por actor. El sistema NO DEBE confiar en flags provistos por el cuerpo de la solicitud (ej. inculoActivo, identidadVerificada) como prueba de identidad/autorización. H3 debe estar autenticado para emitir/revocar credenciales; el actor banco debe estar autenticado/autorizado para revocación masiva.

#### Escenario: Emisión rechazada sin autenticación/autorización de H3
- **DADO** una solicitud de emisión sin credenciales de servicio válidas de H3
- **CUANDO** se intenta emitir
- **ENTONCES** el sistema rechaza la solicitud
- **Y** no emite credencial

#### Escenario: Revocación masiva solo por actor banco autorizado
- **DADO** una solicitud a /revocacion-masiva sin autenticación/autorización del actor banco
- **CUANDO** se intenta ejecutar
- **ENTONCES** el sistema rechaza la solicitud
- **Y** no revoca credenciales

### Requisito: Validación de credencial para los servicios de productos
El sistema DEBE permitir a los servicios de productos (H4, H5, H6) validar si una credencial presentada es vigente y corresponde al cliente, y NO DEBE permitir que una operación continúe con una credencial inválida, expirada o revocada.

#### Escenario: Validación de una credencial vigente
- **DADO** una credencial de sesión vigente y no revocada
- **CUANDO** un servicio de producto la valida antes de autorizar una operación
- **ENTONCES** el sistema confirma que es vigente y a qué cliente corresponde
- **Y** el servicio de producto puede continuar con la operación

#### Escenario: Validación de una credencial inválida, expirada o revocada
- **DADO** una credencial inexistente, expirada o revocada
- **CUANDO** un servicio de producto la valida antes de autorizar una operación
- **ENTONCES** el sistema informa que no es válida
- **Y** la operación no se autoriza
- **Y** el cliente es derivado al flujo de re-verificación en el canal (H3)

### Requisito: Validación del body en endpoint de validar
El endpoint de validación DEBE validar el body con @Valid. El campo credencial DEBE ser obligatorio/no vacío; ante un body vacío o inválido el sistema DEBE responder con error de validación (VALIDATION_ERROR) con status 400, no 401.

#### Escenario: Body vacío en /validar
- **DADO** una solicitud con body vacío {} sin credencial
- **CUANDO** se invoca el endpoint
- **ENTONCES** el sistema responde 400 con contrato VALIDATION_ERROR indicando el campo requerido
- **Y** no se intenta validar una credencial

### Requisito: La credencial nunca se expone en texto al cliente
El sistema NO DEBE incluir el contenido de la credencial de sesión en ningún mensaje visible para el cliente en el canal WhatsApp.

#### Escenario: Emisión o renovación sin exponer la credencial
- **DADO** que el sistema emite o renueva una credencial de sesión para un cliente
- **CUANDO** el canal confirma la operación al cliente
- **ENTONCES** el mensaje al cliente no contiene el contenido de la credencial

### Requisito: Registro de emisiones y revocaciones
El sistema DEBE registrar toda emisión y toda revocación de credencial de sesión, y NO DEBE incluir secretos ni el contenido de la credencial en esos registros.

#### Escenario: Registro de auditoría de una emisión
- **DADO** que el sistema emite una credencial de sesión
- **CUANDO** la emisión se concreta
- **ENTONCES** queda un registro de auditoría de esa emisión, asociado al vínculo número–cliente
- **Y** el registro no contiene el contenido de la credencial ni otros secretos

#### Escenario: Registro de auditoría de una revocación
- **DADO** que el sistema revoca una credencial de sesión, por el motivo que sea
- **CUANDO** la revocación se concreta
- **ENTONCES** queda un registro de auditoría de esa revocación, asociado al vínculo número–cliente y al motivo
- **Y** el registro no contiene el contenido de la credencial ni otros secretos

## Preguntas abiertas
- Vigencia exacta de la credencial de sesión (duración concreta): la fuente dice "vigencia acordada" sin un valor. No bloquea el resto del hito; el arquitecto/TL deben dejarlo parametrizable, siguiendo el mismo criterio que las políticas pendientes de usuarios/spec y canal-whatsapp/spec.
- Umbral o ventana que determina cuándo una credencial está "próxima a expirar" y dispara la renovación automática: no está definido en la fuente.
- Qué constituye exactamente una "señal de riesgo" que impide la renovación automática y fuerza la re-verificación: la fuente lo deja en "política de riesgo" sin criterios concretos.
- Qué constituye exactamente una "señal de fraude" que dispara la revocación inmediata, y de dónde proviene esa señal (canal, motor de riesgo del banco, ambos): no está definido en la fuente.
- Alcance y disparadores exactos de una "revocación masiva" por el banco (todas las credenciales, un segmento de clientes, un criterio de riesgo específico): la fuente solo menciona que el banco "puede forzar revocación masiva o por cliente", sin más detalle.
- Formato técnico de la credencial (JWT u otro mecanismo): la fuente lo excluye explícitamente de su alcance funcional ("detalle técnico del formato de la credencial") y queda como decisión de arquitectura; se registra aquí para que el arquitecto no lo de por implícito.
