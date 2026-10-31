---
title: Cuentas — arquitectura
status: borrador
source: null
updated: 2026-10-27T13:34:05.674Z
---

# Arquitectura — Servicio de Cuentas (H4)

## Base

- **Servicio nuevo** (no existe `pom.xml` todavía): `services/wa-accounts-service`, análogo a `wa-users-service` (H1), `wa-credentials-service` (H2) y `wa-channel-service` (H3).
- Maven groupId: **com.bcra** (de `project.yaml` → `java.group_id`).
- Base package: **com.bcra.accounts.ws** — propuesto, siguiendo el patrón `<group_id>.<dominio>.ws`. `project.yaml` solo define identidad para H1; este valor se confirma en el bootstrap (tech-lead actualiza `project.yaml`).
- Artifact id propuesto: `wa-accounts-service`.
- Módulo de dominio: **cuenta** (agregado central: cuenta, saldo, movimiento, transferencia, tercero agendado, límites).
- Puerto propuesto: **8084** (H1 8080, H3 8081, H2 8082; evita choque en local).
- Persistencia: PostgreSQL en runtime, H2 en test — base de datos propia (`wa_accounts`).
- Integraciones salientes (síncronas, REST, sin service discovery):
  - **H2 (wa-credentials-service)**: validar credencial de sesión (`POST /api/v1/credenciales/validar`). Base URL configurable (`clients.wa-credentials-service.base-url`).
  - H4 no llama a H1 ni H3 directamente: la identidad del cliente viene del JWT validado por H2; el vínculo activo se garantiza porque H2 revoca la credencial al desvincular/bloquear.

## Árbol de paquetes

```
com.bcra.accounts.ws/
├── AccountsApplication.java
├── cuenta/
│   ├── controller/      CuentaController, MovimientoController, TransferenciaController, TerceroController
│   ├── service/         CuentaService, SaldoService, MovimientoService, TransferenciaService,
│   │                    TerceroService, LimiteService, HorarioService, AuditoriaCuentaService
│   ├── repository/      CuentaRepository, MovimientoRepository, TransferenciaRepository,
│   │                    TerceroAgendadoRepository, LimiteRepository, AuditoriaCuentaRepository
│   ├── mapper/          CuentaMapper, MovimientoMapper, TransferenciaMapper (MapStruct)
│   ├── entity/          Cuenta, Moneda, TipoCuenta, Movimiento, TipoMovimiento, Transferencia,
│   │                    EstadoTransferencia, TerceroAgendado, LimiteCanal, AuditoriaCuenta, AuditoriaAccion
│   ├── dto/             CuentaResponse, SaldoResponse, MovimientoResponse, MovimientosPageResponse,
│   │                    TransferenciaRequest, TransferenciaPreparadaResponse, ConfirmarTransferenciaRequest,
│   │                    TransferenciaResponse, ComprobanteResponse, TerceroResponse
│   ├── client/          CredencialesServiceClient (+ dto ValidarCredencialResponse)
│   └── exception/       CuentaNoEncontradaException, SaldoInsuficienteException, LimiteSuperadoException,
│                        FueraDeHorarioException, MonedaNoCoincideException, TerceroNoAgendadoException,
│                        TransferenciaNoConfirmadaException, DobleConfirmacionRequeridaException
├── config/              OpenApiConfig, RestClientConfig (timeouts + base URLs de H2)
└── exception/           ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler,
                        NotFoundException, ConflictException, UnauthorizedException
```

## Entidades

- **cuenta.entity.Cuenta**: id (UUID), numeroCuenta (string único), alias (string), tipo (`TipoCuenta`: CA, CC, etc.), moneda (`Moneda`: ARS, USD, ...), titularDocumentoTipo + titularDocumentoNumero (referencia al cliente, sin FK física), saldoDisponible (BigDecimal), saldoContable (BigDecimal), activa (boolean), createdAt/updatedAt.
- **cuenta.entity.Movimiento**: id (UUID), cuenta (FK lógica a Cuenta), fecha (Instant), tipo (`TipoMovimiento`: DEBITO, CREDITO), monto (BigDecimal), moneda, descripcion, referencia (string, nullable), saldoResultante (BigDecimal).
- **cuenta.entity.Transferencia**: id (UUID), cuentaOrigenId, cuentaDestinoId (nullable si tercero), terceroId (nullable), monto, moneda, estado (`EstadoTransferencia`: PREPARADA, CONFIRMADA, EJECUTADA, RECHAZADA, EXPIRADA), referencia (string), fechaCreacion, fechaEjecucion, motivoRechazo (nullable), dobleConfirmacionRequerida (boolean).
- **cuenta.entity.TerceroAgendado**: id (UUID), titularDocumentoTipo + titularDocumentoNumero, alias, numeroCuentaDestino, bancoDestino (nullable), activo (boolean).
- **cuenta.entity.LimiteCanal**: id, producto (string), canal (string, ej. WHATSAPP), limiteDiario (BigDecimal), limiteMensual (BigDecimal), umbralDobleConfirmacion (BigDecimal), moneda.
- **cuenta.entity.AuditoriaCuenta**: id, accion (`AuditoriaAccion`: LISTAR_CUENTAS, CONSULTAR_SALDO, LISTAR_MOVIMIENTOS, PREPARAR_TRANSFERENCIA, CONFIRMAR_TRANSFERENCIA, EJECUTAR_TRANSFERENCIA, RECHAZAR_TRANSFERENCIA), resultado (EXITOSO/RECHAZADO), numeroWhatsapp, documentoTipo, documentoNumero, cuentaId (nullable), transferenciaId (nullable), motivo (nullable), fecha.

