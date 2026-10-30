# Guía de capacidades — wa-mcp-layer (H7)

Cómo se invoca cada capacidad de H7, qué parámetros acepta y qué devuelve. El contrato publicado
es la fuente de verdad para consumidores:
[`shared/contracts/wa-mcp-layer/capabilities.md`](../../../shared/contracts/wa-mcp-layer/capabilities.md)
y los esquemas `schemas/<dominio>.<accion>.v1.schema.json`.

> **Estado del transporte.** Hoy `python -m wa_mcp_layer.server` solo sirve `GET /health`; el
> transporte FastMCP Streamable HTTP en `/mcp` todavía no está implementado (`GET /mcp` → `404`).
> Las capacidades se invocan desde código Python como se muestra acá. El catálogo y los esquemas ya
> están publicados y son estables.

## Reglas comunes

- **Nombre**: `<dominio>.<accion>` (`cuentas.consultar_saldo`). La herramienta MCP asociada es
  `<dominio>_<accion>_v1` (`Capability.tool_name`).
- **Contexto de sesión** (`ContextoSesion`): `numero_whatsapp` (E.164), `correlation_id` y
  `credencial` (JWT H2 opaco o `null`). La credencial la adjunta el ejecutor de H9 desde su memoria
  de sesión; nunca la genera el modelo ni viaja en el prompt.
- **Parámetros**: los modelos Pydantic usan `extra="forbid"`; un campo de más o un valor inválido
  devuelve `SOLICITUD_INVALIDA` sin llamar al servicio.
- **Publicación y actor**: `Catalog.invoke` aplica `resolve(nombre, actor=...)`, que devuelve la
  capacidad solo si está en el catálogo, habilitada por flag y el actor coincide. Si no, se rechaza
  con `CAPACIDAD_NO_DISPONIBLE` antes de tocar cualquier servicio. El actor lo fija el consumidor
  confiable, nunca un argumento del modelo. H2/H4/H5/H6 se invocan por su clase: mientras el
  transporte MCP no exista, el consumidor es responsable de aplicar el actor y el flag declarados
  (la revocación masiva valida además su propio flag en `AccesoCapabilities`).
- **Credencial**: `require_credential` comprueba que exista y tenga forma de JWT (tres segmentos).
  La vigencia la decide el servicio: H4/H5/H6 reciben `X-Credencial-Sesion` y la validan contra H2;
  para H3 autenticado, H7 valida contra H2 y arma los headers `X-Canal-Credencial-Valida` y
  `X-Credencial-Documento-Tipo/Numero`.
- **Rutas fijas**: los identificadores que van al path (`numero`, `tarjetaId`, `poliza_id`,
  `numero_caso`, `transferencia_id`) se validan con regex/UUID para que una entrada no pueda cambiar
  la ruta HTTP.
- **Sin reintentos**: cada invocación es una sola petición. Los timeouts se configuran con
  `MCP_CLIENTS_CONNECT_TIMEOUT_MS` / `MCP_CLIENTS_READ_TIMEOUT_MS`.

## Cómo se invoca

`Catalog.invoke` despacha **solo H1 y H3**. H2, H4, H5 y H6 se invocan con las clases de
`capabilities/*`, que reciben su cliente HTTP.

### H1/H3 — con el catálogo

```python
import asyncio

from pydantic import SecretStr

from wa_mcp_layer.catalog import Catalog
from wa_mcp_layer.clients.channel_client import ChannelClient
from wa_mcp_layer.clients.credentials_client import CredentialsClient
from wa_mcp_layer.clients.users_client import UsersClient
from wa_mcp_layer.envelope import ContextoSesion
from wa_mcp_layer.settings import Settings


async def main() -> None:
    settings = Settings()
    contexto = ContextoSesion(
        numero_whatsapp="+5491100000000",
        correlation_id="demo-0001",
        credencial=SecretStr("aaa.bbb.ccc"),  # ejemplo; en runtime la adjunta H9
    )
    async with (
        UsersClient(settings) as users,
        ChannelClient(settings) as channel,
        CredentialsClient(settings) as credentials,
    ):
        catalog = Catalog(users, channel, credentials)
        resultado = await catalog.invoke("vinculo.consultar_estado", contexto, {}, actor="agente")
        print(resultado.model_dump_json())


asyncio.run(main())
```

