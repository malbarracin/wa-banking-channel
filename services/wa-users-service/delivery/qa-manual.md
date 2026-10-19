# Manual de QA — wa-users-service

Cómo importar y ejecutar las colecciones contra un entorno local. Los datos de ejemplo son ficticios
y coinciden con Swagger / `api.md` / Bruno.

## Precondiciones

1. Servicio arriba en `http://localhost:8080` (ver README o `devops-local.md`).
2. Health OK: `GET /actuator/health` → `UP`.
3. Base limpia o documento de prueba no usado (el alta del flujo feliz genera un número único).

## Postman

1. Importar `delivery/postman/wa-users-service.postman_collection.json`.
2. Importar `delivery/postman/wa-users-service.postman_environment.json` y seleccionarlo.
3. Abrir Collection Runner sobre la colección completa (o carpeta a carpeta).

### Orden sugerido

| Carpeta | Qué valida |
|---------|------------|
| **1. Flujo feliz** | Alta → consulta por id → consulta por documento → update → suspender → reactivar → baja lógica |
| **2. Errores** | 400 validación, 409 duplicado, 404, transición inválida, campo no permitido |
| **3. Salud** | Actuator health / info |

El alta del flujo feliz genera `docNumber` en un *prerequest* (`Date.now()`) y guarda `userId` en
variables de colección para los pasos siguientes. El Runner no requiere editar IDs a mano.

### Newman (opcional)

Con la app levantada:

```sh
newman run delivery/postman/wa-users-service.postman_collection.json \
  -e delivery/postman/wa-users-service.postman_environment.json
```

Si `newman` no está instalado, usar Collection Runner en la UI de Postman.

## Bruno

1. Open Collection → carpeta `delivery/bruno/wa-users-service/`.
2. Elegir entorno `local`.
3. Ejecutar en orden los `.bru` de `1-flujo-feliz/` y luego `2-errores/`.

También se puede importar el JSON de Postman (Import → Postman Collection).

## Casos esperados (resumen)

| Caso | Status | code (si error) |
|------|--------|-----------------|
| Alta válida | 201 | — |
| Alta sin contacto | 400 | `VALIDATION_ERROR` |
| Alta documento duplicado | 409 | `CONFLICT` |
| GET id existente | 200 | — |
| GET id inexistente | 404 | `NOT_FOUND` |
| PATCH campo no permitido | 400 | `BAD_REQUEST` |
| PATCH status ACTIVE→SUSPENDED | 200 | `channelLinkEligible: false` |
| PATCH status desde LOGICALLY_DELETED | 409 | `CONFLICT` |

## OpenAPI / Swagger

Con la app en perfil no-prod: http://localhost:8080/swagger-ui.html — probar con los ejemplos del
endpoint. Contrato estático: `delivery/openapi/openapi.json` (se regenera con `./mvnw verify`).