## DTOs

- `CuentaResponse(numeroCuenta, alias, tipo, moneda)`
- `SaldoResponse(numeroCuenta, alias, saldoDisponible, saldoContable, moneda)`
- `MovimientoResponse(fecha, tipo, monto, moneda, descripcion, referencia, saldoResultante)`
- `MovimientosPageResponse(movimientos: List<MovimientoResponse>, tieneMas: boolean, cursor: String?)`
- `TransferenciaRequest(cuentaOrigen, cuentaDestino?, terceroId?, monto, moneda, concepto?)`
- `TransferenciaPreparadaResponse(transferenciaId, resumen, requiereDobleConfirmacion, expiraEn)`
- `ConfirmarTransferenciaRequest(transferenciaId, confirmacion)` — confirmacion = "CONFIRMAR"
- `TransferenciaResponse(transferenciaId, estado, fechaEjecucion, referencia, comprobante)`
- `ComprobanteResponse(fecha, origen, destino, monto, moneda, referencia)`
- `TerceroResponse(id, alias, numeroCuentaDestino, bancoDestino)`

## Endpoints

Todos bajo `/api/v1` (sin versionado por ahora — `api.versioning` no está habilitado en `project.yaml`).

| Método y path | Request | Éxito | Errores (HTTP — `code`) |
|---|---|---|---|
| `GET /api/v1/cuentas` | — (credencial por header `X-Credencial-Sesion`) | 200 `List<CuentaResponse>` | 401 `UNAUTHORIZED`; 403 `FORBIDDEN` (vínculo inactivo) |
| `GET /api/v1/cuentas/{numeroCuenta}/saldo` | — | 200 `SaldoResponse` | 401 `UNAUTHORIZED`; 404 `NOT_FOUND` (cuenta no del titular); 403 `FORBIDDEN` |
| `GET /api/v1/cuentas/{numeroCuenta}/movimientos` | query: `desde`, `hasta`, `tipo`, `limit`, `cursor` | 200 `MovimientosPageResponse` | 401 `UNAUTHORIZED`; 400 `VALIDATION_ERROR`/`BAD_REQUEST`; 404 `NOT_FOUND` |
| `POST /api/v1/transferencias` | `TransferenciaRequest` | 201 `TransferenciaPreparadaResponse` | 400 `VALIDATION_ERROR`/`BAD_REQUEST`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND`; 409 `CONFLICT` (límite, horario, moneda, tercero no agendado) |
| `POST /api/v1/transferencias/{id}/confirmar` | `ConfirmarTransferenciaRequest` | 200 `TransferenciaResponse` | 400 `VALIDATION_ERROR`; 401 `UNAUTHORIZED`; 404 `NOT_FOUND`; 409 `CONFLICT` (expirada, ya ejecutada, doble confirmación pendiente) |
| `GET /api/v1/terceros` | — | 200 `List<TerceroResponse>` | 401 `UNAUTHORIZED` |

Notas:
- La credencial se recibe por header `X-Credencial-Sesion` (JWT de H2). H4 la valida con H2 antes de cualquier operación; si inválida/expirada/revocada → 401 `UNAUTHORIZED` y no consulta productos.
- Transferencia en dos pasos: preparar (valida límites, horario, moneda, tercero agendado, saldo) → devuelve resumen y expiración; confirmar ejecuta el débito/crédito y emite comprobante. Montos sobre el umbral exigen dos confirmaciones consecutivas.
- Movimientos: ventana máxima configurable (default 90 días) y tope por mensaje configurable (default 10); paginación por cursor.

## Mappers

- `cuenta.mapper.CuentaMapper` (MapStruct): `Cuenta` ↔ `CuentaResponse`/`SaldoResponse`.
- `cuenta.mapper.MovimientoMapper`: `Movimiento` ↔ `MovimientoResponse`.
- `cuenta.mapper.TransferenciaMapper`: `Transferencia` ↔ `TransferenciaResponse`/`ComprobanteResponse`.

## Repositories

- `CuentaRepository` (JpaRepository): `findByTitularDocumentoTipoAndTitularDocumentoNumero`, `findByNumeroCuenta`, `findByNumeroCuentaAndTitular...`.
- `MovimientoRepository`: `findByCuentaIdOrderByFechaDesc`, `findByCuentaIdAndFechaBetweenAndTipo`.
- `TransferenciaRepository`: `findByIdAndEstado`, `findByCuentaOrigenIdAndFechaCreacionAfter`.
- `TerceroAgendadoRepository`: `findByTitularDocumentoTipoAndTitularDocumentoNumeroAndActivoTrue`.
- `LimiteCanalRepository`: `findByProductoAndCanal`.
- `AuditoriaCuentaRepository`: `findByNumeroWhatsappOrderByFechaDesc`.

## Services

- `CuentaService`: listar cuentas del titular autenticado (filtra por documento del JWT).
- `SaldoService`: consultar saldo disponible y contable.
- `MovimientoService`: listar movimientos con filtros y paginación.
- `TransferenciaService`: preparar, confirmar y ejecutar transferencias; valida límites, horario, moneda, tercero agendado, saldo; doble confirmación.
- `TerceroService`: listar terceros agendados del titular.
- `LimiteService`: consultar límites diarios/mensuales y acumulados del canal.
- `HorarioService`: validar horario bancario operativo.
- `AuditoriaCuentaService`: registrar operaciones (`REQUIRES_NEW`), mismo contrato que H1/H2/H3.

## Clientes externos

- `CredencialesServiceClient` (interfaz) + impl `RestClient`: `validar(credencial): ValidarCredencialResponse` (numeroWhatsapp, documentoTipo, documentoNumero, fechaExpiracion, vigente). Timeouts configurados en `RestClientConfig`.

## Error contract

- `exception/ErrorResponse`, `FieldError`, `ErrorCode` (agrega `UNAUTHORIZED`, `FORBIDDEN` a los 5 códigos base), `GlobalExceptionHandler`, `NotFoundException`, `ConflictException` + `UnauthorizedException` (401) y `ForbiddenException` (403).
- Ninguna respuesta incluye datos sensibles ni internals.

## Configuración parametrizable (application.yml)

- `limits.movimientos.ventana-dias` (default 90)
- `limits.movimientos.tope-por-mensaje` (default 10)
- `limits.transferencia.horario-inicio` / `horario-fin` (ej. 08:00–17:00)
- `limits.transferencia.expiracion-preparada-minutos` (default 5)
- `clients.wa-credentials-service.base-url`

## Tests

- Unit: `CuentaService`, `SaldoService`, `MovimientoService`, `TransferenciaService` (límites, horario, moneda, doble confirmación, tercero no agendado), `LimiteService`, `HorarioService`, mockeando repositorios y `CredencialesServiceClient`.
- Integración: MockMvc por endpoint + WireMock para H2. JaCoCo ≥ 80%.
- Un test de error por endpoint que verifique status + `$.code` + `$.timestamp`.

## Riesgos / decisiones pendientes

- **Identidad Java del servicio nueva** (`base_package`, `artifactId`, puerto): propuestos por convención; tech-lead confirma y completa `project.yaml`.
- **Valores de límites diarios/mensuales por producto y canal**: parametrizables hasta que el banco los defina.
- **Horario bancario exacto y mensaje de "horario permitido"**: parametrizable.
- **Umbral de doble confirmación**: parametrizable.
- **Ventana máxima de movimientos y tope por mensaje**: defaults propuestos, parametrizables.
- **Productos multi-moneda y monedas aplicables**: parametrizable; por defecto solo misma moneda.
- **Formato de la referencia de transferencia**: generar UUID corto; parametrizable si el banco define formato.
- **Confirmación explícita**: flujo de dos pasos con expiración de la preparación; el cliente debe enviar "CONFIRMAR".
- **Concurrencia en transferencias**: usar bloqueo pesimista en cuenta origen para evitar doble débito.

## Dependencias con otros hitos

- **H2 (Servicio de Acceso y Sesión)**: H4 consume `POST /api/v1/credenciales/validar` para autorizar toda operación.
- **H1 (Servicio de Usuarios)**: referencia al cliente por documento; no se llama directamente (los datos vienen del JWT).
- **H3 (Servicio Canal WhatsApp)**: H3 invoca los endpoints de H4 con la credencial del cliente; H4 no llama a H3.
