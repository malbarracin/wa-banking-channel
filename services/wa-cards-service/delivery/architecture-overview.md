# Arquitectura — wa-cards-service

## Qué es

Microservicio Spring Boot 3 (Java 21) que atiende las operaciones de tarjetas del canal (hito H5):
listado de tarjetas del titular, consumos recientes, bloqueo temporal y su desbloqueo, bloqueo
definitivo por robo o extravío y aviso de viaje. No expone datos sensibles de la tarjeta y no da de
alta tarjetas.

Identidad Maven: `com.bcra:wa-cards-service` · paquete base `com.bcra.cards.ws`
(`project.yaml` / `specs/tarjetas/arquitectura.md`) · puerto `8086`.

## Capas (módulo `tarjeta`)

```
com.bcra.cards.ws/
├── CardsApplication.java
├── tarjeta/
│   ├── controller/     # TarjetaController, ConsumoController, BloqueoController, AvisoViajeController
│   ├── service/        # TarjetaService, ConsumoService, BloqueoService, AvisoViajeService,
│   │                   # AuditoriaTarjetaService
│   ├── repository/     # Tarjeta, Consumo, Bloqueo, AvisoViaje, AuditoriaTarjeta
│   ├── mapper/         # TarjetaMapper, ConsumoMapper, BloqueoMapper, AvisoViajeMapper (MapStruct)
│   ├── entity/         # Tarjeta, Consumo, Bloqueo, AvisoViaje, AuditoriaTarjeta y enums
│   ├── dto/            # Request / Response
│   ├── client/         # CredencialesServiceClient + RestCredencialesServiceClient
│   └── exception/      # dominio (NotFound, Conflict, …)
├── config/             # OpenApiConfig, RestClientConfig
└── exception/          # ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler
```

## Flujo de una petición

```
HTTP → *Controller → *Service → *Repository → Entity
                          ↓
                       Mapper (MapStruct)
                          ↓
                     DTO Response
```

Antes de tocar productos, cada servicio valida la credencial con H2
(`POST /api/v1/credenciales/validar`). La identidad resultante (documento del titular) filtra todas
las consultas: una tarjeta de otro titular se comporta como inexistente (**404**).

## Endpoints

| Método | Ruta | Responsabilidad |
|--------|------|-----------------|
| GET | `/api/v1/tarjetas` | Lista tarjetas del titular autenticado |
| GET | `/api/v1/tarjetas/{tarjetaId}/consumos` | Consumos con ventana, tope y cursor |
| POST | `/api/v1/tarjetas/{tarjetaId}/bloquear/temporal` | Bloqueo temporal con confirmación |
| POST | `/api/v1/tarjetas/{tarjetaId}/desbloquear/temporal` | Reactivación tras validación adicional |
| POST | `/api/v1/tarjetas/{tarjetaId}/bloquear/definitivo` | Bloqueo irreversible con doble confirmación |
| POST | `/api/v1/tarjetas/{tarjetaId}/aviso-viaje` | Registro del aviso confirmado |

## Estados de la tarjeta

| Estado | Significado | Salidas |
|--------|-------------|---------|
| `ACTIVA` | Operativa | `BLOQUEO_TEMPORAL`, `BLOQUEO_DEFINITIVO` |
| `BLOQUEO_TEMPORAL` | Bloqueo reversible | `ACTIVA` (desbloqueo), `BLOQUEO_DEFINITIVO` |
| `BLOQUEO_DEFINITIVO` | Irreversible por el canal | *(ninguna)* |

Operación no permitida por el estado → **409** `CONFLICT`.

## Persistencia y perfiles

| Perfil | BD | `ddl-auto` |
|--------|-----|------------|
| default | PostgreSQL (`DB_URL`, …) | `validate` |
| `dev` | PostgreSQL (`localhost:5437`) | `update` |
| `docker` | PostgreSQL en Compose (`postgres:5432`) | `update` |
| `test` | H2 in-memory (MODE=PostgreSQL) | `create-drop` |
| `prod` | PostgreSQL (vars obligatorias) | `validate` |

