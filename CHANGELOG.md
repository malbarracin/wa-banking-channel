# Cambios

## [0.1.0] - 2026-10-31

### Agregado

- Servicio de usuarios bancarios: alta, consulta por tipo y número de documento, actualización de datos permitidos y
  cambio de estado, con historial de auditoría (#2)
- Vínculo de WhatsApp: aceptación de términos, verificación de identidad, vinculación y re-vinculación, consulta de
  perfil y preferencias, bloqueo y desvinculación, e historial resumido para soporte (#3)
- Acceso y sesión: emisión, validación, renovación automática y revocación de credenciales —individual y masiva— con
  consulta del historial (#4)
- Cuentas: consulta de cuentas, saldos y movimientos, terceros agendados y preparación y confirmación de
  transferencias con límites (#6)
- Seguros: consulta de pólizas y coberturas, y registro y seguimiento de reclamos (#7)
- Tarjetas: consulta de tarjetas y consumos recientes, bloqueo temporal, desbloqueo, bloqueo definitivo y aviso de
  viaje (#11)
- Plataforma de eventos Kafka con contratos versionados, reintentos, cola de mensajes fallidos y trazabilidad por
  conversación (#8)
- Gateway de WhatsApp con Twilio: recepción de mensajes con validación de firma y protección anti-replay, avisos de
  estado de entrega y envío de respuestas desde el backend (#9)
- Orquestador de conversaciones: estado y orden por conversación, deduplicación de mensajes recibidos, flujos de alta
  y re-verificación con compensaciones y envío de respuestas idempotente (#10)
- Capa MCP con el catálogo de capacidades de usuarios, acceso, canal, cuentas, tarjetas y seguros para el agente, con
  enmascarado de datos personales y auditoría de cada llamada (#12)
- Base de conocimiento: ingesta de contenidos aprobados con control de datos personales, publicación versionada y
  búsqueda híbrida con citas y nivel de confianza, separando contenido público e interno (#13)
- Agente conversacional con Google ADK: atención por WhatsApp con consultas a la base de conocimiento y a los
  servicios, memoria de sesión, idempotencia por mensaje y escalamiento a una persona (#14)

### Corregido

- Las altas de usuario rechazadas por validación quedan auditadas y la consulta completa respeta el perfil de quien
  consulta (#15)
- El alta de usuario devuelve en la respuesta la ubicación del recurso creado (#15)
- Aceptar los términos dos veces ya no genera vínculos pendientes duplicados y las acciones sensibles del vínculo
  exigen un token validado (#15)
- En acceso y sesión, la renovación entrega el token renovado al canal, la emisión y la revocación identifican al
  servicio que llama, la revocación masiva exige un actor del banco y la validación rechaza pedidos vacíos (#15)

### Seguridad

- Los webhooks de Twilio rechazan los envíos que superan el tamaño máximo antes de leer el cuerpo, incluso con envíos
  fragmentados o con una longitud declarada menor a la real (#16)
- El despliegue de usuarios ya no usa una contraseña de base de datos por defecto: el chart de Helm no arranca sin
  `DB_PASSWORD` (#15)
