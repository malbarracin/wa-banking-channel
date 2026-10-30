# Catálogo de capacidades MCP v1 — wa-mcp-layer (H7)

Contrato publicado para H9 y otros consumidores MCP. Convención de nombre:
`<dominio>.<accion>`; herramienta MCP registrada como `<dominio>_<accion>_v1`.
Cambio incompatible = capacidad `_v2` conviviendo con `_v1` hasta que el consumidor migre.

Esquemas JSON Schema draft-07 en `schemas/`. El sobre común está en
`schemas/_envelope.json`. Cada capacidad tiene `schemas/<dominio>.<accion>.v1.schema.json`
con `entrada.parametros` y salida (`resultado` | `error` + `auditoria`).

El registro runtime (`ai/wa-mcp-layer/.../catalog.py`) declara para cada entrada:
`nombre`, `version`, `servicio`, `requiere_credencial`, `actor`, `handler`, `habilitada`.
Deshabilitar en caliente: `MCP_CAPABILITY_<DOMINIO>_<ACCION>_ENABLED=false`
(por defecto `acceso.revocar_credenciales_masivo` está deshabilitada).

## Catálogo (32 capacidades)

| Capacidad | v | Servicio | Operación | Requiere credencial | Actor | Handler | Schema |
|---|---|---|---|---|---|---|---|
| `identidad.consultar_cliente` | 1 | H1 | `GET /api/v1/users/{tipo}/{numero}` | No | soporte | `consultar_cliente` | [schema](schemas/identidad.consultar_cliente.v1.schema.json) |
| `identidad.consultar_estado_cliente` | 1 | H1 | `GET /api/v1/users/{tipo}/{numero}/status` | No | soporte | `consultar_estado_cliente` | [schema](schemas/identidad.consultar_estado_cliente.v1.schema.json) |
| `acceso.emitir_credencial` | 1 | H2 | `POST /api/v1/credenciales/emitir` | No | agente | `emitir_credencial` | [schema](schemas/acceso.emitir_credencial.v1.schema.json) |
| `acceso.validar_credencial` | 1 | H2 | `POST /api/v1/credenciales/validar` | Recibe la credencial a validar | agente | `validar_credencial` | [schema](schemas/acceso.validar_credencial.v1.schema.json) |
| `acceso.revocar_credencial` | 1 | H2 | `POST /api/v1/credenciales/{numero}/revocacion` | No | agente | `revocar_credencial` | [schema](schemas/acceso.revocar_credencial.v1.schema.json) |
| `acceso.revocar_credenciales_masivo` | 1 | H2 | `POST /api/v1/credenciales/revocacion-masiva` | No | banco | `revocar_credenciales_masivo` | [schema](schemas/acceso.revocar_credenciales_masivo.v1.schema.json) |
| `acceso.consultar_historial_credencial` | 1 | H2 | `GET /api/v1/credenciales/{numero}/historial` | No | soporte | `consultar_historial_credencial` | [schema](schemas/acceso.consultar_historial_credencial.v1.schema.json) |
| `vinculo.aceptar_terminos` | 1 | H3 | `POST /api/v1/vinculos/terminos` | No | agente | `aceptar_terminos` | [schema](schemas/vinculo.aceptar_terminos.v1.schema.json) |
| `vinculo.verificar_identidad` | 1 | H3 | `POST /api/v1/vinculos/{numero}/verificaciones` | No | agente | `verificar_identidad` | [schema](schemas/vinculo.verificar_identidad.v1.schema.json) |
| `vinculo.consultar_estado` | 1 | H3 | `GET /api/v1/vinculos/{numero}/estado` | No | agente | `consultar_estado` | [schema](schemas/vinculo.consultar_estado.v1.schema.json) |
| `vinculo.consultar_perfil` | 1 | H3 | `GET /api/v1/vinculos/{numero}/perfil` | Sí | agente | `consultar_perfil` | [schema](schemas/vinculo.consultar_perfil.v1.schema.json) |
| `vinculo.actualizar_preferencias` | 1 | H3 | `PATCH /api/v1/vinculos/{numero}/preferencias` | Sí | agente | `actualizar_preferencias` | [schema](schemas/vinculo.actualizar_preferencias.v1.schema.json) |
| `vinculo.bloquear` | 1 | H3 | `PATCH /api/v1/vinculos/{numero}/bloqueo` | Sí | agente | `bloquear` | [schema](schemas/vinculo.bloquear.v1.schema.json) |
| `vinculo.desvincular` | 1 | H3 | `PATCH /api/v1/vinculos/{numero}/desvinculacion` | Sí | agente | `desvincular` | [schema](schemas/vinculo.desvincular.v1.schema.json) |
| `vinculo.consultar_historial` | 1 | H3 | `GET /api/v1/vinculos/{numero}/historial` | No | soporte | `consultar_historial` | [schema](schemas/vinculo.consultar_historial.v1.schema.json) |
| `cuentas.listar_cuentas` | 1 | H4 | `GET /api/v1/cuentas` | Sí | agente | `listar_cuentas` | [schema](schemas/cuentas.listar_cuentas.v1.schema.json) |
| `cuentas.consultar_saldo` | 1 | H4 | `GET /api/v1/cuentas/{numero}/saldo` | Sí | agente | `consultar_saldo` | [schema](schemas/cuentas.consultar_saldo.v1.schema.json) |
| `cuentas.consultar_movimientos` | 1 | H4 | `GET /api/v1/cuentas/{numero}/movimientos` | Sí | agente | `consultar_movimientos` | [schema](schemas/cuentas.consultar_movimientos.v1.schema.json) |
| `cuentas.listar_terceros` | 1 | H4 | `GET /api/v1/terceros` | Sí | agente | `listar_terceros` | [schema](schemas/cuentas.listar_terceros.v1.schema.json) |
| `cuentas.preparar_transferencia` | 1 | H4 | `POST /api/v1/transferencias` | Sí | agente | `preparar_transferencia` | [schema](schemas/cuentas.preparar_transferencia.v1.schema.json) |
| `cuentas.confirmar_transferencia` | 1 | H4 | `POST /api/v1/transferencias/{id}/confirmar` | Sí | agente | `confirmar_transferencia` | [schema](schemas/cuentas.confirmar_transferencia.v1.schema.json) |
| `tarjetas.listar_tarjetas` | 1 | H5 | `GET /api/v1/tarjetas` | Sí | agente | `listar_tarjetas` | [schema](schemas/tarjetas.listar_tarjetas.v1.schema.json) |
| `tarjetas.consultar_consumos` | 1 | H5 | `GET /api/v1/tarjetas/{id}/consumos` | Sí | agente | `consultar_consumos` | [schema](schemas/tarjetas.consultar_consumos.v1.schema.json) |
| `tarjetas.bloquear_temporal` | 1 | H5 | `POST /api/v1/tarjetas/{id}/bloquear/temporal` | Sí | agente | `bloquear_temporal` | [schema](schemas/tarjetas.bloquear_temporal.v1.schema.json) |
| `tarjetas.desbloquear_temporal` | 1 | H5 | `POST /api/v1/tarjetas/{id}/desbloquear/temporal` | Sí | agente | `desbloquear_temporal` | [schema](schemas/tarjetas.desbloquear_temporal.v1.schema.json) |
| `tarjetas.bloquear_definitivo` | 1 | H5 | `POST /api/v1/tarjetas/{id}/bloquear/definitivo` | Sí | agente | `bloquear_definitivo` | [schema](schemas/tarjetas.bloquear_definitivo.v1.schema.json) |
| `tarjetas.registrar_aviso_viaje` | 1 | H5 | `POST /api/v1/tarjetas/{id}/aviso-viaje` | Sí | agente | `registrar_aviso_viaje` | [schema](schemas/tarjetas.registrar_aviso_viaje.v1.schema.json) |
| `seguros.listar_polizas` | 1 | H6 | `GET /api/v1/seguros` | Sí | agente | `listar_polizas` | [schema](schemas/seguros.listar_polizas.v1.schema.json) |
| `seguros.consultar_coberturas` | 1 | H6 | `GET /api/v1/seguros/{id}/coberturas` | Sí | agente | `consultar_coberturas` | [schema](schemas/seguros.consultar_coberturas.v1.schema.json) |
| `seguros.denunciar_siniestro` | 1 | H6 | `POST /api/v1/siniestros` | Sí | agente | `denunciar_siniestro` | [schema](schemas/seguros.denunciar_siniestro.v1.schema.json) |
| `seguros.consultar_siniestro` | 1 | H6 | `GET /api/v1/siniestros/{numeroCaso}` | Sí | agente | `consultar_siniestro` | [schema](schemas/seguros.consultar_siniestro.v1.schema.json) |
| `seguros.consultar_documentacion_pendiente` | 1 | H6 | `GET /api/v1/siniestros/{numeroCaso}/documentacion-pendiente` | Sí | agente | `consultar_documentacion_pendiente` | [schema](schemas/seguros.consultar_documentacion_pendiente.v1.schema.json) |

