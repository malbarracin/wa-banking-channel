# Arquitectura — Servicio de Tarjetas (H5)

## Base

- **Servicio nuevo** (no existe `pom.xml` todavía): `services/wa-cards-service`, análogo a `wa-accounts-service` (H4) y `wa-insurance-service` (H6).
- Maven groupId: **com.bcra** (`project.yaml` → `java.group_id`).
- Base package: **com.bcra.cards.ws** — propuesto, siguiendo el patrón `<group_id>.<dominio>.ws`. Tech-lead confirma y completa `project.yaml`.
- Artifact id propuesto: `wa-cards-service`.
- Módulo de dominio: **tarjeta** (agregado central: tarjeta, consumo, bloqueo, avisoViaje).
- Puerto propuesto: **8086** (H1 8080, H3 8081, H2 8082, H4 8084, H6 8085; evita choque en local).
- Persistencia: PostgreSQL en runtime, H2 en test — base de datos propia (`wa_cards`).
- Integraciones salientes (síncronas, REST):
  - **H2 (wa-credentials-service)**: validar credencial de sesión (`POST /api/v1/credenciales/validar`). Base URL configurable (`clients.wa-credentials-service.base-url`).
  - H5 no llama a H1 ni H3 directamente: la identidad del cliente viene del JWT validado por H2; el vínculo activo se garantiza porque H2 revoca la credencial al desvincular/bloquear.

## Árbol de paquetes

```
com.bcra.cards.ws/
├── CardsApplication.java
├── tarjeta/
│   ├── controller/      TarjetaController, ConsumoController, BloqueoController, AvisoViajeController
│   ├── service/         TarjetaService, ConsumoService, BloqueoService, AvisoViajeService,
│   │                    AuditoriaTarjetaService
│   ├── repository/      TarjetaRepository, ConsumoRepository, BloqueoRepository,
│   │                    AvisoViajeRepository, AuditoriaTarjetaRepository
│   ├── mapper/          TarjetaMapper, ConsumoMapper (MapStruct)
│   ├── entity/          Tarjeta, MarcaTarjeta, TipoTarjeta, EstadoTarjeta,
│   │                    Consumo, Bloqueo, TipoBloqueo, AvisoViaje, AuditoriaTarjeta, AuditoriaAccion
│   ├── dto/             TarjetaResponse, ConsumoResponse, ConsumosPageResponse,
│   │                    BloqueoTemporalRequest, BloqueoDefinitivoRequest,
│   │                    DesbloqueoRequest, AvisoViajeRequest, AvisoViajeResponse
│   ├── client/          CredencialesServiceClient (+ dto ValidarCredencialResponse)
│   └── exception/       TarjetaNoEncontradaException, TarjetaNoAutorizadaException,
│                        BloqueoNoAplicableException, BloqueoDefinitivoIrreversibleException,
│                        ConfirmacionRequeridaException
├── config/              OpenApiConfig, RestClientConfig (timeouts + base URLs de H2)
└── exception/           ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler,
                        NotFoundException, ConflictException, UnauthorizedException,
                        ForbiddenException
```

## Entidades

- **tarjeta.entity.Tarjeta**: id (UUID), numeroTarjetaHash (string, hash de PAN; nunca almacenar PAN completo), ultimosCuatro (string), marca (`MarcaTarjeta`: VISA, MASTERCARD, AMEX, etc.), tipo (`TipoTarjeta`: DEBITO, CREDITO), titularDocumentoTipo + titularDocumentoNumero (referencia al cliente, sin FK física), estado (`EstadoTarjeta`: ACTIVA, BLOQUEO_TEMPORAL, BLOQUEO_DEFINITIVO), activa (boolean), createdAt/updatedAt.
- **tarjeta.entity.Consumo**: id (UUID), tarjeta (FK lógica a Tarjeta), fecha (Instant), comercio (string), monto (BigDecimal), moneda, descripcion (nullable), referencia (string, nullable).
- **tarjeta.entity.Bloqueo**: id (UUID), tarjetaId (UUID), tipo (`TipoBloqueo`: TEMPORAL, DEFINITIVO), motivo (string, nullable para TEMPORAL; "ROBO" | "EXTRAVIO" para DEFINITIVO), fechaBloqueo (Instant), fechaDesbloqueo (Instant, nullable), usuarioEjecutor (string, referencia al titular), dobleConfirmacionCompleta (boolean).
- **tarjeta.entity.AvisoViaje**: id (UUID), tarjetaId (UUID), pais (string), region (string, nullable), fechaDesde (LocalDate), fechaHasta (LocalDate), fechaRegistro (Instant), confirmado (boolean).
- **tarjeta.entity.AuditoriaTarjeta**: id, accion (`AuditoriaAccion`: LISTAR_TARJETAS, CONSULTAR_CONSUMOS, BLOQUEAR_TEMPORAL, DESBLOQUEAR_TEMPORAL, BLOQUEAR_DEFINITIVO, REGISTRAR_AVISO_VIAJE), resultado (EXITOSO/RECHAZADO), numeroWhatsapp, documentoTipo, documentoNumero, tarjetaId (nullable), motivo (nullable), fecha.

