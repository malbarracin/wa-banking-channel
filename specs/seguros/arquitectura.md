---
title: Seguros — arquitectura
status: borrador
source: null
updated: 2026-10-27T16:01:59.543Z
---

# Arquitectura — Servicio de Seguros (H6)

## Base

- **Servicio nuevo** (no existe `pom.xml` todavía): `services/wa-insurance-service`, análogo a `wa-accounts-service` (H4).
- Maven groupId: **com.bcra** (`project.yaml` → `java.group_id`).
- Base package: **com.bcra.insurance.ws** — propuesto, siguiendo el patrón `<group_id>.<dominio>.ws`. Tech-lead confirma y completa `project.yaml`.
- Artifact id propuesto: `wa-insurance-service`.
- Módulo de dominio: **seguro** (agregado central: póliza, cobertura, exclusión, siniestro, estado).
- Puerto propuesto: **8085** (H1 8080, H3 8081, H2 8082, H4 8084; evita choque en local).
- Persistencia: PostgreSQL en runtime, H2 en test — base de datos propia (`wa_insurance`).
- Integraciones salientes (síncronas, REST):
  - **H2 (wa-credentials-service)**: validar credencial de sesión (`POST /api/v1/credenciales/validar`). Base URL configurable (`clients.wa-credentials-service.base-url`).
  - H6 no llama a H1 ni H3 directamente: la identidad del cliente viene del JWT validado por H2.

## Árbol de paquetes

```
com.bcra.insurance.ws/
├── InsuranceApplication.java
├── seguro/
│   ├── controller/      PolizaController, SiniestroController
│   ├── service/         PolizaService, CoberturaService, SiniestroService,
│   │                    DuplicadoService, DocumentacionService
│   ├── repository/      PolizaRepository, CoberturaRepository, ExclusionRepository,
│   │                    SiniestroRepository
│   ├── mapper/          PolizaMapper, SiniestroMapper (MapStruct)
│   ├── entity/          Poliza, Cobertura, Exclusion, Siniestro, EstadoSiniestro,
│   │                    TipoSiniestro
│   ├── dto/             PolizaResponse, CoberturaResponse, DenunciaRequest,
│   │                    DenunciaResponse, SiniestroResponse, DocumentacionPendienteResponse
│   ├── client/          CredencialesServiceClient (+ dto ValidarCredencialResponse)
│   └── exception/       PolizaNoEncontradaException, PolizaNoAutorizadaException,
│                        SiniestroDuplicadoException, SiniestroNoEncontradoException
├── config/              OpenApiConfig, RestClientConfig
└── exception/           ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler,
                        NotFoundException, ConflictException, UnauthorizedException,
                        ForbiddenException
```

## Entidades

- **seguro.entity.Poliza**: id (UUID), numeroPoliza (string único), ramo (string), vigente (boolean), fechaVigenciaDesde (LocalDate), fechaVigenciaHasta (LocalDate), titularDocumentoTipo + titularDocumentoNumero (referencia al cliente, sin FK física), createdAt/updatedAt.
- **seguro.entity.Cobertura**: id (UUID), poliza (FK lógica), descripcion (string), resumen (string).
- **seguro.entity.Exclusion**: id (UUID), poliza (FK lógica), descripcion (string).
- **seguro.entity.Siniestro**: id (UUID), numeroCaso (string único), polizaId (UUID), tipo (`TipoSiniestro`: HOGAR, AUTO, VIDA, etc.), fechaSiniestro (LocalDate), descripcion (string), estado (`EstadoSiniestro`: RECIBIDO, EN_ANALISIS, DOCUMENTACION_PENDIENTE, RESUELTO), fechaApertura (Instant), ventanaDuplicadoHasta (Instant).
- **seguro.entity.TipoSiniestro**: enum (HOGAR, AUTO, VIDA, OTROS).

## DTOs

- `PolizaResponse(numeroPoliza, ramo, vigente, fechaVigenciaDesde, fechaVigenciaHasta, identificadorCorto)`
- `CoberturaResponse(coberturas: List<String>, exclusiones: List<String>, resumen: boolean, notaPrevalencia: String)`
- `DenunciaRequest(polizaId, tipoSiniestro, fechaSiniestro, descripcion)`
- `DenunciaResponse(numeroCaso, proximosPasos, plazos)`
- `SiniestroResponse(numeroCaso, estado, pendiente)`
- `DocumentacionPendienteResponse(documentos: List<String>, orientacionEnvio: String)`

## Endpoints

Todos bajo `/api/v1` (sin versionado por ahora — `api.versioning` no está habilitado).

