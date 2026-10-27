# wa-accounts-service

Base del servicio de cuentas H4: Java 21, Spring Boot 3, JPA y PostgreSQL.
Identidad `com.bcra.accounts.ws` (from tech-lead confirmation — pattern <group_id>.<dominio>.ws).
`project.yaml` conserva la identidad de H1.

Desde esta carpeta: `./mvnw clean verify` compila, ejecuta tests con H2 en memoria y verifica JaCoCo ≥80%.
En PowerShell usar `./mvnw.cmd clean verify`.

Para iniciar: configurar PostgreSQL con la base `wa_accounts` y las variables `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`; ejecutar `./mvnw spring-boot:run`. Puerto por defecto: 8084 (`PORT`).
El esquema se valida por defecto; las entidades y migraciones corresponden a las tareas de dominio.
Actuator: `/actuator/health`; Swagger UI: `/swagger-ui.html`.

La validación de credenciales usa H2 (servicio wa-credentials-service), configurable mediante
`CREDENTIALS_SERVICE_BASE_URL` (http://localhost:8082), `CREDENTIALS_CONNECT_TIMEOUT` (2s) y
`CREDENTIALS_READ_TIMEOUT` (5s). H2 en memoria es únicamente la base de datos de tests.
POST /api/v1/transferencias recibe X-Credencial-Sesion y un JSON con cuentaOrigen, exactamente uno
de cuentaDestino (propia) o terceroId (agenda activa del titular), monto positivo con dos decimales como
máximo, moneda y concepto opcional. Devuelve 201 con transferenciaId, estado PREPARADA, resumen,
requiereDobleConfirmacion y expiraEn (instante UTC); no debita ni genera movimientos.

La política se configura en limites_canal: producto CA/CC, canal WHATSAPP, moneda, limite_diario,
limite_mensual y umbral_doble_confirmacion. Si no existe una política compatible se rechaza la
preparación. Los acumulados son por titular, producto y moneda e incluyen ejecutadas y reservas
vigentes (PREPARADA/CONFIRMADA); estas últimas también comprometen saldo de la cuenta origen.
Las cuentas del titular se bloquean con PESSIMISTIC_WRITE en orden estable durante la preparación.
La confirmación usa el mismo orden de bloqueo y revalida reglas y vencimiento.
Solo se admite la misma moneda: no hay productos multi-moneda habilitados.

Horario: TRANSFERENCIAS_HORARIO_INICIO (08:00 inclusivo), TRANSFERENCIAS_HORARIO_FIN (17:00
exclusivo), TRANSFERENCIAS_ZONA (America/Argentina/Buenos_Aires) y TRANSFERENCIAS_DIAS
(MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY). La vigencia se configura mediante
TRANSFERENCIAS_EXPIRACION_MINUTOS (5). Son valores iniciales configurables, no políticas bancarias definitivas.
Aplicar delivery/sql/002-transferencia-preparacion.sql sobre bases existentes antes del despliegue.

POST /api/v1/transferencias/{id}/confirmar recibe X-Credencial-Sesion y el JSON
`{"confirmacion":"CONFIRMAR"}` (literal exacto). Devuelve 200 con estado EJECUTADA, fechaEjecucion,
referencia UUID y comprobante (fecha, origen, destino, monto, moneda, referencia).
Si requiere doble confirmación, el primer pedido devuelve 200 con estado CONFIRMADA y sin comprobante;
el segundo CONFIRMAR ejecuta. Una confirmación distinta interrumpe la secuencia, devuelve 409 y exige
nuevamente las dos confirmaciones. El vencimiento original no se extiende.
Saldos disponibles/contables, movimientos DEBITO/CREDITO, estado y auditoría exitosa se confirman
en una sola transacción. Se bloquean las cuentas del titular y el destino local del tercero en orden UUID,
y después la transferencia; una ejecución repetida devuelve 409 sin generar otro débito.
Se revalidan horario, límites (excluyendo la propia reserva), saldo, moneda y vigencia del destinatario.
Las transferencias ajenas o inexistentes devuelven 404; sesión inválida, 401.
Los terceros deben tener cuenta destino local: no existe un adaptador de liquidación interbancaria;
un destino no disponible se rechaza con 409 sin debitar fondos.

## OpenAPI y Swagger

Con el servicio iniciado, Swagger UI está disponible en
`http://localhost:8084/swagger-ui.html` (puerto configurable mediante `PORT`).
La UI y la especificación no requieren credencial; las operaciones de cuentas sí validan
`X-Credencial-Sesion` con H2 en cada solicitud.

La especificación se consulta en `/v3/api-docs` (JSON) o `/v3/api-docs.yaml` (YAML).
`./mvnw -q -B verify` regenera `delivery/openapi/openapi.json` y `openapi.yaml`
y verifica los seis endpoints, los ejemplos de éxito/error y el acceso a Swagger.
Los ejemplos contienen datos ficticios: usar cuentas del titular, un tercero de su agenda,
el identificador devuelto al preparar y una credencial H2 vigente para ejecutar solicitudes.
Para continuar movimientos, copiar el cursor de la respuesta anterior y conservar los filtros.
