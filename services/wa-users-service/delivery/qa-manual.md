# Manual de QA — wa-users-service

## Prerrequisitos

1. Servicio en `http://localhost:8080` (PostgreSQL disponible; ver `delivery/devops-local.md`).
2. Postman o Bruno instalados.
3. Sin tokens: la API H1 no exige JWT.

## Importar colecciones

### Postman

1. Import → `delivery/postman/wa-users-service.postman_collection.json`
2. Import → `delivery/postman/wa-users-service.postman_environment.json`
3. Seleccionar entorno **wa-users-service - local**

### Bruno

1. Open Collection → carpeta `delivery/bruno/wa-users-service/`
2. Entorno **local** (`environments/local.bru`)

También se puede importar el JSON de Postman en Bruno.

## Orden de ejecución (Collection Runner)

1. **1. Flujo feliz** (en orden):
   - Alta (genera `docNumber` y guarda `userId`)
   - Consultar
   - Actualizar
   - Cambiar estado → SUSPENDIDO
   - Aptitud de canal (esperado: `aptoParaVinculoCanal=false`)
2. **2. Errores** (tras el flujo feliz, reutiliza `userId` / `docNumber`):
   - Validación 400
   - Duplicado 409
   - No encontrado 404
   - Transición inválida 409 (SUSPENDIDO→SUSPENDIDO)
   - Campos no permitidos 400
3. **3. Salud** — `/actuator/health`

Cada pedido tiene tests de status y campos clave, más respuestas de ejemplo guardadas.

## Newman (opcional)

Si `newman` está instalado y la app corre:

```powershell
newman run delivery/postman/wa-users-service.postman_collection.json `
  -e delivery/postman/wa-users-service.postman_environment.json
```

## Swagger UI

Con la app levantada (no en perfil `prod`): http://localhost:8080/swagger-ui.html  
OpenAPI JSON: http://localhost:8080/v3/api-docs

## Qué verificar

| Caso | Esperado |
|------|----------|
| Alta mínima | 201, `status=ACTIVO`, `aptoParaVinculoCanal=true` |
| Duplicado | 409 `CONFLICT`, `details=[]` |
| Validación | 400 `VALIDATION_ERROR`, `details` con campos |
| UUID inexistente | 404 `NOT_FOUND` |
| Suspendido | aptitud `false` |
| Campo legal en PATCH | 400 `BAD_REQUEST` |

## Evidencia de verificación (cierre documental)

| Chequeo | Resultado |
|---------|-----------|
| `./mvnw -q -B verify` | Ejecutado en el cierre documental; regenera `delivery/openapi/` |
| JSON Postman válidos | Validados con `JSON.parse` |
| Newman / app + PostgreSQL en vivo | No ejecutado en este paso (requiere servicio levantado) |
| Daemon Docker / Compose up | No ejecutado aquí; ver pendientes en `devops-local.md` |

No afirmar pruebas de Collection Runner contra instancia real si no se corrieron.
