# Manual de QA — wa-insurance-service

## Preparación

1. Levantar el servicio (local o Docker). Ver `README.md`.
2. Tener acceso a **wa-credentials-service (H2)** para validar credenciales, o usar credenciales válidas en el entorno.
3. Importar colecciones Postman/Bruno (ver abajo).

Puerto por defecto: `8085`  
Header: `X-Credencial-Sesion: <JWT_H2>`

## Importar colecciones

### Postman
1. Abrir Postman → Import
2. Importar `delivery/postman/wa-insurance-service.postman_collection.json`
3. Importar `delivery/postman/wa-insurance-service.postman_environment.json`
4. Seleccionar entorno `wa-insurance-service - local`
5. Ajustar `baseUrl` si es necesario (default `http://localhost:8085`)

### Bruno
1. Abrir Bruno → Open Collection
2. Seleccionar la carpeta `delivery/bruno/wa-insurance-service/`
3. Seleccionar entorno `local` y ajustar `baseUrl` si corresponde

## Orden de ejecución (flujo feliz)

Ejecutar en orden con Collection Runner o manualmente:

1. **Seguros** → `Listar pólizas vigentes` (GET `/api/v1/seguros`)
2. **Seguros** → `Consultar coberturas` (GET `/api/v1/seguros/{polizaId}/coberturas`) — usar un `polizaId` obtenido o de ejemplo
3. **Siniestros** → `Denunciar siniestro (AUTO)` (POST `/api/v1/siniestros`) — guarda `numeroCaso` en variable
4. **Siniestros** → `Consultar siniestro` (GET `/api/v1/siniestros/{numeroCaso}`)
5. **Siniestros** → `Documentación pendiente` (GET `/api/v1/siniestros/{numeroCaso}/documentacion-pendiente`)

## Casos de error a validar

| Caso | Endpoint | Esperado | Verificación |
|---|---|---|---|
| Sin credencial | GET `/api/v1/seguros` | 401 UNAUTHORIZED | `$.code == UNAUTHORIZED` |
| Tercero sobre póliza ajena | GET `/api/v1/seguros/{polizaId}/coberturas` | 403 FORBIDDEN | `$.code == FORBIDDEN` |
| Póliza inexistente | GET `/api/v1/seguros/{polizaId}/coberturas` | 404 NOT_FOUND | `$.code == NOT_FOUND` |
| Denuncia con campos inválidos | POST `/api/v1/siniestros` | 400 VALIDATION_ERROR | `$.code == VALIDATION_ERROR`, `details` con campos |
| Denuncia duplicada (misma póliza/tipo en ventana) | POST `/api/v1/siniestros` | 409 CONFLICT | `$.code == CONFLICT` |
| Consultar caso ajeno | GET `/api/v1/siniestros/{numeroCaso}` | 403 FORBIDDEN | `$.code == FORBIDDEN` |
| Caso inexistente | GET `/api/v1/siniestros/{numeroCaso}` | 404 NOT_FOUND | `$.code == NOT_FOUND` |

Los pedidos de la carpeta **3. Errores** de Postman/Bruno cubren 401, 400, 404 y 409 con respuestas de ejemplo guardadas.

## Newman (opcional)

Con la app levantada y `credencial` configurada en el entorno:

```sh
newman run delivery/postman/wa-insurance-service.postman_collection.json \
  -e delivery/postman/wa-insurance-service.postman_environment.json
```

Si `newman` no está instalado, usar Collection Runner de Postman o Bruno.

## Verificación técnica

- Swagger UI carga: `http://localhost:8085/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8085/v3/api-docs`
- Archivos estáticos regenerados por build: `delivery/openapi/openapi.json` y `openapi.yaml`
- Health: `GET /actuator/health`
- Build con cobertura: `./mvnw clean verify` (JaCoCo ≥ 80%)