Tablas: `tarjetas`, `consumos`, `bloqueos`, `avisos_viaje`, `auditoria_tarjetas`. La tarjeta guarda
hash del PAN y últimos cuatro dígitos; nunca el PAN completo. El titular se referencia por documento
sin FK física a H1.

## Transacciones y auditoría

- `TarjetaService.listar`: audita el listado en `REQUIRES_NEW` (no pierde el registro si falla el
  resto del flujo).
- `BloqueoService.bloquearTemporal`: cambio de estado + bloqueo en la transacción del servicio; la
  auditoría de bloqueo temporal se registra en `REQUIRES_NEW`.
- `BloqueoService.bloquearDefinitivo` y `desbloquearTemporal`: bloqueo/desbloqueo, cambio de estado y
  auditoría en la **misma** transacción (`Propagation.MANDATORY` en `AuditoriaTarjetaService`).
- `bloquearDefinitivo` y `desbloquearTemporal` leen la tarjeta con `PESSIMISTIC_WRITE`
  (`buscarParaActualizar`); `bloquearTemporal` usa `buscarParaBloqueo`, también con lock.
- `ConsumoService` es `readOnly` y usa una ventana congelada por cursor.

## Consumos: paginación

El cursor es Base64 URL-safe de `tarjetaId|ventana|hasta|últimaFecha|últimoId`. Se valida que
corresponda a la misma tarjeta y ventana, que `hasta` no sea futuro y que la posición esté dentro de la
ventana; si no, **400** `BAD_REQUEST`. El orden es fecha e identificador descendentes y el tope de
página se acota a `cards.consumos.tope-por-mensaje`.

## Decisión: doble confirmación del bloqueo definitivo

El bloqueo definitivo solo se aplica con `motivo` `ROBO`/`EXTRAVIO` y las dos confirmaciones exactas
`CONFIRMAR`. Si faltan, se lanza `DobleConfirmacionRequeridaException` (→ **409** `CONFLICT`) con el
texto de irreversibilidad para que el canal lo muestre antes de volver a pedirlas. La tarjeta no se
modifica en ese intento. Un motivo inválido es `IllegalArgumentException` → **400** genérico
(`La solicitud no es válida`).

## Configuración parametrizable

| Propiedad | Default | Uso |
|-----------|---------|-----|
| `cards.consumos.ventana-dias` | 90 | Máximo de días hacia atrás |
| `cards.consumos.tope-por-mensaje` | 10 | Máximo de consumos por página |
| `cards.bloqueo.validacion-adicional-requerida` | true | Exigir `validacionAdicional` al desbloquear |
| `cards.aviso-viaje.recomendaciones` | texto del emisor | Texto de seguridad del aviso |
| `clients.wa-credentials-service.*` | — | URL y timeouts de H2 |

## Contrato de errores

`ErrorResponse` (`code`, `message`, `details`, `timestamp` UTC) y `GlobalExceptionHandler` en
`exception/`. Códigos: `VALIDATION_ERROR`, `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`,
`CONFLICT`, `INTERNAL_ERROR`. Sin stacktrace ni internals en la respuesta
(`server.error.include-stacktrace: never`).

## Alcance H5 / fuera de alcance

Ver [`specs/tarjetas/spec.md`](../../specs/tarjetas/spec.md). Fuera: PAN/CVV/vencimiento, alta de
tarjetas, pago de resumen y disputa de consumos (proceso formal).

## Limitaciones conocidas (preguntas abiertas de la spec)

1. **Ventana y tope de consumos** — la fuente no fija valores; parametrizables (90 / 10 por defecto).
2. **Validación adicional del desbloqueo** — no definida; hoy booleano configurable.
3. **Recomendaciones del aviso de viaje** — no detalladas; texto parametrizable.
4. **Coordinación con H4** — unificación de límites de gasto opcional según la fuente.