### H2/H4/H5/H6 — con la clase de capacidades

Asumiendo el `contexto` y `settings` del ejemplo anterior:

```python
from wa_mcp_layer.capabilities.cuentas import CuentasCapabilities
from wa_mcp_layer.clients.accounts_client import AccountsClient
from wa_mcp_layer.settings import Settings

settings = Settings()
async with AccountsClient(settings) as client:
    resultado = await CuentasCapabilities(client).consultar_saldo(contexto, {"numero": "0000000001"})
```

Clases y clientes por dominio:

| Dominio | Clase | Cliente | Constructor de la clase |
|---|---|---|---|
| H2 | `capabilities.acceso.AccesoCapabilities` | `CredentialsClient` | `AccesoCapabilities(client, settings)` |
| H4 | `capabilities.cuentas.CuentasCapabilities` | `AccountsClient` | `CuentasCapabilities(client)` |
| H5 | `capabilities.tarjetas.TarjetasCapabilities` | `CardsClient` | `TarjetasCapabilities(client)` |
| H6 | `capabilities.seguros.SegurosCapabilities` | `InsuranceClient` | `SegurosCapabilities(client)` |

Cada método devuelve `Resultado` (éxito) o un fallo (`Fallo`/`Rechazo`, según el módulo) con
`error` + `auditoria`; serializables con `model_dump(mode="json")`.

## Catálogo por dominio

### H1 — identidad (2 capacidades, actor `soporte`, sin credencial)

| Capacidad | Parámetros | Efecto |
|---|---|---|
| `identidad.consultar_cliente` | `tipo` (1–20, `[A-Za-z0-9_-]`), `numero` (1–30, `[A-Za-z0-9-]`) | `GET /api/v1/users/{tipo}/{numero}` |
| `identidad.consultar_estado_cliente` | igual | `GET /api/v1/users/{tipo}/{numero}/status` |

No se ofrecen al agente conversacional: `resolve(..., actor="agente")` las rechaza.

### H2 — acceso / credenciales (5 capacidades)

| Capacidad | Parámetros | Credencial | Efecto |
|---|---|---|---|
| `acceso.emitir_credencial` | `documentoTipo` (1–20), `documentoNumero` (1–30), `vinculoActivo: true`, `identidadVerificada: true` | No | `POST /api/v1/credenciales/emitir`; agrega `numeroWhatsapp` desde el contexto |
| `acceso.validar_credencial` | ninguno | Recibe la credencial del contexto | `POST /api/v1/credenciales/validar`; devuelve `vigente`, `numeroWhatsapp`, `documentoTipo`, `documentoNumero` |
| `acceso.revocar_credencial` | `motivo`: `BLOQUEO` \| `DESVINCULACION` \| `FRAUDE` \| `POLITICA_BANCO` \| `REVOCACION_MASIVA` \| `REEMPLAZO`; `solicitadoPor`: `CANAL` (default) \| `BANCO` \| `SISTEMA` | No | `POST /api/v1/credenciales/{numero}/revocacion` con el número del contexto |
| `acceso.revocar_credenciales_masivo` | `criterio`: `TODAS` \| `POR_CLIENTE`; `segmento`: `{documentoTipo, documentoNumero}` solo con `POR_CLIENTE` | No | `POST /api/v1/credenciales/revocacion-masiva`; **deshabilitada por defecto** |
| `acceso.consultar_historial_credencial` | ninguno | No | `GET /api/v1/credenciales/{numero}/historial` (actor `soporte`) |

`acceso.emitir_credencial` supone que H3 ya resolvió vínculo e identidad: no repite ese flujo. No
hay capacidad de "renovar": la renovación es un job interno de H2; ante un 401 por renovación se
deriva a `vinculo.verificar_identidad` → `acceso.emitir_credencial`.

