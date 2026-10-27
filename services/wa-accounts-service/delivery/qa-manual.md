# Manual de QA — wa-accounts-service

Cómo importar y correr las colecciones Postman/Bruno contra el servicio local.

## Prerrequisitos

1. Servicio levantado (local o Docker). Ver `README.md` o documentación de despliegue.
2. Health OK: `curl http://localhost:8084/actuator/health` → `{"status":"UP"}`.
3. Tener credencial H2 vigente para header `X-Credencial-Sesion` (validación con wa-credentials-service/H2).
4. Contar con cuentas del titular, al menos un tercero activo en agenda y política en `limites_canal` compatible.

## Postman

1. Importar `delivery/postman/wa-accounts-service.postman_collection.json` (si existe).
2. Importar entorno `delivery/postman/wa-accounts-service.postman_environment.json` (baseUrl `http://localhost:8084`).
3. Seleccionar el entorno correspondiente y setear `X-Credencial-Sesion` con una credencial H2 válida.
4. Ejecutar en orden: listar cuentas → obtener saldo → movimientos → terceros → preparar transferencia → confirmar.
5. Para movimientos con paginación, usar `nextCursor` de la respuesta anterior.

Cada pedido debe tener ejemplos de respuesta guardados; validar códigos y contrato de error.

### Newman (opcional)

```bash
cd services/wa-accounts-service
newman run delivery/postman/wa-accounts-service.postman_collection.json \
  -e delivery/postman/wa-accounts-service.postman_environment.json
```

Si `newman` no está instalado: `npm i -g newman`.

## Bruno

1. Abrir colección desde `delivery/bruno/wa-accounts-service/`.
2. Configurar entorno local con `baseUrl: http://localhost:8084` y `credencialSesion`/header `X-Credencial-Sesion`.
3. Ejecutar en orden las carpetas correspondientes al flujo.

## Flujo feliz (verificación manual)

| # | Pedido | Esperado |
|---|--------|----------|
| 1 | GET `/api/v1/cuentas` con `X-Credencial-Sesion` | 200, lista de cuentas del titular |
| 2 | GET `/api/v1/cuentas/{numero}/saldo` | 200, saldos contable/disponible |
| 3 | GET `/api/v1/cuentas/{numero}/movimientos?limit=10` | 200, items + nextCursor/hasMore |
| 4 | GET `/api/v1/terceros` | 200, terceros activos |
| 5 | POST `/api/v1/transferencias` (a tercero o cuenta propia) | 201, `estado PREPARADA`, `expiraEn`, sin débito |
| 6a | POST `/api/v1/transferencias/{id}/confirmar` con `"CONFIRMAR"` | 200, si doble confirmación → `CONFIRMADA`; si no → `EJECUTADA` con comprobante |
| 6b | Si quedó `CONFIRMADA`, repetir confirmar con `"CONFIRMAR"` | 200, `EJECUTADA` con comprobante y referencia |

## Casos de error a verificar

| Caso | Esperado |
|------|----------|
| Sin `X-Credencial-Sesion` / sesión inválida | 401 `UNAUTHORIZED` |
| Cuenta origen ajena o inexistente | 404 `NOT_FOUND` |
| Destino no local / sin adaptador interbancario | 409 `CONFLICT` (sin débito) |
| Saldo insuficiente / límite diario/mensual | 409 `CONFLICT` |
| Fuera de horario / día no habilitado | 400/409 según validación (`BAD_REQUEST`/`CONFLICT`) |
| Reserva expirada al confirmar | 409 `CONFLICT` |
| Confirmación distinta a `CONFIRMAR` | 409 `CONFLICT` (vuelve a `PREPARADA`) |
| Doble confirmación interrumpida/reintentada mal | 409 `CONFLICT` |
| Ejecución repetida de misma transferencia | 409 `CONFLICT` (sin nuevo débito) |
| Mismo monto/moneda pero sin política compatible en `limites_canal` | 409/400 según regla |

## Tests automatizados

```bash
cd services/wa-accounts-service
./mvnw -q -B verify
```

Regenera `delivery/openapi/` (OpenApiExportTest/OpenApiContractTest), ejecuta tests y verifica JaCoCo ≥ 80%. En PowerShell usar `./mvnw.cmd -q -B verify`.

## Notas

- Las reservas `PREPARADA/CONFIRMADA` comprometen saldo y límites; al confirmar se revalida excluyendo la propia reserva.
- Las cuentas se bloquean en orden UUID para evitar deadlocks.
- No hay extensión de vencimiento al confirmar.
- Usar datos ficticios en ejemplos; respetar formato de montos con hasta dos decimales.
