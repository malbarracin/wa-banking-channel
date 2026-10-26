# Arquitectura — wa-channel-service

## Qué es

Microservicio Spring Boot 3 (Java 21) que administra el ciclo de vida del vínculo entre un número de
WhatsApp y un cliente existente del banco (hito H3). Expone aceptación de términos, verificación de
identidad, estado anti-duplicados, perfil y preferencias, bloqueo, desvinculación, re-vinculación e
historial para soporte.

No es el gateway de WhatsApp (H0), ni el orquestador de la conversación (H0.2), ni el servicio de
usuarios (H1), ni el emisor de credenciales (H2). Entrega a H0.2 un resultado verificable de identidad
y vínculo; la credencial la emite H2.

Identidad Maven: `com.bcra:wa-channel-service` · paquete base `com.bcra.channel.ws`.

## Capas (módulo `vinculo`)

```
com.bcra.channel.ws/
├── ChannelApplication.java
├── vinculo/
│   ├── CredencialCanal.java        # contrato del header de credencial válida
│   ├── CredencialHeaders.java      # headers de documento propagados por H2/H0.2
│   ├── controller/                 # VinculoController (/api/v1/vinculos)
│   ├── service/                    # VinculoService, VerificacionService,
│   │                               # VinculoAuditService, VerificadorIdentidad
│   ├── repository/                 # VinculoRepository, IntentoVerificacionRepository,
│   │                               # VinculoAuditLogRepository
│   ├── mapper/                     # VinculoMapper, VerificacionMapper (MapStruct)
│   ├── entity/                     # Vinculo, VinculoEstado, MotivoBloqueo,
│   │                               # IntentoVerificacion, VinculoAuditLog
│   ├── dto/                        # Request / Response
│   ├── client/                     # UsersServiceClient (H1),
│   │                               # CredencialesServiceClient (H2)
│   └── exception/                  # excepciones de dominio del vínculo
├── config/                         # OpenApiConfig, RestClientConfig, VerificacionConfig
└── exception/                      # ErrorResponse + GlobalExceptionHandler
```

## Flujo de una petición

```
HTTP → VinculoController → VinculoService / VerificacionService
                                 ↓                 ↓
                         VinculoRepository    UsersServiceClient (H1)
                                 ↓                 ↓
                              Entity         CredencialesServiceClient (H2)
                                 ↓
                          MapStruct Mapper
                                 ↓
                            DTO / Response
```

La verificación bloquea los vínculos del número con `PESSIMISTIC_WRITE` para que dos solicitudes
concurrentes no activen el mismo número.

## Endpoints

| Método | Ruta | Responsabilidad |
|--------|------|-----------------|
| POST | `/api/v1/vinculos/terminos` | Aceptación explícita; crea un pendiente nuevo |
| POST | `/api/v1/vinculos/{numero}/verificaciones` | Identidad, límite de intentos y activación/re-vinculación |
| GET | `/api/v1/vinculos/{numero}/estado` | Anti-duplicados sin datos del cliente; nunca 404 |
| GET | `/api/v1/vinculos/{numero}/perfil` | Perfil de H1 + preferencias locales; exige ACTIVO |
| PATCH | `/api/v1/vinculos/{numero}/preferencias` | Idioma, notificaciones y horario; exige ACTIVO |
| PATCH | `/api/v1/vinculos/{numero}/bloqueo` | Bloqueo por cliente o por política/riesgo |
| PATCH | `/api/v1/vinculos/{numero}/desvinculacion` | Baja del canal con confirmación |
| GET | `/api/v1/vinculos/{numero}/historial` | Estado actual y eventos de auditoría para soporte |

No hay versionado por paquetes: todos los endpoints están bajo `/api/v1`.

## Estados y transiciones

| Estado | Significado | Salidas |
|--------|-------------|---------|
| `PENDIENTE_VERIFICACION` | Términos aceptados; falta verificar | `ACTIVO` si la verificación es exitosa |
| `ACTIVO` | Vínculo operativo | `BLOQUEADO`, `DESVINCULADO` |
| `BLOQUEADO` | Pausado por cliente, banco o intentos | `DESVINCULADO` |
| `DESVINCULADO` | Canal dado de baja | Re-vinculación con un proceso nuevo |

- Cada `POST /terminos` crea un registro pendiente nuevo (no es idempotente).
- Un fallo de verificación deja el vínculo en `PENDIENTE_VERIFICACION` y descuenta un intento.
- Al agotar los fallos de la ventana, la siguiente solicitud responde **423** `LOCKED` y deriva a
  soporte; el estado no cambia y se puede reintentar cuando vence la ventana.
