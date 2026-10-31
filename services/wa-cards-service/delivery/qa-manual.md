# Manual de QA — wa-cards-service

Cómo importar y correr las colecciones Postman/Bruno contra el servicio local.

## Prerrequisitos

1. Servicio levantado (perfil `dev` o Docker). Ver `README.md` o `../../delivery/devops-local.md`.
2. Health OK: `curl http://localhost:8086/actuator/health` → `{"status":"UP"}`.
3. **wa-credentials-service (H2)** accesible en `CREDENTIALS_SERVICE_BASE_URL`; sin él no se puede
   validar ninguna credencial.
4. Una credencial JWT válida de H2 (`X-Credencial-Sesion`) cuyo titular tenga tarjetas cargadas.
5. Datos de tarjetas: H5 no da de alta tarjetas. Para pruebas locales se puede aplicar
   [`delivery/sql/001-datos-qa.sql`](sql/001-datos-qa.sql) sobre `wa_cards` (documento `DNI 30111222`).

Completar en el entorno de Postman y de Bruno:

| Variable | Valor |
|---|---|
| `baseUrl` | `http://localhost:8086` |
| `credencial` | JWT de H2 del titular |
| `tarjetaId` | `11111111-1111-4111-8111-111111111111` (o una tarjeta real) |

## Postman

1. Importar `delivery/postman/wa-cards-service.postman_collection.json`.
2. Importar entorno `delivery/postman/wa-cards-service.postman_environment.json`.
3. Seleccionar el entorno **wa-cards-service - local** y completar `credencial`.
4. Collection Runner, en este orden:
   1. **1. Consultas** — listado y consumos (el listado actualiza `tarjetaId` con la primera tarjeta).
   2. **2. Bloqueo temporal** — bloquear y desbloquear.
   3. **3. Aviso de viaje** — registrar aviso.
   4. **4. Bloqueo definitivo** — advertencia sin confirmaciones (409) y bloqueo aplicado (200).
   5. **5. Errores** — 401, 404, 400.
   6. **6. Salud** — `/actuator/health`.
5. La carpeta **4** deja la tarjeta en `BLOQUEO_DEFINITIVO`: correrla al final o volver a aplicar el
   seed antes de repetir el flujo.

Cada pedido tiene tests de status y campos clave, y respuestas de ejemplo guardadas.

### Newman (opcional)

```bash
cd services/wa-cards-service
newman run delivery/postman/wa-cards-service.postman_collection.json \
  -e delivery/postman/wa-cards-service.postman_environment.json
```

Si `newman` no está instalado: `npm i -g newman`.

## Bruno

1. Open Collection → carpeta `delivery/bruno/wa-cards-service/`.
2. Entorno **local** (`environments/local.bru`) y completar `credencial`.
3. Ejecutar en orden: `1-consultas`, `2-bloqueo-temporal`, `3-aviso-viaje`,
   `4-bloqueo-definitivo`, `5-errores`, `6-salud`.

Misma secuencia y datos que Postman. También se puede importar el JSON de Postman en Bruno.

## Orden del flujo feliz

| # | Pedido | Esperado |
|---|--------|----------|
| 1 | GET `/api/v1/tarjetas` | 200, lista con `ultimosCuatro`, `marca`, `tipo`, `estado` |
| 2 | GET `.../{tarjetaId}/consumos?dias=30&limit=5` | 200, `consumos`, `tieneMas`, `cursor` |
| 3 | POST `.../bloquear/temporal` | 200, `estado=BLOQUEO_TEMPORAL` |
| 4 | POST `.../desbloquear/temporal` | 200, `estado=ACTIVA` |
| 5 | POST `.../aviso-viaje` | 201, `mensajeConfirmacion` |
| 6 | POST `.../bloquear/definitivo` | 200, `tarjeta.estado=BLOQUEO_DEFINITIVO` |

## Casos de error a verificar

| Caso | Pedido | Esperado |
|------|--------|----------|
| Sin credencial | GET `/api/v1/tarjetas` sin header | 401 `UNAUTHORIZED` |
| Tarjeta inexistente / ajena | GET `/api/v1/tarjetas/{uuid}/consumos` | 404 `NOT_FOUND` |
| Consumos con `dias=0` | GET consumos | 400 `BAD_REQUEST` |
| Bloqueo temporal sin `CONFIRMAR` | POST `bloquear/temporal` con `confirmar` vacío | 400 `BAD_REQUEST`, tarjeta sin cambios |
| Desbloqueo sin validación adicional | POST `desbloquear/temporal` con `validacionAdicional=false` | 400 `BAD_REQUEST` |
| Desbloqueo de tarjeta con bloqueo definitivo | POST `desbloquear/temporal` | 409 `CONFLICT` (irreversible) |
| Bloqueo definitivo sin doble confirmación | POST `bloquear/definitivo` sin confirmaciones | 409 `CONFLICT`, tarjeta sin cambios |
| Aviso de viaje con fechas invertidas | POST `aviso-viaje` `fechaHasta` < `fechaDesde` | 400 `BAD_REQUEST` |
| Aviso de viaje sin país | POST `aviso-viaje` | 400 `VALIDATION_ERROR` con `details` |

## Tests automatizados del repo

```bash
cd services/wa-cards-service
./mvnw -q -B verify
```

Regenera `delivery/openapi/` y exige JaCoCo ≥ 80 %.

## Verificación técnica

- Swagger UI: `http://localhost:8086/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8086/v3/api-docs`
- OpenAPI exportado: `delivery/openapi/openapi.json` y `openapi.yaml`
- Health: `GET /actuator/health`
