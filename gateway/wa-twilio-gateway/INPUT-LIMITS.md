# Protección de entrada HTTP

`WebhookInputFilter` protege ambos POST de Twilio antes de que Spring MVC
convierta el cuerpo a `byte[]`. Conserva las cuotas de 120 solicitudes/minuto
por IP remota para inbound y global para callbacks. Rechaza con 429 antes de
leer el cuerpo al agotar la cuota; no confía en headers de IP reenviada.

Con Content-Length mayor de 16384 responde 413 sin abrir el stream. Para
longitud desconocida (incluido chunked) o declarada incorrectamente, lee como
máximo 16385 bytes y rechaza con 413 si supera 16384. Solo un cuerpo aceptado
se entrega al controlador. Los filtros de correlación y métricas envuelven
esta protección y observan sus rechazos.

Las cuotas son locales a cada instancia. El proxy debe configurar también
límites de conexiones, tiempos de lectura y cuotas compartidas entre réplicas.

`WebhookInputFilterTest` verifica ambos endpoints, rechazo sin lectura, límite
de lectura con un stream de 1 MiB sin longitud, cuota previa a la lectura y
preservación de un cuerpo de exactamente 16384 bytes. Las pruebas MVC de ambos
controladores incluyen el filtro para comprobar firmas y respuestas a través
de la protección.

`WebhookBodyLimitMvcTest` atraviesa el filtro real y Spring MVC con ambos
controladores y un stream de 1 MiB generado sin almacenar el cuerpo completo.
Comprueba 413 y cero bytes leídos con longitud declarada excesiva, y exactamente
16385 bytes con longitud desconocida (chunked) o declarada menor que la real.
También verifica que no se invocan validación de firma, anti-replay ni publicación.