### H3 — vínculo / verificación (8 capacidades)

Todas aceptan `numero_whatsapp` opcional. Para el actor `agente` debe coincidir con el número del
contexto (si no, `VINCULO_INACTIVO`); `soporte` puede consultar otro número. El número no se envía
en el body: va en el path (o como `numeroWhatsapp` en `aceptar_terminos`).

| Capacidad | Parámetros adicionales | Credencial | Efecto |
|---|---|---|---|
| `vinculo.aceptar_terminos` | — | No | `POST /api/v1/vinculos/terminos` |
| `vinculo.verificar_identidad` | `documentoTipo` (1–20), `documentoNumero` (1–30), `datosVerificacion` (objeto string→string, hasta 32 claves) | No | `POST /api/v1/vinculos/{numero}/verificaciones` |
| `vinculo.consultar_estado` | — | No | `GET /api/v1/vinculos/{numero}/estado` |
| `vinculo.consultar_perfil` | — | Sí | `GET /api/v1/vinculos/{numero}/perfil` con headers de identidad validada |
| `vinculo.actualizar_preferencias` | `idioma` (opcional, máx. 20), `notificacionesHabilitadas` (opcional, bool), `horarioPreferido` (opcional, máx. 100) | Sí | `PATCH /api/v1/vinculos/{numero}/preferencias` |
| `vinculo.bloquear` | `confirmacion: true`, `motivo: "SOLICITUD_CLIENTE"` | Sí | `PATCH /api/v1/vinculos/{numero}/bloqueo` |
| `vinculo.desvincular` | `confirmacion: true` | Sí | `PATCH /api/v1/vinculos/{numero}/desvinculacion` |
| `vinculo.consultar_historial` | — | No | `GET /api/v1/vinculos/{numero}/historial` (actor `soporte`) |

`vinculo.bloquear` solo acepta `SOLICITUD_CLIENTE`; `POLITICA_BANCO` es una operación interna que
el agente no puede elegir.

### H4 — cuentas y transferencias (6 capacidades, actor `agente`, con credencial)

| Capacidad | Parámetros | Efecto |
|---|---|---|
| `cuentas.listar_cuentas` | ninguno | `GET /api/v1/cuentas` |
| `cuentas.consultar_saldo` | `numero` (1–34, `[A-Za-z0-9_-]`) | `GET /api/v1/cuentas/{numero}/saldo` |
| `cuentas.consultar_movimientos` | `numero`; `desde`/`hasta` (opcional, ISO 8601); `tipo` (opcional): `DEBITO` \| `CREDITO`; `limit` (opcional, > 0); `cursor` (opcional) | `GET /api/v1/cuentas/{numero}/movimientos` (query) |
| `cuentas.listar_terceros` | ninguno | `GET /api/v1/terceros` |
| `cuentas.preparar_transferencia` | `cuentaOrigen` (1–34), `cuentaDestino` (opcional, máx. 34), `terceroId` (opcional, UUID), `monto` (≥ 0.01), `moneda`: `ARS` \| `USD`, `concepto` (opcional, máx. 255) | `POST /api/v1/transferencias` |
| `cuentas.confirmar_transferencia` | `transferencia_id` (UUID), `confirmacion` (texto) | `POST /api/v1/transferencias/{id}/confirmar` |

La doble confirmación es regla de H4: H7 reenvía `confirmacion` tal cual y devuelve el estado y el
comprobante sin interpretarlos ni ejecutar otro paso. No hay reintentos automáticos de operaciones
con dinero.

### H5 — tarjetas (6 capacidades, actor `agente`, con credencial)