## DTOs

- `TarjetaResponse(id, ultimosCuatro, marca, tipo, estado)`
- `ConsumoResponse(fecha, comercio, monto, moneda, descripcion, referencia)`
- `ConsumosPageResponse(consumos: List<ConsumoResponse>, tieneMas: boolean, cursor: String?)`
- `BloqueoTemporalRequest(tarjetaId, confirmar)` — confirmar = "CONFIRMAR"
- `BloqueoDefinitivoRequest(tarjetaId, motivo, confirmarPrimero, confirmarSegundo)` — motivo = "ROBO" | "EXTRAVIO"
- `DesbloqueoRequest(tarjetaId, validacionAdicional)` — validacionAdicional según política
- `AvisoViajeRequest(tarjetaId, pais, region?, fechaDesde, fechaHasta)`
- `AvisoViajeResponse(tarjetaId, pais, region, fechaDesde, fechaHasta, mensajeConfirmacion)`

## Endpoints

Todos bajo `/api/v1` (sin versionado por ahora — `api.versioning` no está habilitado en `project.yaml`).

| Método y path | Request | Éxito | Errores (HTTP — `code`) |
|---|---|---|---|
| `GET /api/v1/tarjetas` | — (credencial por header `X-Credencial-Sesion`) | 200 `List<TarjetaResponse>` | 401 `UNAUTHORIZED`; 403 `FORBIDDEN` (vínculo inactivo) |
| `GET /api/v1/tarjetas/{tarjetaId}/consumos` | query: `dias`, `limit`, `cursor` | 200 `ConsumosPageResponse` | 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND` (tarjeta no del titular) |
| `POST /api/v1/tarjetas/{tarjetaId}/bloquear/temporal` | `BloqueoTemporalRequest` | 200 (mensaje de bloqueo temporal + cómo desbloquear) | 400 `VALIDATION_ERROR`/`BAD_REQUEST`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND`; 409 `CONFLICT` |
| `POST /api/v1/tarjetas/{tarjetaId}/desbloquear/temporal` | `DesbloqueoRequest` | 200 (confirmación de desbloqueo) | 400 `VALIDATION_ERROR`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND`; 409 `CONFLICT` (bloqueo definitivo) |
| `POST /api/v1/tarjetas/{tarjetaId}/bloquear/definitivo` | `BloqueoDefinitivoRequest` | 200 (bloqueo definitivo + sugerencia reposición) | 400 `VALIDATION_ERROR`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND`; 409 `CONFLICT` (sin doble confirmación) |
| `POST /api/v1/tarjetas/{tarjetaId}/aviso-viaje` | `AvisoViajeRequest` | 201 `AvisoViajeResponse` | 400 `VALIDATION_ERROR`/`BAD_REQUEST`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND` |

Notas:
- La credencial se recibe por header `X-Credencial-Sesion` (JWT de H2). H5 la valida con H2 antes de cualquier operación; si inválida/expirada/revocada → 401 `UNAUTHORIZED` y no consulta productos.
- Bloqueo temporal requiere confirmación explícita (`confirmar = "CONFIRMAR"`). Si falta → no cambia estado.
- Bloqueo definitivo requiere doble confirmación (`confirmarPrimero` y `confirmarSegundo` = "CONFIRMAR") + motivo válido (ROBO/EXTRAVIO) y advertencia de irreversibilidad. No permite desbloqueo por chat.
- Desbloqueo temporal requiere validación adicional (parametrizable). Si tarjeta en bloqueo definitivo → 409 `CONFLICT` con derivación a canales formales.
- Consumos: tope de cantidad y ventana de días parametrizables (defaults propuestos). Nunca incluir PAN/CVV/fecha de vencimiento.
- Aviso de viaje: registra país/región y fechas; confirma y entrega recomendaciones de seguridad. No reemplaza políticas internacionales del emisor.

## Mappers

- `tarjeta.mapper.TarjetaMapper` (MapStruct): `Tarjeta` ↔ `TarjetaResponse`.
- `tarjeta.mapper.ConsumoMapper`: `Consumo` ↔ `ConsumoResponse`.

## Repositories

- `TarjetaRepository` (JpaRepository): `findByTitularDocumentoTipoAndTitularDocumentoNumero`, `findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero`.
- `ConsumoRepository`: `findByTarjetaIdAndFechaAfterOrderByFechaDesc`, `findByTarjetaIdAndFechaBetweenOrderByFechaDesc`.
- `BloqueoRepository`: `findByTarjetaIdOrderByFechaBloqueoDesc`.
- `AvisoViajeRepository`: `findByTarjetaIdOrderByFechaRegistroDesc`.
- `AuditoriaTarjetaRepository`: `findByNumeroWhatsappOrderByFechaDesc`.

## Services

- `TarjetaService`: listar tarjetas del titular autenticado (filtra por documento del JWT).
- `ConsumoService`: listar consumos recientes con tope y ventana parametrizables, paginación por cursor.
- `BloqueoService`: bloquear temporal (con confirmación), desbloquear temporal (con validación adicional, rechaza si definitivo), bloquear definitivo (doble confirmación, advierte irreversibilidad, no reversible por chat).
- `AvisoViajeService`: registrar aviso de viaje con país/región/fechas, confirmar y entregar recomendaciones.
- `AuditoriaTarjetaService`: registrar operaciones (`REQUIRES_NEW`), mismo contrato que H1/H2/H3/H4/H6.

## Clientes externos

- `CredencialesServiceClient` (interfaz) + impl `RestClient`: `validar(credencial): ValidarCredencialResponse` (numeroWhatsapp, documentoTipo, documentoNumero, fechaExpiracion, vigente). Timeouts configurados en `RestClientConfig`.

## Error contract

- `exception/ErrorResponse`, `FieldError`, `ErrorCode` (agrega `UNAUTHORIZED`, `FORBIDDEN` a los 5 códigos base), `GlobalExceptionHandler`, `NotFoundException`, `ConflictException` + `UnauthorizedException` (401) y `ForbiddenException` (403).
- Ninguna respuesta incluye datos sensibles (PAN/CVV/fecha de vencimiento) ni internals.

## Configuración parametrizable (application.yml)

- `cards.consumos.ventana-dias` (default 90)
- `cards.consumos.tope-por-mensaje` (default 10)
- `cards.bloqueo.validacion-adicional-requerida` (default true)
- `cards.aviso-viaje.recomendaciones` (texto parametrizable)
- `clients.wa-credentials-service.base-url`
- `clients.wa-credentials-service.connect-timeout`
- `clients.wa-credentials-service.read-timeout`

## Tests

- Unit: `TarjetaService`, `ConsumoService`, `BloqueoService` (confirmación única, doble confirmación, irreversible, desbloqueo en definitivo), `AvisoViajeService`, mockeando repositorios y `CredencialesServiceClient`.
- Integración: MockMvc por endpoint + WireMock para H2. JaCoCo ≥ 80%.
- Un test de error por endpoint que verifique status + `$.code` + `$.timestamp`.
- Test explícito: ausencia de PAN/CVV/fecha en respuestas.

## Riesgos / decisiones pendientes

- **Identidad Java del servicio nueva** (`base_package`, `artifactId`, puerto): propuestos por convención; tech-lead confirma y completa `project.yaml`.
- **Tope de cantidad y ventana de días para consumos recientes**: parametrizables (fuente no fija valores).
- **Validación adicional para desbloqueo temporal**: no definida (ej. OTP/pregunta de seguridad); parametrizable hasta que se defina.
- **Recomendaciones de seguridad para aviso de viaje**: no detalladas; parametrizables.
- **Coordinación con H4 (Cuentas) si política unifica límites de gasto**: opcional según fuente.
- **Almacenamiento seguro**: nunca guardar PAN completo; usar hash + últimos 4 dígitos.

## Dependencias con otros hitos

- **H2 (Servicio de Acceso y Sesión)**: H5 consume `POST /api/v1/credenciales/validar` para autorizar toda operación.
- **H1 (Servicio de Usuarios)**: referencia al cliente por documento; no se llama directamente.
- **H3 (Servicio Canal WhatsApp)**: H3 invoca los endpoints de H5 con la credencial del cliente; H5 no llama a H3.
