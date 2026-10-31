# wa-mcp-layer (H7)

Capa MCP del canal bancario: el puente estructurado entre el agente conversacional (H9) y los
servicios H1–H6. Publica un catálogo versionado de **32 capacidades** (una por operación de H1–H6
relevada para el canal), exige la credencial de sesión (H2) en las capacidades de producto y delega
en H3/H4/H5/H6 la validación de vínculo y titularidad; traduce los errores de los servicios a
mensajes de negocio y audita cada invocación.

Python 3.12, administrado con [uv](https://docs.astral.sh/uv/).

## Estado del componente

- Implementado: catálogo (`catalog.py`), contrato publicado en
  [`shared/contracts/wa-mcp-layer/`](../../shared/contracts/wa-mcp-layer/capabilities.md), clientes
  HTTP hacia H1–H6, capacidades de H1/H2/H3/H4/H5/H6, traducción de errores, auditoría por logs y
  enmascarado de PII.
- El entrypoint `python -m wa_mcp_layer.server` sirve **solo `GET /health`** en el puerto 8101.
  El transporte FastMCP Streamable HTTP en `/mcp` **todavía no está implementado**: `GET /mcp`
  responde `404`. Por eso hoy las capacidades se invocan desde código Python (ver
  [docs/capacidades.md](docs/capacidades.md)), no desde un cliente MCP.

## Qué hace y qué no hace

| Hace | No hace |
|---|---|
| Expone como capacidades MCP las operaciones ya definidas en H1–H6, sin duplicar su lógica de negocio | No implementa reglas de producto: montos, límites, bloqueos y confirmaciones los decide H1–H6 |
| Valida la precondición de sesión (que exista credencial H2; que H3 tenga el contexto validado) antes de llamar al servicio | No emite ni firma credenciales (eso es H2); no valida la firma del JWT (lo hacen H4/H5/H6) |
| Traduce errores HTTP de los servicios a una taxonomía de negocio chica y estable | No redacta la conversación ni decide qué decirle al cliente (eso es H9) |
| Audita cada invocación con `correlationId`, capacidad, servicio, resultado y duración | No persiste estado de negocio ni trazas en una base propia: no tiene base de datos |
| Publica el catálogo y los esquemas JSON de entrada/salida en `shared/contracts/` | No conoce Twilio, Kafka ni el estado técnico del mensaje (H0/H0.1/H0.2); no usa la base de conocimiento (H8) |

## Cómo levantarlo

### Con uv (local)

Requiere Python 3.12 y `uv` en el `PATH`.

```powershell
cd ai/wa-mcp-layer
uv sync --frozen
uv run python -m wa_mcp_layer.server
```

El servidor queda escuchando en `http://localhost:8101`:

```powershell
curl.exe http://localhost:8101/health   # {"status":"ok"}
```

La configuración se lee del entorno y, opcionalmente, de un archivo `.env` local en esta carpeta
(copiarlo de `.env.example`). El proceso de health no llama a H1–H6; las URLs y timeouts se usan
cuando se invoca una capacidad desde código.

### Con Docker Compose

El stack completo de IA está en `ai/docker-compose.yml` (H7 + H1–H6 + Postgres/pgvector + Redis +
Agent Memory Server). Desde `ai/`:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
curl.exe http://localhost:8101/health
```

El servicio `wa-mcp-layer` publica `${MCP_HOST_PORT:-8101}:8101`, espera a que H1–H6 estén healthy y
tiene `HEALTHCHECK` contra `/health`. Las URLs internas (`http://wa-users-service:8080`, etc.) las
inyecta el compose; los secretos de la base y el JWT de H2 viven en `ai/.env`, pero H7 no los usa
(`Settings` ignora las variables que no declara).

Para construir solo la imagen (contexto `ai/wa-mcp-layer`):

```powershell
docker build -t wa-mcp-layer:local .
```

## Variables de entorno

Ninguna variable de este componente contiene secretos: no hay API keys ni credenciales de servicio.
La credencial de sesión (JWT H2) llega en el contexto de cada invocación, nunca por configuración.

| Variable | Para qué | Ejemplo |
|---|---|---|
| `PORT` | Puerto HTTP del servidor de health | `8101` |
| `USERS_SERVICE_BASE_URL` | URL base de H1 (`wa-users-service`) | `http://localhost:8080` |
| `CREDENTIALS_SERVICE_BASE_URL` | URL base de H2 (`wa-credentials-service`) | `http://localhost:8082` |
| `CHANNEL_SERVICE_BASE_URL` | URL base de H3 (`wa-channel-service`) | `http://localhost:8081` |
| `ACCOUNTS_SERVICE_BASE_URL` | URL base de H4 (`wa-accounts-service`) | `http://localhost:8084` |
| `CARDS_SERVICE_BASE_URL` | URL base de H5 (`wa-cards-service`) | `http://localhost:8086` |
| `INSURANCE_SERVICE_BASE_URL` | URL base de H6 (`wa-insurance-service`) | `http://localhost:8085` |
| `MCP_CLIENTS_CONNECT_TIMEOUT_MS` | Timeout de conexión por cliente HTTP (ms) | `2000` |
| `MCP_CLIENTS_READ_TIMEOUT_MS` | Timeout de lectura por cliente HTTP (ms) | `5000` |
| `MCP_CAPABILITY_<DOMINIO>_<ACCION>_ENABLED` | Habilita/deshabilita una capacidad (`true`/`false`) | `MCP_CAPABILITY_CUENTAS_LISTAR_CUENTAS_ENABLED=false` |
| `MCP_CAPABILITY_ACCESO_REVOCAR_CREDENCIALES_MASIVO_ENABLED` | Revocación masiva (nace deshabilitada) | `false` |

El nombre del flag se arma con el nombre de la capacidad en mayúsculas y `.` → `_`
(`cuentas.listar_cuentas` → `MCP_CAPABILITY_CUENTAS_LISTAR_CUENTAS_ENABLED`). Si la variable no
está definida se usa el flag `habilitada` del catálogo.

## Cómo probarlo

```powershell
cd ai/wa-mcp-layer
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest -q --cov=src --cov-fail-under=80
```

Desde la raíz del repo, el gate del equipo corre lo mismo para todos los componentes Python de IA:

```powershell
python ai/verify.py
```

Las pruebas usan `httpx.MockTransport`/`respx` con datos sintéticos: no hay red, servicios
levantados ni modelo. `pytest` excluye por defecto el marker `evals`; hoy no hay tests de
integración ni evals para H7 (los markers `integration` y `evals` están declarados en
`pyproject.toml`). CI: `.github/workflows/ai.yml` (calidad, `verify.py`, build de imagen y
`docker compose config`).

## Contrato publicado

Fuente de verdad para H9 y otros consumidores:
[`shared/contracts/wa-mcp-layer/capabilities.md`](../../shared/contracts/wa-mcp-layer/capabilities.md).

- **32 capacidades v1** con convención `<dominio>.<accion>`; la herramienta MCP se registra como
  `<dominio>_<accion>_v1` (`Capability.tool_name`).
- **Esquemas** JSON Schema draft-07 en `shared/contracts/wa-mcp-layer/schemas/`: `_envelope.json`
  (sobre común) y `<dominio>.<accion>.v1.schema.json` por capacidad. Cada esquema lleva el bloque
  `x-mcp` (nombre, versión, servicio, actor, handler, `requiere_credencial`, `habilitada`,
  `tool_name`) alineado 1:1 con `catalog.CATALOG`.
- **Sobre común**: entrada `{contexto: {numero_whatsapp, credencial, correlation_id}, parametros}`;
  salida `{resultado, auditoria}` o `{error, auditoria}`. `contexto.credencial` es sensible: nunca
  se logea ni se repite en el resultado.
- **Publicación y rechazo**: en el despacho por catálogo (H1/H3), `resolve(nombre, actor=...)`
  devuelve la capacidad solo si está publicada, habilitada y el actor coincide; si no, la invocación
  se rechaza con `CAPACIDAD_NO_DISPONIBLE` sin llamar a ningún servicio. Mientras el transporte MCP
  no exista, H2/H4/H5/H6 se invocan por su clase y el consumidor es responsable de aplicar el actor
  y el flag declarados en el catálogo (la revocación masiva sí valida su propio flag en
  `AccesoCapabilities`).
- **Versionado**: un cambio incompatible se publica como `_v2` conviviendo con `_v1` hasta que H9
  migre.
- **Actores**: `agente` (H9), `soporte` y `banco`. Las capacidades de `soporte`/`banco` están en el
  catálogo; `Catalog.invoke` no las despacha al agente (`resolve` con actor `agente` devuelve
  `None`).

Guía de uso por dominio, parámetros y ejemplos: [docs/capacidades.md](docs/capacidades.md).

## Operación

- **Health**: `GET /health` → `{"status":"ok"}`. Cualquier otra ruta responde `404` (incluido
  `/mcp` mientras el transporte MCP no exista). `Dockerfile` y `docker-compose.yml` usan ese
  endpoint como `HEALTHCHECK`.
- **Logs de auditoría**: cada invocación emite un evento JSON `invocacion_capacidad` con
  `timestamp`, `correlation_id`, `capacidad`, `version`, `servicio_origen`, `actor` (número de
  WhatsApp enmascarado), `resultado` (`EXITO` o el código de error) y `duracion_ms`. Nunca incluye
  la credencial, los parámetros ni el cuerpo de la respuesta.
- **PII**: el número de WhatsApp y los documentos se enmascaran (`***1234`) antes de loguear. No
  hay datos reales de clientes en código, tests ni ejemplos.
- **Trazabilidad**: `correlation_id` viaja desde H9 en el contexto y aparece en la respuesta
  (`auditoria`) y en el log de la invocación. H1–H6 no reciben ese header; H7 no inventa soporte
  donde el servicio no lo tiene.
- **Sin métricas propias**: latencia y tasa de error por capacidad se obtienen agregando el log
  estructurado por `capacidad`/`resultado` en el pipeline de logs del banco.
- **Sin reintentos**: los clientes no reintentan; un timeout o 5xx se devuelve como
  `SERVICIO_NO_DISPONIBLE` (`reintentable: true`). Las operaciones con dinero no se reintentan
  automáticamente.

### Errores de negocio

Taxonomía estable de `errors.py` (nunca se exponen stack traces, SQL ni nombres internos):

| Código | Cuándo | Reintentable |
|---|---|---|
| `CREDENCIAL_REQUERIDA` | La capacidad de producto no recibió credencial | No |
| `CREDENCIAL_INVALIDA` | 401 del servicio, credencial mal formada o identidad no vigente | No |
| `VINCULO_INACTIVO` | 403 del servicio | No |
| `SOLICITUD_INVALIDA` | 400 o parámetros que no validan | No |
| `NO_ENCONTRADO` | 404 | No |
| `CONFLICTO` | 409 / 423 | No |
| `SERVICIO_NO_DISPONIBLE` | 5xx, timeout o respuesta no JSON | Sí |
| `CAPACIDAD_NO_DISPONIBLE` | No publicada, deshabilitada o actor incorrecto en el despacho por catálogo; revocación masiva apagada | No |

Errores frecuentes al operar: `CREDENCIAL_REQUERIDA` suele indicar que el ejecutor de H9 no adjuntó
la credencial al contexto; `SERVICIO_NO_DISPONIBLE` que el servicio destino no está levantado o la
URL base apunta mal; `CAPACIDAD_NO_DISPONIBLE` que el flag `MCP_CAPABILITY_*_ENABLED` está en
`false` o que se invocó con un actor distinto del declarado.