## Sobre de entrada/salida

Todas las capacidades comparten el sobre de `schemas/_envelope.json`:

```json
{
  "contexto": {
    "numero_whatsapp": "+5491112345678",
    "credencial": "eyJhbGciOi... | null",
    "correlation_id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01"
  },
  "parametros": { }
}
```

Salida exitosa:

```json
{
  "resultado": { },
  "auditoria": {
    "capacidad": "tarjetas.bloquear_temporal",
    "version": 1,
    "correlation_id": "...",
    "timestamp": "2026-01-01T12:00:00Z"
  }
}
```

Error (sin stack trace ni detalle técnico):

```json
{
  "error": {
    "codigo": "CREDENCIAL_INVALIDA",
    "mensaje": "Por seguridad, volvé a verificar tu identidad para continuar",
    "reintentable": false
  },
  "auditoria": {
    "capacidad": "tarjetas.bloquear_temporal",
    "version": 1,
    "correlation_id": "...",
    "timestamp": "2026-01-01T12:00:00Z"
  }
}
```

`contexto.credencial` está marcado `x-sensible: true`: nunca en logs ni repetido en
`resultado`, salvo la emisión de H2. Capacidad no publicada o deshabilitada →
`CAPACIDAD_NO_DISPONIBLE` sin llamar a ningún servicio.