| Capacidad | Parámetros | Efecto |
|---|---|---|
| `tarjetas.listar_tarjetas` | ninguno | `GET /api/v1/tarjetas` |
| `tarjetas.consultar_consumos` | `tarjetaId` (UUID); `dias`, `limit`, `cursor` (opcionales) | `GET /api/v1/tarjetas/{id}/consumos` (query) |
| `tarjetas.bloquear_temporal` | `tarjetaId`; `confirmar` (opcional, texto) | `POST /api/v1/tarjetas/{id}/bloquear/temporal` |
| `tarjetas.desbloquear_temporal` | `tarjetaId`; `validacionAdicional` (opcional, bool, default `false`) | `POST /api/v1/tarjetas/{id}/desbloquear/temporal` |
| `tarjetas.bloquear_definitivo` | `tarjetaId`; `motivo`, `confirmarPrimero`, `confirmarSegundo` (opcionales, texto) | `POST /api/v1/tarjetas/{id}/bloquear/definitivo` |
| `tarjetas.registrar_aviso_viaje` | `tarjetaId`; `pais` (1–255), `region` (opcional, máx. 255), `fechaDesde`, `fechaHasta` (fecha) | `POST /api/v1/tarjetas/{id}/aviso-viaje` |

El `tarjetaId` viaja en el path y también en el body del POST, como define el OpenAPI de H5. Las
confirmaciones se reenvían sin inventar ni reintentar.

### H6 — seguros (5 capacidades, actor `agente`, con credencial)

| Capacidad | Parámetros | Efecto |
|---|---|---|
| `seguros.listar_polizas` | ninguno | `GET /api/v1/seguros` |
| `seguros.consultar_coberturas` | `poliza_id` (UUID) | `GET /api/v1/seguros/{id}/coberturas` |
| `seguros.denunciar_siniestro` | `polizaId` (UUID), `tipoSiniestro`: `HOGAR` \| `AUTO` \| `VIDA` \| `OTROS`, `fechaSiniestro` (fecha), `descripcion` (1–2000) | `POST /api/v1/siniestros` (éxito 201) |
| `seguros.consultar_siniestro` | `numero_caso` (1–64, `[A-Za-z0-9_-]`) | `GET /api/v1/siniestros/{numeroCaso}` |
| `seguros.consultar_documentacion_pendiente` | `numero_caso` | `GET /api/v1/siniestros/{numeroCaso}/documentacion-pendiente` |

La denuncia reenvía `polizaId`, `tipoSiniestro`, `fechaSiniestro` y `descripcion` tal cual; el
número de caso y los próximos pasos se devuelven sin interpretar ni reintentar.

## Ejemplos de entrada/salida

### Éxito sin credencial — `vinculo.consultar_estado_v1`

```json
{
  "contexto": {
    "numero_whatsapp": "+5491100000000",
    "credencial": null,
    "correlation_id": "demo-0001"
  },
  "parametros": {}
}
```

```json
{
  "resultado": {"vinculado": true, "estado": "ACTIVO"},
  "auditoria": {
    "capacidad": "vinculo.consultar_estado",
    "version": 1,
    "correlation_id": "demo-0001",
    "timestamp": "2026-10-30T12:00:00Z"
  }
}
```

### Éxito con credencial — `cuentas.consultar_saldo_v1`

```json
{
  "contexto": {
    "numero_whatsapp": "+5491100000000",
    "credencial": "<JWT H2>",
    "correlation_id": "demo-0002"
  },
  "parametros": {"numero": "0000000001"}
}
```

El `resultado` es la respuesta de H4 sin transformar (saldo y datos de la cuenta).

### Doble confirmación — `cuentas.confirmar_transferencia_v1`

```json
{
  "contexto": {
    "numero_whatsapp": "+5491100000000",
    "credencial": "<JWT H2>",
    "correlation_id": "demo-0003"
  },
  "parametros": {
    "transferencia_id": "a902f7b6-0000-4000-8000-000000000001",
    "confirmacion": "CONFIRMAR"
  }
}
```

H7 devuelve el `estado` que informe H4 (por ejemplo `CONFIRMADA` en la primera confirmación y
`EJECUTADA` con comprobante en la segunda). El texto de aviso al cliente lo redacta H9.

### Bloqueo de tarjeta — `tarjetas.bloquear_temporal_v1`

```json
{
  "contexto": {
    "numero_whatsapp": "+5491100000000",
    "credencial": "<JWT H2>",
    "correlation_id": "demo-0004"
  },
  "parametros": {"tarjetaId": "6f1c2a10-0000-4000-8000-000000000002"}
}
```

