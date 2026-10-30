# wa-cards-service

Base del servicio de tarjetas H5. Java 21, Spring Boot 3.5.16 y PostgreSQL.
Identidad `com.bcra.cards.ws` confirmada por tech-lead para TASK-103, según
`specs/tarjetas/arquitectura.md:7`. Puerto por defecto: 8086.

## Compilar y verificar

Desde esta carpeta: `./mvnw -q compile` y `./mvnw clean verify`.
En Windows usar `.\mvnw.cmd`. Las pruebas usan H2 y comprueban el arranque
del contexto y el contrato HTTP de errores. JaCoCo exige 80% de líneas;
se excluye solamente el punto de entrada, que delega en Spring.

## Ejecutar

Configurar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`, luego ejecutar
`./mvnw spring-boot:run`. DB_URL por defecto:
`jdbc:postgresql://localhost:5432/wa_cards`.
El perfil `dev` (por defecto) actualiza el esquema.
Para producción definir `SPRING_PROFILES_ACTIVE=prod`: valida un esquema
previamente provisionado. El perfil `test` usa H2, disponible solo en pruebas.
Salud: `/actuator/health`; información: `/actuator/info`.

Las funcionalidades se ubican en `tarjeta/{controller,service,repository,mapper,entity,dto}`.

## Consumos recientes

`GET /api/v1/tarjetas/{tarjetaId}/consumos` requiere `X-Credencial-Sesion`, validada con H2
antes de consultar productos. Una tarjeta inexistente o ajena devuelve 404; una sesión inválida, 401.
Los parámetros opcionales `dias` y `limit` deben ser positivos y se acotan a
`cards.consumos.ventana-dias` (90) y `cards.consumos.tope-por-mensaje` (10).
Se pueden configurar con `CARDS_CONSUMOS_VENTANA_DIAS` y `CARDS_CONSUMOS_TOPE_POR_MENSAJE`.
La respuesta contiene `consumos`, `tieneMas` y `cursor`. Para continuar, reenviar el cursor
con la misma tarjeta y ventana solicitada; el cursor final es nulo. El orden es fecha e ID descendentes.
Cada página excluye fechas futuras y consumos fuera de la ventana reciente, sin PAN, CVV ni vencimiento.

## Bloqueo definitivo

`POST /api/v1/tarjetas/{tarjetaId}/bloquear/definitivo` requiere `X-Credencial-Sesion`
y un cuerpo con `tarjetaId`, `motivo` (`ROBO` o `EXTRAVIO`), `confirmarPrimero`
y `confirmarSegundo` (ambos exactamente `CONFIRMAR`). Path y cuerpo deben coincidir.
Al iniciar el flujo, enviar las confirmaciones omitidas: devuelve 409 `CONFLICT`
con la advertencia de irreversibilidad por WhatsApp antes de solicitar las dos confirmaciones,
sin modificar la tarjeta. El canal debe mostrar esa advertencia antes de recogerlas.
Con ambas confirmaciones devuelve 200 con `tarjeta` y `mensaje`, que sugiere reposición
por canales oficiales. Persiste bloqueo, desactivación y auditoría en una sola transacción.
No se permite repetir el bloqueo definitivo ni desbloquearlo por chat (409).
