# wa-insurance-service — Servicio de Seguros (H6)

Servicio independiente de seguros bancarios (H6): consulta de pólizas vigentes y coberturas, denuncia de siniestros y seguimiento de casos con documentación pendiente. Java 21 y Spring Boot 3. Puerto por defecto: 8085.

## Funcionalidades

- Listar pólizas vigentes autorizadas — `GET /api/v1/seguros`
- Consultar coberturas y exclusiones — `GET /api/v1/seguros/{polizaId}/coberturas`
- Denunciar siniestro — `POST /api/v1/siniestros`
- Consultar estado del siniestro — `GET /api/v1/siniestros/{numeroCaso}`
- Consultar documentación pendiente — `GET /api/v1/siniestros/{numeroCaso}/documentacion-pendiente`

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Base de datos | PostgreSQL (H2 en perfil `test`) |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi (Swagger UI) |
| Mapeo | MapStruct · Lombok |

## Requisitos

- JDK 21
- Docker + Compose v2 (opcional para levantar app + Postgres)
- Variables de entorno según perfil (ver abajo)

## Cómo correrlo

Desde `services/wa-insurance-service`. En PowerShell usar `.\mvnw.cmd` en lugar de `./mvnw`.

### Local
```sh
# PostgreSQL accesible
export DB_URL=jdbc:postgresql://localhost:5432/wa_insurance
export DB_USERNAME=postgres
export DB_PASSWORD=<password>
./mvnw spring-boot:run
```

Los tests usan H2 sin requerir PostgreSQL. El esquema debe existir; por defecto `JPA_DDL_AUTO=validate` (ver `application.yml`).

### Con Docker
```sh
cp .env.example .env   # PowerShell: copy .env.example .env
docker compose up -d --build
curl http://localhost:8085/actuator/health
```

## Configuración

| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|
| `PORT` | Puerto HTTP | `8085` |
| `DB_URL` | JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/wa_insurance` |
| `DB_USERNAME` | Usuario BD | `postgres` |
| `DB_PASSWORD` | Contraseña BD | — |
| `JPA_DDL_AUTO` | DDL Hibernate | `validate` |
| `CREDENTIALS_SERVICE_BASE_URL` | URL de wa-credentials-service (H2) | `http://localhost:8082` |
| `CREDENTIALS_SERVICE_CONNECT_TIMEOUT` | Timeout de conexión | `2s` |
| `CREDENTIALS_SERVICE_READ_TIMEOUT` | Timeout de lectura | `3s` |
| `SINIESTROS_VENTANA_DUPLICADO_DIAS` | Ventana de duplicados (días) | `30` |

## API

- Swagger UI: http://localhost:8085/swagger-ui.html
- OpenAPI: http://localhost:8085/v3/api-docs · copia estática en `delivery/openapi/openapi.json` y `.yaml`

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| GET | `/api/v1/seguros` | Listar pólizas vigentes autorizadas | 200, 401, 403 |
| GET | `/api/v1/seguros/{polizaId}/coberturas` | Coberturas, exclusiones y resumen | 200, 401, 403, 404 |
| POST | `/api/v1/siniestros` | Denunciar siniestro | 201, 400, 401, 403, 404, 409 |
| GET | `/api/v1/siniestros/{numeroCaso}` | Consultar estado del caso | 200, 401, 403, 404 |
| GET | `/api/v1/siniestros/{numeroCaso}/documentacion-pendiente` | Documentación pendiente | 200, 401, 403, 404 |

### Errores

Contrato único (`code`, `message`, `details`, `timestamp` UTC); sin stacktraces. Todas las operaciones requieren header `X-Credencial-Sesion` (JWT validado por H2).

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [{ "field": "descripcion", "message": "no debe estar vacío" }],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

| HTTP | `code` | Uso |
|------|--------|-----|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` | Datos inválidos o solicitud malformada |
| 401 | `UNAUTHORIZED` | Credencial inválida o ausente |
| 403 | `FORBIDDEN` | Sin autorización sobre póliza/caso |
| 404 | `NOT_FOUND` | Recurso no encontrado |
| 409 | `CONFLICT` | Siniestro duplicado en ventana configurada |
| 500 | `INTERNAL_ERROR` | Error interno |

## Probar la API

- Postman: importar `delivery/postman/wa-insurance-service.postman_collection.json` y `delivery/postman/wa-insurance-service.postman_environment.json`
- Bruno: abrir la colección `delivery/bruno/wa-insurance-service/`
- Paso a paso QA: [`delivery/qa-manual.md`](delivery/qa-manual.md)
- Guía con curl: [`delivery/api.md`](delivery/api.md)

## Tests

```sh
./mvnw -q -B verify
```

Unitarios + integración (perfil `test` / H2). JaCoCo ≥ 80 % en `verify`. El test `OpenApiExportTest` regenera `delivery/openapi/`.

## Estructura del proyecto

```
com.bcra.insurance.ws/
├── InsuranceApplication.java
├── config/              # OpenApiConfig, RestClientConfig
├── exception/           # contrato de error global
└── seguro/
    ├── controller/      # PolizaController, SiniestroController
    ├── service/         # PolizaService, CoberturaService, SiniestroService, DuplicadoService, DocumentacionService
    ├── repository/      # PolizaRepository, CoberturaRepository, ExclusionRepository, SiniestroRepository
    ├── mapper/          # PolizaMapper, SiniestroMapper
    ├── entity/          # Poliza, Cobertura, Exclusion, Siniestro, EstadoSiniestro, TipoSiniestro
    ├── dto/             # request/response
    ├── client/          # cliente H2 (CredencialesServiceClient)
    └── exception/       # excepciones de dominio
```

## Documentación

| Documento | Para qué |
|---|---|
| [`delivery/api.md`](delivery/api.md) | Uso de la API con curl |
| [`delivery/architecture-overview.md`](delivery/architecture-overview.md) | Capas, decisiones y flujo de una petición |
| [`delivery/qa-manual.md`](delivery/qa-manual.md) | Cómo probar todo con Postman/Bruno |
| [`specs/seguros/`](../../specs/seguros/) | Requisitos de negocio |