## Notas de cobertura

- `users.alta` (`POST /api/v1/users`) **no** se expone: el alta no es flujo del canal WhatsApp.
- Renovación de credencial: no hay endpoint; el flujo es `vinculo.verificar_identidad` →
  `acceso.emitir_credencial`.
- Capacidades con actor `soporte` / `banco` quedan en el catálogo pero el `tool_filter` de H9
  conversacional no las ofrece.
- `acceso.revocar_credenciales_masivo` publica con `habilitada=false` por defecto
  (`MCP_CAPABILITY_ACCESO_REVOCAR_CREDENCIALES_MASIVO_ENABLED=false`).

## Errores de negocio (taxonomía H7)

| Código | Cuándo |
|---|---|
| `CREDENCIAL_REQUERIDA` / `CREDENCIAL_INVALIDA` | Falta o falla la credencial de sesión |
| `VINCULO_INACTIVO` | Vínculo inactivo (403) |
| `SOLICITUD_INVALIDA` | Validación / 400 |
| `NO_ENCONTRADO` | 404 |
| `CONFLICTO` | 409 / 423 |
| `SERVICIO_NO_DISPONIBLE` | 5xx / timeout |
| `CAPACIDAD_NO_DISPONIBLE` | No publicada, deshabilitada o actor incorrecto |
