---
title: Arquitectura — Capa MCP (H7)
status: borrador
source: specs/capa-mcp/spec.md + contratos reales de H1–H6
  (services/*/delivery/api.md, openapi) + platform/wa-channel-orchestrator/H9.md
updated: 2026-10-30T18:17:40.313Z
---

## Base

- Componente: `ai/wa-mcp-layer` (carpeta nueva; no existe todavía en el repo).
- Stack (`ia-stack.md`): Python 3.12, `uv`, SDK oficial `mcp` (`FastMCP`, transporte **Streamable HTTP**),
  `httpx` async para llamar a H1–H6, `pydantic` / `pydantic-settings`, `structlog` (JSON), `ruff`, `mypy --strict`,
  `pytest` + `pytest-asyncio`.
- `project.yaml` no tenía sección `ia:`; la agregué con los valores default de `ia-stack.md` (modelo
  `gemini-2.5-flash`, embeddings `text-embedding-004`/768, puertos H7 8101 / H8 8102 / H9 8103, `session_ttl_minutes: 30`)
  porque la fuente no fija otros valores. El puerto 8101 no choca con los puertos ya usados por H0–H6 (8080–8091).
- H7 **no tiene base de datos propia**: no está en el stack obligatorio de H7 (eso es H8/pgvector y H9/Redis). La
  auditoría se resuelve con logs estructurados (ver `Auditoría y observabilidad`), no con una tabla nueva.
- Servicios reales que consume (ya construidos, confirmado leyendo sus `delivery/api.md` y README):

  | Hito | Servicio | Puerto local | Credencial de sesión |
  |---|---|---|---|
  | H1 | `wa-users-service` | 8080 | No aplica (consumido internamente por H2/H3) |
  | H2 | `wa-credentials-service` | 8082 | Emite/valida la credencial; no la exige para sí misma |
  | H3 | `wa-channel-service` | 8081 | Exige contexto ya validado por headers (`X-Canal-Credencial-Valida`, `X-Credencial-Documento-Tipo/Numero`), no el JWT crudo |
  | H4 | `wa-accounts-service` | 8084 | Exige `X-Credencial-Sesion: <JWT_H2>` y la valida ella misma contra H2 |
  | H5 | `wa-cards-service` | 8086 | Igual que H4 |
  | H6 | `wa-insurance-service` | 8085 | Igual que H4 |

  Dato clave para el diseño: **H4, H5 y H6 ya validan la credencial contra H2 por su cuenta** (tienen su propio
  `CredencialesServiceClient`). H7 no duplica esa validación de firma/vigencia; su responsabilidad es la
  **precondición de canal** (que exista una credencial en el contexto de sesión antes de llamar al servicio) y la
  **traducción** del 401/403 que el servicio devuelva. H3, en cambio, no valida JWT: espera que quien la llama
  (H7) ya haya resuelto la sesión y le pase el resultado en headers puntuales — ahí H7 sí es responsable de armar
  ese contexto a partir de la credencial vigente.

## Límites (según `ia-boundaries.md`)

- H7 expone capacidades; no decide producto, no conversa, no tiene conocimiento documental (eso es H9/H8).
- H7 no habla con Twilio ni Kafka; no conoce conversationId/messageId técnicos de H0.2 — solo recibe lo que H9 le
  pasa capacidad por capacidad.
- H7 no persiste estado de negocio: cada invocación es un passthrough validado y auditado hacia H1–H6.
- Toda capacidad de producto (H4/H5/H6) exige contexto de sesión con credencial; H3 (vínculo) y H2 (emitir/validar)
  pueden invocarse sin ella, según el flujo de alta/reverificación.

## Árbol de paquetes

```
ai/wa-mcp-layer/
├── pyproject.toml
├── uv.lock
├── .env.example
├── README.md
└── src/wa_mcp_layer/
    ├── server.py                 # FastMCP("wa-mcp-layer"), transporte Streamable HTTP en /mcp, healthcheck /health
    ├── settings.py                # pydantic-settings: puertos, URLs/timeouts de H1–H6, flags de capacidad por env
    ├── envelope.py                 # ContextoSesion, Resultado, ErrorNegocio (modelos pydantic comunes)
    ├── security/
    │   ├── session_context.py     # valida presencia/forma de credencial antes de llamar al servicio
    │   └── pii.py                  # enmascarado de documento/teléfono/nombre para logs y auditoría
    ├── clients/
    │   ├── base.py                 # httpx.AsyncClient con timeouts connect/read configurables y mapeo de errores HTTP
    │   ├── users_client.py         # H1
    │   ├── credentials_client.py   # H2
    │   ├── channel_client.py       # H3
    │   ├── accounts_client.py      # H4
    │   ├── cards_client.py         # H5
    │   └── insurance_client.py     # H6
    ├── capabilities/
    │   ├── catalog.py              # registro versionado: nombre, versión, servicio, requiere_credencial, actor, handler
    │   ├── identidad.py            # H1 (uso restringido, actor=soporte)
    │   ├── acceso.py               # H2
    │   ├── vinculo.py               # H3
    │   ├── cuentas.py               # H4
    │   ├── tarjetas.py              # H5
    │   └── seguros.py               # H6
    ├── errors.py                   # mapeo código/HTTP de H1–H6 → motivo de negocio de H7 (sin stack trace)
    └── audit/
        └── logger.py                # evento de auditoría estructurado (structlog), nunca el valor de la credencial
tests/
├── unit/                            # mocks de httpx por cliente, catálogo, mapeo de errores, enmascarado PII
└── integration/                     # contra los servicios H1–H6 levantados (docker-compose), o WireMock-equivalente
```

## Catálogo de capacidades (`shared/contracts/wa-mcp-layer/`)

Publica `shared/contracts/wa-mcp-layer/capabilities.md` (este catálogo) + `shared/contracts/wa-mcp-layer/schemas/`
con el esquema JSON de entrada/salida de cada capacidad. Convención de nombre: `<dominio>.<accion>` y versión en el
nombre de la herramienta MCP registrada (`<dominio>_<accion>_v1`); cuando cambie el contrato de un servicio se agrega
`_v2` sin retirar `_v1` hasta que H9 migre (requisito de versionado de la spec).

Releví las operaciones reales de cada servicio (no las de la fuente genérica "H1 a H6") contra sus `delivery/api.md`.
Resultado — **32 capacidades**:

| Capacidad | v | Servicio | Operación real | Requiere credencial (H2) | Actor habilitado |
|---|---|---|---|---|---|
| `identidad.consultar_cliente` | 1 | H1 | `GET /api/v1/users/{tipo}/{numero}` | No | soporte |
| `identidad.consultar_estado_cliente` | 1 | H1 | `GET /api/v1/users/{tipo}/{numero}/status` | No | soporte |
| `acceso.emitir_credencial` | 1 | H2 | `POST /api/v1/credenciales/emitir` | No (requiere `vinculoActivo`+`identidadVerificada` ya resueltos por `vinculo.verificar_identidad`) | agente |
| `acceso.validar_credencial` | 1 | H2 | `POST /api/v1/credenciales/validar` | Recibe la credencial a validar (no una ya validada) | agente (interno, no conversacional) |
| `acceso.revocar_credencial` | 1 | H2 | `POST /api/v1/credenciales/{numero}/revocacion` | No (identifica por número) | agente (autoservicio: "cerrá mi sesión") y soporte |
| `acceso.revocar_credenciales_masivo` | 1 | H2 | `POST /api/v1/credenciales/revocacion-masiva` | No | banco/seguridad (deshabilitada para el agente por flag) |
| `acceso.consultar_historial_credencial` | 1 | H2 | `GET /api/v1/credenciales/{numero}/historial` | No | soporte |
| `vinculo.aceptar_terminos` | 1 | H3 | `POST /api/v1/vinculos/terminos` | No | agente |
| `vinculo.verificar_identidad` | 1 | H3 | `POST /api/v1/vinculos/{numero}/verificaciones` | No | agente |
| `vinculo.consultar_estado` | 1 | H3 | `GET /api/v1/vinculos/{numero}/estado` | No | agente |
| `vinculo.consultar_perfil` | 1 | H3 | `GET /api/v1/vinculos/{numero}/perfil` | Sí (contexto validado, header `X-Canal-Credencial-Valida`) | agente |
| `vinculo.actualizar_preferencias` | 1 | H3 | `PATCH /api/v1/vinculos/{numero}/preferencias` | Sí (headers de documento) | agente |
| `vinculo.bloquear` | 1 | H3 | `PATCH /api/v1/vinculos/{numero}/bloqueo` | Sí para `SOLICITUD_CLIENTE` | agente |
| `vinculo.desvincular` | 1 | H3 | `PATCH /api/v1/vinculos/{numero}/desvinculacion` | Sí | agente |
| `vinculo.consultar_historial` | 1 | H3 | `GET /api/v1/vinculos/{numero}/historial` | No | soporte |
| `cuentas.listar_cuentas` | 1 | H4 | `GET /api/v1/cuentas` | Sí | agente |
| `cuentas.consultar_saldo` | 1 | H4 | `GET /api/v1/cuentas/{numero}/saldo` | Sí | agente |
| `cuentas.consultar_movimientos` | 1 | H4 | `GET /api/v1/cuentas/{numero}/movimientos` | Sí | agente |
| `cuentas.listar_terceros` | 1 | H4 | `GET /api/v1/terceros` | Sí | agente |
| `cuentas.preparar_transferencia` | 1 | H4 | `POST /api/v1/transferencias` | Sí | agente |
| `cuentas.confirmar_transferencia` | 1 | H4 | `POST /api/v1/transferencias/{id}/confirmar` | Sí | agente |
| `tarjetas.listar_tarjetas` | 1 | H5 | `GET /api/v1/tarjetas` | Sí | agente |
| `tarjetas.consultar_consumos` | 1 | H5 | `GET /api/v1/tarjetas/{id}/consumos` | Sí | agente |
| `tarjetas.bloquear_temporal` | 1 | H5 | `POST /api/v1/tarjetas/{id}/bloquear/temporal` | Sí | agente |
| `tarjetas.desbloquear_temporal` | 1 | H5 | `POST /api/v1/tarjetas/{id}/desbloquear/temporal` | Sí | agente |
| `tarjetas.bloquear_definitivo` | 1 | H5 | `POST /api/v1/tarjetas/{id}/bloquear/definitivo` | Sí | agente |
| `tarjetas.registrar_aviso_viaje` | 1 | H5 | `POST /api/v1/tarjetas/{id}/aviso-viaje` | Sí | agente |
| `seguros.listar_polizas` | 1 | H6 | `GET /api/v1/seguros` | Sí | agente |
| `seguros.consultar_coberturas` | 1 | H6 | `GET /api/v1/seguros/{id}/coberturas` | Sí | agente |
| `seguros.denunciar_siniestro` | 1 | H6 | `POST /api/v1/siniestros` | Sí | agente |
| `seguros.consultar_siniestro` | 1 | H6 | `GET /api/v1/siniestros/{numeroCaso}` | Sí | agente |
| `seguros.consultar_documentacion_pendiente` | 1 | H6 | `GET /api/v1/siniestros/{numeroCaso}/documentacion-pendiente` | Sí | agente |

Notas de cobertura:
- `users.alta` (`POST /api/v1/users`) **no** se expone: el alta de cliente no es un flujo del canal WhatsApp (lo
  confirman las specs de H1/H3); si el banco decide habilitarlo, es una capacidad nueva a pedir explícitamente.
- `acceso.*` no tiene una capacidad de "renovación": el propio H2 confirma en su `delivery/api.md` que la renovación
  es un job interno sin endpoint. El rol de H7 ante un 401 por riesgo de renovación es derivar a `vinculo.verificar_identidad`
  → `acceso.emitir_credencial` (re-verificación), no reintentar la validación.
- `acceso.revocar_credenciales_masivo`, `acceso.consultar_historial_credencial`, `identidad.*` y
  `vinculo.consultar_historial` quedan en el catálogo (cumple "catálogo cubre H1–H6") pero con actor `soporte`/`banco`:
  el registro de capacidades en `catalog.py` las marca `actor: "soporte"` y el servidor MCP no las ofrece en el
  `tool_filter` que usa H9 para conversación con clientes (si en el futuro soporte necesita invocarlas, es otro
  consumidor MCP con su propio contexto, no el agente conversacional).

## Envoltorio de entrada/salida (contrato común)

Todas las capacidades comparten el mismo sobre, documentado en `shared/contracts/wa-mcp-layer/schemas/_envelope.json`:

```json
{
  "contexto": {
    "numero_whatsapp": "+5491112345678",
    "credencial": "eyJhbGciOi... | null",
    "correlation_id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01"
  },
  "parametros": { "...": "específico de cada capacidad" }
}
```

Salida exitosa:

```json
{
  "resultado": { "...": "específico de cada capacidad" },
  "auditoria": { "capacidad": "tarjetas.bloquear_temporal", "version": 1, "correlation_id": "...", "timestamp": "..." }
}
```

Error (nunca stack trace, SQL ni nombres internos — requisito de la spec):

```json
{
  "error": { "codigo": "CREDENCIAL_INVALIDA", "mensaje": "Por seguridad, volvé a verificar tu identidad para continuar", "reintentable": false },
  "auditoria": { "capacidad": "tarjetas.bloquear_temporal", "version": 1, "correlation_id": "...", "timestamp": "..." }
}
```

`contexto.credencial` se marca `"x-sensible": true` en el esquema: el valor nunca aparece en logs, nunca se repite
en `resultado` salvo en las dos capacidades donde el propio H2 lo emite (`acceso.emitir_credencial`,
`acceso.validar_credencial` no lo repite — solo confirma vigencia), y el ejecutor de herramientas de H9 debe
tratarlo como dato opaco, no como texto para el modelo (ver `Seguridad`).

### Ejemplos trabajados (los 29 restantes siguen el mismo patrón; tech-lead los parte en tareas por capacidad
contra el `openapi.yaml` real de cada servicio)

**Sin credencial — `vinculo.consultar_estado_v1`** (entrada/salida tomadas de `wa-channel-service/delivery/api.md`):
```json
// entrada.parametros
{ "numero_whatsapp": "+5491112345678" }
// salida.resultado (passthrough de VinculoEstadoResponse)
{ "vinculado": true, "estado": "ACTIVO" }
```

**Con credencial — `tarjetas.listar_tarjetas_v1`**:
```json
// entrada.contexto.credencial requerido; parametros vacío ({})
// salida.resultado: lista passthrough de TarjetaResponse
[{ "id": "...", "ultimosCuatro": "1234", "marca": "VISA", "tipo": "CREDITO", "estado": "ACTIVA" }]
```

**Con doble confirmación — `cuentas.confirmar_transferencia_v1`**:
```json
// entrada.parametros
{ "transferencia_id": "a902f7b6-...", "confirmacion": "CONFIRMAR" }
// salida.resultado (primera confirmación)
{ "transferencia_id": "a902f7b6-...", "estado": "CONFIRMADA" }
// salida.resultado (segunda confirmación)
{ "transferencia_id": "a902f7b6-...", "estado": "EJECUTADA", "comprobante": { "...": "..." } }
```
H7 no decide la doble confirmación (es regla de H4); solo pasa la respuesta tal cual y, si la segunda confirmación
es irreversible, el texto de aviso lo redacta H9 a partir de `estado`, nunca H7.

## Seguridad — recorrido de la credencial de sesión

1. H0.2 invoca a H9 (`POST /api/v1/agent/turn`, contrato provisional en `platform/wa-channel-orchestrator/H9.md`)
   con `customerAddress`, `body` y `context.technicalContext` — **sin credencial**: H0.2 no guarda credenciales
   (confirmado en `shared/contracts/wa-channel-orchestrator/handoff.md`, línea "No guardar credenciales/conversación
   en contexto técnico").
2. H9 resuelve la credencial vigente desde **su propia memoria de sesión en Redis** (`RedisSessionMemoryService`,
   clave por conversación/número), nunca desde el turno de H0.2 ni desde el prompt del modelo. Si no hay credencial
   vigente, el flujo de herramientas de H9 (código, no el LLM) dispara `vinculo.verificar_identidad` →
   `acceso.emitir_credencial` y cachea el resultado.
3. Cuando el modelo decide invocar una capacidad de producto, el **ejecutor de herramientas de H9** (no el modelo)
   completa `contexto.credencial` antes de llamar al tool MCP. El modelo nunca ve ni genera ese valor — así se
   cumple que la credencial no viaja por el prompt (regla de `llm-security`).
4. H7 recibe la capacidad con `contexto.credencial`. Si la capacidad es de producto (H4/H5/H6) y falta la
   credencial, H7 **rechaza sin llamar al servicio origen** con `CREDENCIAL_REQUERIDA` → mensaje
   *"Por seguridad, volvé a verificar tu identidad para continuar"* (exactamente el texto de la spec). Si está
   presente, H7 la reenvía como header `X-Credencial-Sesion` al servicio (H4/H5/H6 la validan ellos mismos contra
   H2); para H3 arma los headers puntuales (`X-Canal-Credencial-Valida`, `X-Credencial-Documento-Tipo/Numero`) que
   ese servicio espera, derivados de la última `acceso.validar_credencial` exitosa.
5. Revocación: cuando H2 revoca una credencial (`acceso.revocar_credencial`, o en cascada desde
   `vinculo.bloquear`/`vinculo.desvincular`), la siguiente llamada a H4/H5/H6 con esa credencial devuelve 401/403;
   H7 lo traduce a rechazo de negocio de inmediato (sin caché propia de vigencia) — así se cumple "deja de
   autorizar en el mismo minuto operativo" sin que H7 necesite sincronizar estado con H2.

Guardrails adicionales (`llm-security`):
- Contenido que llega del cliente (texto libre) y de H8 se tratan como datos en H9, nunca como instrucciones; eso
  es responsabilidad de H9, pero H7 refuerza la agencia acotada: **solo** ejecuta capacidades publicadas y
  habilitadas (`catalog.py` con flag `habilitada`, configurable por `MCP_CAPABILITY_<NOMBRE>_ENABLED`); una
  capacidad no publicada o deshabilitada se rechaza sin tocar ningún servicio (requisito de la spec).
- PII en logs/auditoría: documento y número de WhatsApp enmascarados (`***1234`) con `security/pii.py`; la
  credencial nunca se logea, ni completa ni parcial.
- Topes: timeout de conexión/lectura por cliente (configurable, default connect 2 s / read 5 s, igual a lo que ya
  usan H4/H5 contra H2) y sin reintentos automáticos de operaciones con dinero (transferencias) — un reintento lo
  decide el cliente vía doble confirmación, no H7.

## Traducción de errores a mensajes de negocio

H1–H6 ya comparten un contrato de error único (`code`, `message`, `details`, `timestamp` — confirmado en varios
`delivery/api.md`). H7 lo re-mapea a una taxonomía de negocio más chica y estable para H9 (`errors.py`):

| HTTP / code origen | Motivo de negocio H7 | Mensaje para el agente |
|---|---|---|
| 401 (cualquier servicio) | `CREDENCIAL_INVALIDA` | "Por seguridad, volvé a verificar tu identidad para continuar" |
| 403 vínculo inactivo | `VINCULO_INACTIVO` | "Por seguridad, volvé a verificar tu identidad para continuar" |
| 400 `VALIDATION_ERROR` / `BAD_REQUEST` | `SOLICITUD_INVALIDA` | mensaje de `details` reformulado sin jerga técnica |
| 404 | `NO_ENCONTRADO` | mensaje empático ("no encontramos ese dato") |
| 409 `CONFLICT` / 423 `LOCKED` | `CONFLICTO` | mensaje de negocio puntual (p. ej. doble confirmación, intentos agotados → soporte) |
| 5xx / timeout / sin respuesta | `SERVICIO_NO_DISPONIBLE` | mensaje empático + alternativa (soporte, reintentar más tarde) |
| capacidad no publicada/deshabilitada | `CAPACIDAD_NO_DISPONIBLE` | rechazo antes de invocar cualquier servicio |

Nunca se exponen `details` técnicos tal cual, nombres de campo internos ni SQL; `errors.py` solo deja pasar el
texto ya pensado para cliente final que cada servicio redacta (varios ya lo hacen así, p. ej. H6 "Consultá al
titular o acudí a un canal formal...").

## Auditoría y observabilidad

- Sin base de datos propia (no está en el stack de H7): cada invocación (éxito o falla) emite un evento
  `structlog` JSON con `correlation_id`, `capacidad`, `version`, `servicio_origen`, `actor` (número enmascarado),
  `resultado` (éxito/código de error), `duracion_ms` — nunca la credencial ni el cuerpo de la solicitud/respuesta.
- "Consulta de trazas por soporte" (requisito de la spec) se resuelve agregando esos logs en el pipeline de logs
  del banco (fuera del alcance de H7); si más adelante se pide una consulta propia, es una capacidad nueva de
  soporte, no una tabla dentro de H7.
- `correlation_id` viaja desde H9 (quien lo recibe de H0.2 en el turno) hasta cada llamada HTTP a H1–H6 solo como
  dato de auditoría/log (H1–H6 no aceptan ese header hoy — confirmado: ninguno lo documenta en su `api.md`); no se
  inventa soporte de header en servicios que no lo tienen.
- Métricas mínimas por capacidad: latencia y tasa de error por capacidad y servicio origen (vía logs
  estructurados + cualquier colector que agregue por `capacidad`/`resultado`); sin SLA de latencia definido en la
  fuente (queda como pregunta abierta, ver abajo).

## Configuración (`.env.example`)

```
# Puertos
PORT=8101

# H1–H6 (URLs + timeouts, mismo patrón que ya usan H4/H5 contra H2)
USERS_SERVICE_BASE_URL=http://localhost:8080
CREDENTIALS_SERVICE_BASE_URL=http://localhost:8082
CHANNEL_SERVICE_BASE_URL=http://localhost:8081
ACCOUNTS_SERVICE_BASE_URL=http://localhost:8084
CARDS_SERVICE_BASE_URL=http://localhost:8086
INSURANCE_SERVICE_BASE_URL=http://localhost:8085
MCP_CLIENTS_CONNECT_TIMEOUT_MS=2000
MCP_CLIENTS_READ_TIMEOUT_MS=5000

# Flags de capacidad (default true salvo las de banco/soporte)
MCP_CAPABILITY_ACCESO_REVOCAR_CREDENCIALES_MASIVO_ENABLED=false
```

Sin secretos en el repo (no hay credenciales de servicio ni API keys en esta capa; las URLs son internas de red).

## ADRs requeridos

- `docs/adr/ADR-009-agente-opera-solo-via-capa-mcp.md` — el agente (H9) nunca llama directo a H1–H6; toda acción
  pasa por el catálogo publicado de H7.
- `docs/adr/ADR-010-credencial-de-sesion-fuera-del-prompt.md` — la credencial H2 vive en la memoria de sesión de
  H9 (Redis) y se adjunta por código al invocar una capacidad; H7 no la persiste ni la logea, y no valida su firma
  (eso ya lo hacen H4/H5/H6 y, para H3, la resuelve a partir de la última validación exitosa).

(Los otros dos ADRs que pide la skill — pgvector/Redis de H8 y el contrato de turno H0.2↔H9 — son decisiones de
H8/H9, fuera del alcance de este hito; el segundo ya tiene una definición provisional documentada en
`platform/wa-channel-orchestrator/H9.md`, pendiente de publicación formal por el arquitecto de H9.)

## Riesgos / decisiones pendientes (de las preguntas abiertas de la spec)

- **Latencia máxima acumulada**: no definida en la fuente; queda parametrizable por los timeouts de `clients/`
  (connect 2 s / read 5 s por defecto, igual que H4/H5 contra H2) sin un SLA de punta a punta todavía.
- **Retención de trazas**: al no haber DB propia, la retención depende del pipeline de logs del banco; no hay
  valor definido en H7.
- **Mecanismo de habilitar/deshabilitar capacidades**: resuelto a nivel de variable de entorno
  (`MCP_CAPABILITY_<NOMBRE>_ENABLED`) por simplicidad y porque no se pidió un endpoint de administración; si el
  banco quiere un panel u operación en caliente, es un cambio explícito a pedir.
- **Formato del catálogo**: Markdown (`capabilities.md`, humano y versionable) + JSON Schema por capacidad en
  `schemas/`, siguiendo el mismo patrón que `shared/contracts/events/` (README + schema.json por evento).
- **Estrategia de versionado**: versión en el nombre de la herramienta MCP (`_v1`, `_v2`), sin semver adicional;
  se mantiene la anterior hasta que H9 confirme la migración.

## Verificación

- `ruff check` / `ruff format --check` y `mypy --strict` sobre `src/` (cuando exista el código).
- `pytest` unitario: catálogo (capacidad publicada/no publicada/deshabilitada), mapeo de errores, enmascarado PII,
  armado de headers por servicio (H3 vs H4/H5/H6).
- `pytest` de integración contra H1–H6 levantados localmente (docker-compose de cada servicio) para al menos un
  camino feliz y un 401/403 por capacidad crítica (emitir credencial, listar tarjetas, confirmar transferencia).
- No aplica `adk eval` (es H9); no aplica verificación de pgvector (es H8).
