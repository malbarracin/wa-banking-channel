# Manual de QA — wa-users-service

Cómo importar y correr las colecciones Postman/Bruno contra el servicio local.

## Prerrequisitos

1. Servicio levantado (local o Docker). Ver `README.md` o `../../delivery/devops-local.md`.
2. Health OK: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.
3. La consulta completa `GET /api/v1/users/{tipo}/{numero}` exige principal autenticado con rol
   `USERS_FULL_READ`. El servicio no configura autenticación: con la configuración del repo esa consulta
   responde **403** por diseño. Los casos **200/404** de esa ruta se validan con los tests de integración
   (MockMvc con principal y rol) o en un despliegue que asigne el rol.

## Postman

1. Importar `delivery/postman/wa-users-service.postman_collection.json`.
2. Importar entorno `delivery/postman/wa-users-service.postman_environment.json` (baseUrl `http://localhost:8080`).
3. Seleccionar el entorno **wa-users-service - local**.
4. Collection Runner → carpeta **1. Flujo feliz** (orden fijo). El alta genera `docNumber` único.
5. Luego **2. Errores** (validación, duplicado, 404, campo no editable, transición inválida).
6. **3. Salud** → `/actuator/health`.

Cada pedido tiene tests de status y campos clave, más respuestas de ejemplo guardadas.

### Newman (opcional)

```bash
cd services/wa-users-service
newman run delivery/postman/wa-users-service.postman_collection.json \
  -e delivery/postman/wa-users-service.postman_environment.json
```

Si `newman` no está instalado: `npm i -g newman`.

## Bruno

1. Open Collection → carpeta `delivery/bruno/wa-users-service/`.
2. Entorno **local** (`environments/local.bru`).
3. Ejecutar en orden la carpeta `1-flujo-feliz`, luego `2-errores`, luego `3-salud`.

Misma secuencia y datos que Postman. También se puede importar el JSON de Postman en Bruno.

## Orden del flujo feliz

| # | Pedido | Esperado |
|---|--------|----------|
| 1 | Alta POST `/api/v1/users` | 201 con `Location` y `estado=ACTIVE` |
| 2 | Consulta GET `.../{tipo}/{numero}` | 403 `FORBIDDEN` sin autenticación configurada (200 con rol `USERS_FULL_READ`) |
| 3 | Status GET `.../status` | 200, `estado=ACTIVE` |
| 4 | Actualización PATCH | 200, email/tratamiento nuevos |
| 5 | Cambio estado PATCH `.../status` → SUSPENDED | 200, `estado=SUSPENDED` |

## Casos de error a verificar

| Caso | Esperado |
|------|----------|
| Alta sin nombre | 400 `VALIDATION_ERROR` (solo audita `RECHAZADO` si el documento viene identificable) |
| Alta documento repetido | 409 `CONFLICT` |
| Consulta completa sin principal o sin rol | 403 `FORBIDDEN` |
| GET documento inexistente con rol `USERS_FULL_READ` | 404 `NOT_FOUND` (sin el rol: 403) |
| PATCH con `fechaNacimiento` | 400 `BAD_REQUEST` (campo no editable) |
| PATCH status LOGICAL_DELETE → ACTIVE | 400 `BAD_REQUEST` |

## Tests automatizados del repo

```bash
cd services/wa-users-service
./mvnw -q -B verify
```

Regenera `delivery/openapi/` y exige JaCoCo ≥ 80 %.
