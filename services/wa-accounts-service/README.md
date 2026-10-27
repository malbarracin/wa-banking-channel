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
La confirmación deberá usar el mismo protocolo de bloqueo y revalidar reglas y vencimiento.
Solo se admite la misma moneda: no hay productos multi-moneda habilitados.

Horario: TRANSFERENCIAS_HORARIO_INICIO (08:00 inclusivo), TRANSFERENCIAS_HORARIO_FIN (17:00
exclusivo), TRANSFERENCIAS_ZONA (America/Argentina/Buenos_Aires) y TRANSFERENCIAS_DIAS
(MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY). La vigencia se configura mediante
TRANSFERENCIAS_EXPIRACION_MINUTOS (5). Son valores iniciales configurables, no políticas bancarias definitivas.
Aplicar delivery/sql/002-transferencia-preparacion.sql sobre bases existentes antes del despliegue.
