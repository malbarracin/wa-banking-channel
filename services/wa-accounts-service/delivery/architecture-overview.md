# Arquitectura — wa-accounts-service

## Qué es

Microservicio Spring Boot 3 (Java 21) que implementa el servicio de cuentas bancarias H4. 
Permite consultar cuentas y saldos, listar movimientos con paginación por cursor, gestionar agenda de terceros, preparar transferencias con reserva de saldo/límites y confirmar/ejecutar con soporte a doble confirmación. 
La validación de sesión se delega al servicio H2 (wa-credentials-service) mediante `X-Credencial-Sesion`.

Identidad Maven: `com.bcra:wa-accounts-service` · paquete base `com.bcra.accounts.ws`.

## Capas

```
com.bcra.accounts.ws/
├── AccountsApplication.java
├── config/              # Configuración (OpenApiConfig, rest/client, etc.)
├── exception/           # ErrorResponse, ErrorCode, GlobalExceptionHandler
├── cuenta/
│   ├── controller/      # CuentasController
│   ├── service/         # CuentaService
│   ├── repository/      # CuentaRepository
│   ├── mapper/          # CuentaMapper (MapStruct)
│   ├── entity/          # Cuenta, etc.
│   └── dto/             # Request/Response
├── movimiento/
│   ├── controller/      # Movimientos (si aplica)
│   ├── service/         # MovimientoService
│   ├── repository/      # MovimientoRepository
│   ├── entity/          # Movimiento
│   └── dto/             # DTOs de movimientos
├── tercero/
│   ├── controller/      # TercerosController
│   ├── service/         # TerceroService
│   ├── repository/      # TerceroRepository
│   ├── entity/          # Tercero
│   └── dto/             # DTOs
├── transferencia/
│   ├── controller/      # TransferenciaController
│   ├── service/         # TransferenciaService
│   ├── repository/      # TransferenciaRepository
│   ├── entity/          # Transferencia
│   ├── mapper/          # TransferenciaMapper (MapStruct)
│   └── dto/             # Request/Response
└── auditoria/
    ├── service/         # AuditoriaService (REQUIRES_NEW)
    └── repository/      # AuditoriaRepository
```

## Flujo de una petición

```
HTTP → Controller (@Valid) → Service (reglas + @Transactional) → Repository → Entity
                                    ↓                ↓
                                 Mapper ←—————— Mapper
                                    ↓
                                DTO/Response
```

## Integración con credenciales (H2)

- Cliente HTTP hacia wa-credentials-service (configurable: `CREDENTIALS_SERVICE_BASE_URL`, timeouts).
- En `test`/`dev` se usa H2 embebida para el servicio de credenciales según configuración del entorno.
- Validación por header `X-Credencial-Sesion` en endpoints protegidos.

## Reglas de negocio relevantes (H4)

### Consultas
- Listado de cuentas del titular autenticado por sesión.
- Movimientos paginados con cursor Base64 (incluye cuenta/rango/tipo/fecha/id). Parámetros configurables (`limits.movimientos.*`).
- Agenda de terceros: solo activos, validados contra H2, auditados.

### Transferencias — preparar (POST /api/v1/transferencias)
- Requiere sesión válida (`X-Credencial-Sesion`).
- Origen debe pertenecer al titular. Destino: cuenta propia o `terceroId` de agenda activa del titular.
- Solo misma moneda. No hay productos multi-moneda habilitados.
- Bloqueo `PESSIMISTIC_WRITE` de **todas las cuentas del titular** en orden estable (`CuentaRepository.bloquearDelTitular`).
- Se validan: horario (`TRANSFERENCIAS_HORARIO_INICIO/FIN`, zona, días), política en `limites_canal` (producto/canal/moneda), límites diarios/mensuales (acumulados incluyen ejecutadas + reservas vigentes `PREPARADA/CONFIRMADA`), saldo disponible (reservas vigentes comprometen saldo), vigencia destino.
- Reservas vigentes por titular/producto/moneda. Auditoría `PREPARAR_TRANSFERENCIA` en `REQUIRES_NEW`.
- Destinos no locales (sin adaptador interbancario) o no disponibles → **409** sin débito.
- Aplica `delivery/sql/002-transferencia-preparacion.sql` sobre bases existentes.

### Transferencias — confirmar/ejecutar (POST /api/v1/transferencias/{id}/confirmar)
- Body `{"confirmacion":"CONFIRMAR"}` literal exacto.
- Doble confirmación: primera confirmación → `200` con `estado CONFIRMADA` (sin comprobante); segunda → `200` con `estado EJECUTADA` y comprobante. Confirmación distinta interrumpe secuencia → `409` y vuelve a `PREPARADA` (requiere repetir ambas).
- Bloqueo en orden: cuentas del titular + destino local del tercero por UUID, luego la transferencia.
- Revalidación: horario, límites (excluyendo la propia reserva), saldo, moneda, vigencia del destinatario. Vencimiento original no se extiende.
- Ejecución atómica: saldos/movimientos (`DEBITO`/`CREDITO`), estado, auditoría exitosa. Reintento/ejecución repetida → `409` sin nuevo débito.
- Transferencias ajenas/inexistentes → `404`. Sesión inválida → `401`.

## Persistencia y perfiles

| Perfil | BD | `ddl-auto` | Notas |
|--------|-----|------------|-------|
| `default` | PostgreSQL | `validate` | Requiere `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| `dev` | PostgreSQL | `update` | Desarrollo local |
| `docker` | PostgreSQL (Compose) | `update` | Configurado en `services/wa-accounts-service` |
| `test` | H2 in-memory | `create-drop` | Tests con H2; validación de credenciales vía H2 según configuración |

## Configuración relevante

| Variable/Propiedad | Descripción | Default |
|--------------------|-------------|---------|
| `PORT` | Puerto del servicio | `8084` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión PostgreSQL | - |
| `CREDENTIALS_SERVICE_BASE_URL` | Base URL wa-credentials-service | `http://localhost:8082` |
| `CREDENTIALS_CONNECT_TIMEOUT` | Timeout conexión | `2s` |
| `CREDENTIALS_READ_TIMEOUT` | Timeout lectura | `5s` |
| `TRANSFERENCIAS_HORARIO_INICIO` | Inicio horario habilitado (inclusivo) | `08:00` |
| `TRANSFERENCIAS_HORARIO_FIN` | Fin horario habilitado (exclusivo) | `17:00` |
| `TRANSFERENCIAS_ZONA` | Zona horaria | `America/Argentina/Buenos_Aires` |
| `TRANSFERENCIAS_DIAS` | Días habilitados | `MONDAY,...,FRIDAY` |
| `TRANSFERENCIAS_EXPIRACION_MINUTOS` | Vigencia de reserva | `5` |
| `limits.movimientos.*` | Límites de paginación de movimientos | Configurables |

## OpenAPI / Swagger

- UI: `/swagger-ui.html`
- JSON: `/v3/api-docs` | YAML: `/v3/api-docs.yaml`
- `./mvnw -q -B verify` regenera `delivery/openapi/openapi.json` y `openapi.yaml` (OpenApiExportTest). Swagger UI sin credencial; operaciones requieren `X-Credencial-Sesion` validado con H2.

## Alcance H4 / fuera de alcance

Incluye: consultas de cuentas/saldos/movimientos, terceros agendados, preparar/confirmar transferencias con doble confirmación, auditoría, contrato de errores.

Fuera: adaptador de liquidación interbancaria (solo cuentas locales de terceros), productos multi-moneda, reactivación/extensión automática de vigencia, otros canales.