- La re-vinculación exige aceptar términos otra vez y verificar de nuevo; conserva el vínculo anterior
  cerrado y audita `REVINCULACION` cuando la identidad coincide.
- El esquema PostgreSQL define un índice único parcial para impedir dos vínculos `ACTIVO` del mismo
  número; el servicio además valida el caso y traduce el conflicto a **409**.

## Integraciones

| Integración | Operación | Efecto de un fallo |
|---|---|---|
| H1 `wa-users-service` | `GET /api/v1/users/{tipo}/{numero}/status` | 500 `INTERNAL_ERROR`; no se activa el vínculo |
| H1 `wa-users-service` | `GET /api/v1/users/{tipo}/{numero}` | 500 `INTERNAL_ERROR` al consultar perfil |
| H2 `credenciales-service` | `POST /api/v1/credenciales/{numero}/revocacion` | Best-effort: el cambio local se mantiene y se audita el rechazo |

Estados de H1 aceptados para vincular: solo `ACTIVE`. `SUSPENDED` y `LOGICAL_DELETE` responden **409**;
un cliente inexistente responde **404**. Tiempos por defecto: conexión 1 s, lectura 2 s.

## Persistencia y perfiles

| Perfil | BD | `ddl-auto` |
|--------|-----|------------|
| default | PostgreSQL (`DB_URL`, …) | `none` (esquema con `V001__vinculo.sql`) |
| `dev` | H2 en memoria | `update` |
| `docker` | PostgreSQL en Compose (`postgres:5432`) | `none` (init del volume) |
| `test` | H2 in-memory (MODE=PostgreSQL) | `create-drop` |
| `prod` | PostgreSQL (vars obligatorias) | `validate` |

Tablas: `vinculo`, `intento_verificacion` y `vinculo_audit_log`.

## Auditoría

`VinculoAuditService` registra en `REQUIRES_NEW` para conservar el evento aunque la operación principal
se revierta. Acciones auditadas: `ACEPTACION_TERMINOS`, `VERIFICACION_EXITOSA`, `VERIFICACION_FALLIDA`,
`VINCULACION`, `REVINCULACION`, `BLOQUEO` y `DESVINCULACION`, con resultado `EXITOSO` o `RECHAZADO`.

Los motivos libres se sustituyen por `Detalle omitido por seguridad`; no se guardan pruebas de
identidad ni secretos. El endpoint de historial omite esos motivos.

## Contrato de error

`ErrorResponse` (`code`, `message`, `details`, `timestamp`), `FieldError`, `ErrorCode` y
`GlobalExceptionHandler` viven en `exception/`. `details` nunca es `null` y no se exponen stacktraces.

| HTTP | `code` |
|------|--------|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` |
| 401 | `UNAUTHORIZED` |
| 404 | `NOT_FOUND` |
| 409 | `CONFLICT` |
| 423 | `LOCKED` |
| 500 | `INTERNAL_ERROR` |

## Decisiones relevantes

- Número en formato E.164 con prefijo `+`; el path lo transporta tal cual.
- El contexto de credencial se recibe por headers y H3 solo comprueba su presencia/valor `true`, no el token.
- La verificación es un puerto (`VerificadorIdentidad`); el bean por defecto rechaza toda prueba hasta
  que Compliance defina el método. Los datos viajan en un mapa y no se persisten.
- Los límites de intentos y la ventana son configurables por entorno.
- El bloqueo por `POLITICA_BANCO` es una capacidad del endpoint; los criterios de riesgo los define el banco.
- La revocación en H2 no participa de la transacción local.

## Alcance y fuera de alcance

Fuente: `specs/canal-whatsapp/spec.md`. Fuera de H3: alta de clientes (H1), emisión/revocación directa
de credenciales (H2), mensajes y webhooks (H0/H0.1/H0.2), productos (H4–H6) y cambios de datos legales
sensibles.

## Limitaciones conocidas (preguntas abiertas de la spec)

1. **Método de verificación** — pendiente de Compliance; placeholder que rechaza todo.
2. **Máximo de intentos y ventana/pausa** — configurables; 3 intentos y 15 minutos son defaults técnicos.
3. **Representante autorizado** — no modelado; solo se implementa el titular.
4. **Criterios de bloqueo por riesgo** — no definidos; el endpoint acepta `POLITICA_BANCO` sin reglas.
5. **Campos de perfil/preferencias** — provisionales (contacto de H1 + idioma/notificaciones/horario).
6. **Contenido y período del historial** — todos los eventos del número, sin paginación ni retención definida.

Mientras esas definiciones no lleguen, el servicio es operable en local pero no debe considerarse
cerrado el contrato de negocio.