| Método y path | Request | Éxito | Errores (HTTP — `code`) |
|---|---|---|---|
| `GET /api/v1/seguros` | — (credencial por header `X-Credencial-Sesion`) | 200 `List<PolizaResponse>` | 401 `UNAUTHORIZED`; 403 `FORBIDDEN` |
| `GET /api/v1/seguros/{polizaId}/coberturas` | — | 200 `CoberturaResponse` | 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND` |
| `POST /api/v1/siniestros` | `DenunciaRequest` | 201 `DenunciaResponse` | 400 `VALIDATION_ERROR`/`BAD_REQUEST`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND`; 409 `CONFLICT` (duplicado) |
| `GET /api/v1/siniestros/{numeroCaso}` | — | 200 `SiniestroResponse` | 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND` |
| `GET /api/v1/siniestros/{numeroCaso}/documentacion-pendiente` | — | 200 `DocumentacionPendienteResponse` | 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `NOT_FOUND` |

Notas:
- La credencial se recibe por header `X-Credencial-Sesion` (JWT de H2). H6 la valida con H2 antes de cualquier operación; si inválida/expirada/revocada → 401 `UNAUTHORIZED`.
- Autorización: solo pólizas donde el cliente figure como titular o asegurado; casos propios del titular. Beneficiario/tercero → 403 `FORBIDDEN` con derivación.
- Siniestro en estados RECIBIDO, EN_ANALISIS, DOCUMENTACION_PENDIENTE impiden duplicado dentro de ventana configurable; RESUELTO permite nueva apertura.
- Número de caso único generado por el servicio.

## Mappers

- `seguro.mapper.PolizaMapper` (MapStruct): `Poliza` ↔ `PolizaResponse`.
- `seguro.mapper.SiniestroMapper`: `Siniestro` ↔ `SiniestroResponse`/`DenunciaResponse`.

## Repositories

- `PolizaRepository`: `findByTitularDocumentoTipoAndTitularDocumentoNumeroAndVigenteTrue`, `findByIdAndTitular...`.
- `CoberturaRepository`: `findByPolizaId`.
- `ExclusionRepository`: `findByPolizaId`.
- `SiniestroRepository`: `findByNumeroCaso`, `findByPolizaIdAndTipoSiniestroAndEstadoInAndFechaAperturaAfter`.

## Services

- `PolizaService`: listar pólizas vigentes autorizadas del titular.
- `CoberturaService`: mostrar coberturas, exclusiones y resumen en lenguaje simple.
- `SiniestroService`: capturar datos, validar, abrir caso, asignar número único, comunicar próximos pasos.
- `DuplicadoService`: control de duplicados por póliza + tipo de evento dentro de ventana configurable.
- `DocumentacionService`: recordatorio de documentación pendiente y orientación de envío.

## Clientes externos

- `CredencialesServiceClient` (interfaz) + impl `RestClient`: `validar(credencial): ValidarCredencialResponse`. Timeouts en `RestClientConfig`.

## Error contract

- `exception/ErrorResponse`, `FieldError`, `ErrorCode` (agrega `UNAUTHORIZED`, `FORBIDDEN`), `GlobalExceptionHandler`, `NotFoundException`, `ConflictException` + `UnauthorizedException` (401), `ForbiddenException` (403).
- Ninguna respuesta incluye datos sensibles ni internals (S10: sin datos médicos ni montos de liquidación).

## Configuración parametrizable (application.yml)

- `insurance.siniestros.ventana-duplicado-dias` (default configurable)
- `clients.wa-credentials-service.base-url`

## Tests

- Unit: `PolizaService`, `CoberturaService`, `SiniestroService`, `DuplicadoService`, mockeando repositorios y `CredencialesServiceClient`.
- Integración: MockMvc por endpoint + WireMock para H2. JaCoCo ≥ 80%.
- Un test de error por endpoint que verifique status + `$.code` + `$.timestamp`.

## Riesgos / decisiones pendientes

- Identidad Java del servicio nueva (base_package, artifactId, puerto): propuestos por convención; tech-lead confirma.
- Duración de ventana de duplicados: parametrizable (fuente no fija valor).
- Listas de documentación y plazos dependen del proceso formal; no se inventan.
- Autorización para datos médicos sensibles: solo si política aprobada; mientras, prohibición S10.

## Dependencias con otros hitos

- **H2 (Servicio de Acceso y Sesión)**: H6 consume `POST /api/v1/credenciales/validar` para autorizar toda operación.
- **H1 (Servicio de Usuarios)**: referencia al cliente por documento; no se llama directamente.
- **H3 (Servicio Canal WhatsApp)**: H3 invoca los endpoints de H6 con la credencial del cliente; H6 no llama a H3.
