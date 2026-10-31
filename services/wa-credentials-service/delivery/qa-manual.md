# Manual de QA — wa-credentials-service

Cómo importar y correr las colecciones Postman/Bruno contra el servicio local, en qué orden y qué se
espera en cada caso.

## Prerrequisitos

1. H2 levantado (local o Docker). Ver [`../README.md`](../README.md) o
   [`../../../delivery/devops-local.md`](../../../delivery/devops-local.md).
2. Health OK: `curl http://localhost:8082/actuator/health` → `{"status":"UP"}`.
3. **H1 `wa-users-service`** en `http://localhost:8080` (o `h1BaseUrl` configurado) y la posibilidad de
   crear un cliente. Sin H1, la carpeta **0** falla y emisión/validación responden **503**/**401**.
4. `CREDENTIAL_JWT_SECRET` configurado (≥ 32 bytes UTF-8). Sin él, H2 no arranca.
5. `WA_USERS_SERVICE_BASE_URL` apuntando a H1 (default `http://localhost:8080`).
6. `CREDENTIAL_H3_SERVICE_TOKEN` y `CREDENTIAL_BANK_SERVICE_TOKEN` con valores distintos. Emitir,
   revocar individualmente y renovar exigen el secreto de H3; la revocación masiva, el del banco.
   Sin ellos esas operaciones responden **401**; con el secreto del actor equivocado, **403**.
   Las colecciones leen los secretos de las variables `h3ServiceToken` y `bankServiceToken`.

La carpeta **0. Preparación H1** genera un `docNumber` único por corrida y crea el cliente `ACTIVE` en
H1. Es la forma recomendada de repetir la suite sin limpiar bases. Si se omite, hay que garantizar a
mano que el documento de `docNumber` exista y esté `ACTIVE` en H1.

## Postman

1. Importar `delivery/postman/wa-credentials-service.postman_collection.json`.
2. Importar el entorno `delivery/postman/wa-credentials-service.postman_environment.json`
   (`baseUrl` H2 `http://localhost:8082`, `h1BaseUrl` H1 `http://localhost:8080`,
   `h3ServiceToken`, `bankServiceToken`).
3. Seleccionar el entorno **wa-credentials-service - local** y ajustar los dos secretos a los valores
   configurados en H2.
4. Collection Runner → ejecutar en orden:
   **0. Preparación H1** → **1. Flujo feliz** → **2. Errores** → **3. Salud** →
   **4. Renovación** (opcional, requiere una entrega preparada; ver más abajo).

Cada pedido tiene tests de status y de campos clave, más una respuesta de ejemplo guardada.
La carpeta 1 guarda `jwt` y `fechaExpiracion` en variables de colección y las reutiliza.
La carpeta 4 se ejecuta después de preparar la ventana de renovación.

### Newman (opcional)

```bash
cd services/wa-credentials-service
newman run delivery/postman/wa-credentials-service.postman_collection.json \
  -e delivery/postman/wa-credentials-service.postman_environment.json
```

Si `newman` no está instalado: `npm i -g newman`.

## Bruno

1. Open Collection → carpeta `delivery/bruno/wa-credentials-service/`.
2. Entorno **local** (`environments/local.bru`): `baseUrl`, `h1BaseUrl`, `docTipo`, `docNumber`,
   `numeroWhatsapp`, `h3ServiceToken` y `bankServiceToken`.
3. Ejecutar en orden `0-preparacion-h1`, `1-flujo-feliz`, `2-errores`, `3-salud` y, si se preparó la
   entrega, `4-renovacion`.

Misma secuencia, datos y aserciones que Postman. También se puede importar el JSON de Postman en Bruno.

## Orden del flujo feliz

| # | Pedido | Esperado |
|---|--------|----------|
| 1 | Crear cliente en H1 (`POST {{h1BaseUrl}}/api/v1/users`) | 201, `estado=ACTIVE` |
| 2 | Emitir `POST /api/v1/credenciales/emitir` | 201, `credencial` y `fechaExpiracion` |
| 3 | Validar `POST /api/v1/credenciales/validar` | 200, `vigente=true`, identidad del cliente |
| 4 | Historial `GET /api/v1/credenciales/{numero}/historial` | 200, `estadoActual=ACTIVA`, eventos |
| 5 | Revocar `POST /api/v1/credenciales/{numero}/revocacion` | 204 sin cuerpo |
| 6 | Emitir de nuevo | 201; queda una activa para la revocación masiva |
| 7 | Revocación masiva `POR_CLIENTE` | 200, `revocadas>=1` |
| 8 | Repetir revocación masiva | 200, `revocadas=0` (idempotente) |

## Casos de error cubiertos

| Caso | Esperado |
|------|----------|
| Validar la credencial revocada | 401 `UNAUTHORIZED` |
| Validar un JWT malformado (`no-es-un-jwt`) | 401 `UNAUTHORIZED` |
| Emitir con `identidadVerificada=false` | 403 `FORBIDDEN` |
| Emitir con número fuera de E.164 | 400 `VALIDATION_ERROR` |
| Emitir con cliente inexistente en H1 (`docInexistente`) | 404 `NOT_FOUND` |
| Revocar sin credencial activa | 404 `NOT_FOUND` |
| Revocar sin `motivo` | 400 `VALIDATION_ERROR` |
| Revocación masiva `POR_CLIENTE` sin `segmento` | 400 `VALIDATION_ERROR` |
| Emitir, revocar o renovar sin `Authorization` | 401 `UNAUTHORIZED` |
| Revocación masiva con el secreto de H3 | 403 `FORBIDDEN` |
| Validar con body `{}` o `credencial` vacía | 400 `VALIDATION_ERROR` |
| Historial de un número sin registros | 404 `NOT_FOUND` |

Casos que dependen del entorno:

| Caso | Cómo provocarlo | Esperado |
|------|-----------------|----------|
| H1 no disponible | Detener H1 y emitir o validar | Emitir **503** `SERVICE_UNAVAILABLE`; validar **401** `UNAUTHORIZED` |
| Cliente suspendido | Cambiar el cliente a `SUSPENDED` en H1 (`PATCH .../status`) y emitir/validar | Emitir **403** `FORBIDDEN`; validar **401** `UNAUTHORIZED` |
| Revocación concurrente / duplicada | Revocar dos veces el mismo número | La primera **204**; la segunda **404** `NOT_FOUND` |
| Renovación en dos fases | Ver sección siguiente | El anterior sigue validando hasta `confirmar`; luego responde 401 y el historial muestra `RENOVACION`/`EXITOSO` |

## Renovación automática (opcional, local)

El job no entrega el JWT: solo prepara la entrega durable. Para observarla sin esperar 24 h, levantar
H2 con:

```powershell
$env:CREDENTIAL_VIGENCIA_MINUTOS="2"
$env:CREDENTIAL_RENOVACION_VENTANA_MINUTOS="1"
$env:CREDENTIAL_RENOVACION_INTERVALO_MS="10000"
./mvnw spring-boot:run
```

1. Emitir una credencial (carpeta 1) y esperar al menos una ejecución del job (10 s).
2. `POST /api/v1/credenciales/renovacion/obtener` con el JWT actual → **200** con el sustituto.
   El JWT anterior **sigue validando** en este punto.
3. `POST /api/v1/credenciales/renovacion/confirmar` con el JWT nuevo → **204**. Repetir la llamada
   devuelve **204** sin efectos adicionales.
4. Validar el JWT nuevo → **200**; validar el anterior → **401**. El historial muestra
   `RENOVACION` `EXITOSO`.

Antes de que la entrega esté preparada, `obtener` responde **404** `NOT_FOUND` (conservar el actual y
reintentar). La ventana debe ser positiva y menor que la vigencia. En el perfil `test` la renovación
está deshabilitada. Las carpetas **4** de Postman y Bruno cubren los pasos 2 a 4 cuando la entrega ya
está preparada.

## Tests automatizados del repo

```bash
cd services/wa-credentials-service
./mvnw -q -B verify
```

Unitarios + integración con perfil `test` (H2 en memoria y H1 simulado con `@MockitoBean`; no requiere
servicios externos). Regenera `delivery/openapi/openapi.json` y `openapi.yaml`, y exige JaCoCo ≥ 80 %
de líneas. En un sandbox con `~/.m2` restringido:

```powershell
./mvnw.cmd -q -B "-Dmaven.repo.local=../../.maven-cache" verify
```

## Qué no cubre la suite manual

- Pérdida de respuesta o reinicios de H3 durante la entrega: cubierto por `RenovacionIntegrationTest`.
- Autenticación real entre servicios (mTLS, rotación de secretos): aquí solo se verifica el Bearer.
- Concurrencia de entregas de renovación: cubierta por el bloqueo pesimista y los tests.
- Concurrencia real de emisiones simultáneas: cubierta por el índice parcial de PostgreSQL y los tests.
- Criterios reales de riesgo/fraude: dependen de la política del banco (hoy `riesgo` se persiste como
  bandera y bloquea la renovación).
- Retención o paginación del historial: el endpoint devuelve todos los eventos del número.