### Denuncia de siniestro — `seguros.denunciar_siniestro_v1`

```json
{
  "contexto": {
    "numero_whatsapp": "+5491100000000",
    "credencial": "<JWT H2>",
    "correlation_id": "demo-0005"
  },
  "parametros": {
    "polizaId": "0b7d9e20-0000-4000-8000-000000000003",
    "tipoSiniestro": "HOGAR",
    "fechaSiniestro": "2026-10-29",
    "descripcion": "Filtración de agua en el baño"
  }
}
```

### Error — sin credencial en una capacidad de producto

```json
{
  "error": {
    "codigo": "CREDENCIAL_REQUERIDA",
    "mensaje": "Por seguridad, volvé a verificar tu identidad para continuar",
    "reintentable": false
  },
  "auditoria": {
    "capacidad": "tarjetas.bloquear_temporal",
    "version": 1,
    "correlation_id": "demo-0006",
    "timestamp": "2026-10-30T12:00:00Z"
  }
}
```

### Error — servicio caído o timeout

```json
{
  "error": {
    "codigo": "SERVICIO_NO_DISPONIBLE",
    "mensaje": "No podemos atender la solicitud ahora. Intentá más tarde o contactá a soporte.",
    "reintentable": true
  },
  "auditoria": {
    "capacidad": "cuentas.listar_cuentas",
    "version": 1,
    "correlation_id": "demo-0007",
    "timestamp": "2026-10-30T12:00:00Z"
  }
}
```

### Error — capacidad no publicada, deshabilitada o actor incorrecto

```json
{
  "error": {
    "codigo": "CAPACIDAD_NO_DISPONIBLE",
    "mensaje": "Esta operación no está disponible.",
    "reintentable": false
  },
  "auditoria": {
    "capacidad": "catalogo.desconocida",
    "version": 1,
    "correlation_id": "demo-0008",
    "timestamp": "2026-10-30T12:00:00Z"
  }
}
```

No se devuelven stack traces, SQL, nombres internos ni detalles técnicos. La taxonomía completa está
en el [README](../README.md#errores-de-negocio).

## Habilitar y deshabilitar capacidades

El catálogo tiene un flag `habilitada` por capacidad y, además, cada una se puede apagar por
entorno:

```powershell
$env:MCP_CAPABILITY_CUENTAS_LISTAR_CUENTAS_ENABLED = "false"
```

`acceso.revocar_credenciales_masivo` nace deshabilitada
(`MCP_CAPABILITY_ACCESO_REVOCAR_CREDENCIALES_MASIVO_ENABLED=false`) y su actor es `banco`: el agente
no puede invocarla. El flag se evalúa en `resolve` (despacho por catálogo, hoy H1/H3) y, para la
revocación masiva, en `AccesoCapabilities`. Las clases de H4/H5/H6 no consultan el flag: si se
invocan directamente, el control de habilitación queda en el consumidor.

## Agregar una capacidad

1. Implementar el handler en `src/wa_mcp_layer/capabilities/<dominio>.py`, con un modelo Pydantic
   de parámetros (`extra="forbid"`), validación de identificadores de path y `require_credential`
   si es de producto.
2. Registrarla en `src/wa_mcp_layer/catalog.py` (`_cap(...)` con nombre, actor, servicio, handler,
   `requiere_credencial` y `habilitada`). El catálogo mantiene el invariante de 32 entradas: si el
   número cambia, actualizarlo junto con el contrato.
3. Publicar el esquema `shared/contracts/wa-mcp-layer/schemas/<dominio>.<accion>.v1.schema.json`
   con el bloque `x-mcp` y agregar la fila en `shared/contracts/wa-mcp-layer/capabilities.md`.
4. Agregar tests en `tests/` (camino feliz, parámetros inválidos, sin credencial, error del
   servicio) y correr los gates del [README](../README.md#cómo-probarlo).
5. Si el cambio es incompatible, publicar `_v2` y mantener `_v1` hasta que H9 migre.
