# Arquitectura — wa-insurance-service (H6)

## Visión general

Servicio REST en Spring Boot 3 / Java 21 que gestiona consultas de seguros, denuncia de siniestros y seguimiento de casos. Se integra con **wa-credentials-service (H2)** para validar la credencial de sesión enviada en `X-Credencial-Sesion`.

## Capas (MVC)

Sigue el patrón por capas dentro del módulo de dominio `seguro/`:

```
com.bcra.insurance.ws/
├── InsuranceApplication.java
├── config/                # OpenApiConfig, RestClientConfig
├── exception/             # ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler, NotFound/Conflict/Unauthorized/Forbidden
└── seguro/
    ├── controller/        # PolizaController, SiniestroController (HTTP, validación, status)
    ├── service/           # PolizaService, CoberturaService, SiniestroService, DuplicadoService, DocumentacionService (reglas)
    ├── repository/        # PolizaRepository, CoberturaRepository, ExclusionRepository, SiniestroRepository (JPA)
    ├── mapper/            # PolizaMapper, SiniestroMapper (MapStruct)
    ├── entity/            # Poliza, Cobertura, Exclusion, Siniestro, EstadoSiniestro, TipoSiniestro
    ├── dto/               # PolizaResponse, CoberturaResponse, DenunciaRequest, DenunciaResponse, SiniestroResponse, DocumentacionPendienteResponse
    ├── client/            # CredencialesServiceClient, RestCredencialesServiceClient, DTOs de validación
    └── exception/         # PolizaNoEncontradaException, PolizaNoAutorizadaException, SiniestroDuplicadoException, SiniestroNoEncontradoException, SiniestroNoAutorizadoException
```

## Flujo de una petición

1. **Controller** recibe la petición HTTP, valida con `@Valid` (Bean Validation), extrae `X-Credencial-Sesion`.
2. **Cliente H2** (`RestCredencialesServiceClient`) valida la credencial contra `POST /api/v1/credenciales/validar` (timeouts configurables).
3. **Service** aplica reglas de negocio (autorización por titularidad, control de duplicados por ventana configurable, generación de número de caso).
4. **Repository** accede a PostgreSQL (H2 en tests). Las entidades JPA no se exponen por la API.
5. **Mapper** (MapStruct) transforma Entity → DTO/Response.
6. **GlobalExceptionHandler** traduce excepciones al contrato único de error (`code`, `message`, `details`, `timestamp`), sin stacktraces.

## Integración externa

- **H2 (wa-credentials-service)**: validación de sesión. Configurado en `application.yml` (`clients.wa-credentials-service.*`). Requiere header `X-Credencial-Sesion`.

## Persistencia

- **Runtime**: PostgreSQL (base `wa_insurance`).
- **Tests**: H2. 
- **Configuración**: `spring.jpa.open-in-view: false`, `server.error.include-stacktrace: never`.

## Contrato de errores

Unificado en `exception/` con `ErrorResponse`, `FieldError`, `ErrorCode` y `@RestControllerAdvice`. Códigos: `VALIDATION_ERROR`, `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`.

## OpenAPI

- Configuración: `config/OpenApiConfig` con ejemplos reutilizables del contrato de error.
- UI: `/swagger-ui.html`, JSON: `/v3/api-docs`.
- Exportación estática: `OpenApiExportTest` genera `delivery/openapi/openapi.json` y `.yaml` en `./mvnw verify`.

## Decisiones relevantes

- **Sin exposición médica/sensible**: se evitan montos de liquidación y datos médicos en respuestas (pendiente/orientación genérica).
- **Ventana de duplicados**: configurable vía `insurance.siniestros.ventana-duplicado-dias` (default 30). Siniestros `RESUELTO` permiten nueva apertura.
- **Autorización estricta**: listado/coberturas permiten titular o asegurado; seguimiento de siniestro solo titular del caso; terceros → `403 FORBIDDEN`.
- **Número de caso único**: generado por el servicio.
