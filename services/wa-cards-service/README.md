# wa-cards-service — Servicio de tarjetas

Gestiona las tarjetas del cliente del canal WhatsApp (hito H5): listado de tarjetas del titular,
consumos recientes, bloqueo temporal y desbloqueo, bloqueo definitivo por robo o extravío y registro
de aviso de viaje. Toda operación exige credencial de sesión válida (H2) y vínculo activo (H3); nunca
expone PAN, CVV ni fecha de vencimiento.

## Funcionalidades

- Listado de tarjetas del titular — `GET /api/v1/tarjetas`
- Consumos recientes por tarjeta — `GET /api/v1/tarjetas/{tarjetaId}/consumos`
- Bloqueo temporal — `POST /api/v1/tarjetas/{tarjetaId}/bloquear/temporal`
- Desbloqueo de bloqueo temporal — `POST /api/v1/tarjetas/{tarjetaId}/desbloquear/temporal`
- Bloqueo definitivo por robo o extravío — `POST /api/v1/tarjetas/{tarjetaId}/bloquear/definitivo`
- Aviso de viaje — `POST /api/v1/tarjetas/{tarjetaId}/aviso-viaje`
- Auditoría interna de listados, bloqueos, desbloqueos y avisos (sin endpoint HTTP)

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Base de datos | PostgreSQL 16 (H2 en perfil `test`) |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi (Swagger UI) |
| Mapeo | MapStruct · Lombok |

## Requisitos

- JDK 21
- Docker + Compose v2 (para levantar app + Postgres)
- Acceso a **wa-credentials-service (H2)** para validar la credencial de sesión
- Variables de entorno según perfil (ver abajo)

## Cómo correrlo

Desde `services/wa-cards-service`. En PowerShell usar `.\mvnw.cmd` en lugar de `./mvnw`.

### Local (perfil `dev`, por defecto)

```sh
# Postgres accesible (local o solo el servicio del compose)
# export DB_URL=jdbc:postgresql://localhost:5437/wa_cards
# export DB_USERNAME=app
# export DB_PASSWORD=cambiar
./mvnw spring-boot:run
```

Solo Postgres del compose y app en el host:

```sh
docker compose up -d postgres
# DB_HOST_PORT por defecto 5437; si se cambia, ajustar DB_URL
./mvnw spring-boot:run
```

El perfil `dev` actualiza el esquema (`ddl-auto=update`) y usa el puerto `8086`.

### Con Docker

```sh
cp .env.example .env   # PowerShell: copy .env.example .env
# Editar .env (no commitear)
docker compose up -d --build
curl http://localhost:8086/actuator/health
```

Detalle de CI, Helm y runbook: [`../../delivery/devops-local.md`](../../delivery/devops-local.md).

## Configuración

| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|
| `PORT` / `APP_PORT` | Puerto HTTP | `8086` |
| `DB_URL` | JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/wa_cards` (perfil `dev`: `5437`) |
| `DB_USERNAME` | Usuario BD | obligatorio en el YAML base; `dev`: `app` |
| `DB_PASSWORD` | Contraseña BD | obligatorio en el YAML base; `dev`: `cambiar` |
| `DB_NAME` | Nombre BD (compose / docker) | `wa_cards` |
| `DB_HOST_PORT` | Puerto host → Postgres | `5437` |
| `JPA_DDL_AUTO` | Estrategia de esquema en `dev` | `update` |
| `CREDENTIALS_SERVICE_BASE_URL` | Base URL de H2 | `http://localhost:8082` |
| `CREDENTIALS_CONNECT_TIMEOUT` | Timeout de conexión a H2 | `2s` |
| `CREDENTIALS_READ_TIMEOUT` | Timeout de lectura a H2 | `5s` |
| `CARDS_CONSUMOS_VENTANA_DIAS` | Ventana de consumos | `90` |
| `CARDS_CONSUMOS_TOPE_POR_MENSAJE` | Consumos por página | `10` |
| `CARDS_BLOQUEO_VALIDACION_ADICIONAL_REQUERIDA` | Exigir validación en el desbloqueo | `true` |
| `CARDS_AVISO_VIAJE_RECOMENDACIONES` | Recomendaciones del aviso de viaje | texto de seguridad del emisor |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring | `dev` (`docker`, `prod`, `test`) |

Perfiles: `dev` (ddl `update`), `docker` (JDBC a host `postgres`, UI/health expuestos), `prod`
(vars obligatorias, ddl `validate`, Swagger UI deshabilitado), `test` (H2 en memoria).

## API

- Swagger UI: http://localhost:8086/swagger-ui.html
- OpenAPI: http://localhost:8086/v3/api-docs · copia estática en `delivery/openapi/openapi.json` y `.yaml`
- Header obligatorio en todos los endpoints: `X-Credencial-Sesion` (JWT validado con H2)

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| GET | `/api/v1/tarjetas` | Lista las tarjetas del titular | 200, 401, 403 |
| GET | `/api/v1/tarjetas/{tarjetaId}/consumos` | Consumos recientes paginados | 200, 400, 401, 403, 404 |
| POST | `/api/v1/tarjetas/{tarjetaId}/bloquear/temporal` | Bloqueo temporal con confirmación | 200, 400, 401, 403, 404, 409 |
| POST | `/api/v1/tarjetas/{tarjetaId}/desbloquear/temporal` | Desbloqueo tras validación adicional | 200, 400, 401, 403, 404, 409 |
| POST | `/api/v1/tarjetas/{tarjetaId}/bloquear/definitivo` | Bloqueo irreversible con doble confirmación | 200, 400, 401, 403, 404, 409 |
| POST | `/api/v1/tarjetas/{tarjetaId}/aviso-viaje` | Registra el aviso confirmado | 201, 400, 401, 403, 404 |

Guía con curl y flujo típico: [`delivery/api.md`](delivery/api.md).

### Errores

Contrato único (`code`, `message`, `details`, `timestamp` UTC); sin stacktraces.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [{ "field": "tarjetaId", "message": "no debe ser nulo" }],
  "timestamp": "2026-10-30T12:00:00Z"
}
```

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `VALIDATION_ERROR` | Bean Validation (`@Valid`) |
| 400 | `BAD_REQUEST` | JSON ilegible, confirmación ausente o inválida, valores fuera de rango, cursor inválido |
| 401 | `UNAUTHORIZED` | Credencial ausente, expirada o revocada |
| 403 | `FORBIDDEN` | Vínculo inactivo en el canal (H3) |
| 404 | `NOT_FOUND` | Tarjeta inexistente o de otro titular (mensaje genérico) |
| 409 | `CONFLICT` | Estado que no admite la operación, bloqueo definitivo irreversible, doble confirmación pendiente |
| 500 | `INTERNAL_ERROR` | Error inesperado (sin stacktrace) |

`details` nunca es `null` (lista vacía si no aplica).

## Guía de uso (H5)

Flujo típico: **listar tarjetas → consumos → bloqueo temporal → desbloqueo temporal → aviso de viaje**.
El bloqueo definitivo es el último paso útil: revoca el uso del canal para esa tarjeta y no se revierte.

- **Bloqueo temporal**: exige `tarjetaId` coincidente con el path y `confirmar = "CONFIRMAR"`; solo
  aplica a tarjeta activa. La respuesta indica cómo desbloquear.
- **Desbloqueo temporal**: solo desde `BLOQUEO_TEMPORAL`; con
  `cards.bloqueo.validacion-adicional-requerida=true` exige `validacionAdicional: true`.
- **Bloqueo definitivo**: motivo `ROBO` o `EXTRAVIO` y dos confirmaciones exactas `CONFIRMAR`. Sin
  ellas responde **409** con la advertencia de irreversibilidad por WhatsApp, sin modificar la tarjeta.
  Repetirlo o desbloquearlo por chat devuelve **409**.
- **Aviso de viaje**: registra país, región opcional y fechas (`fechaHasta` ≥ `fechaDesde`) y confirma
  con recomendaciones. No reemplaza las políticas internacionales del emisor ni los límites del banco.

**Fuera de alcance de H5**: mostrar PAN, CVV o vencimiento; alta de tarjetas; pago de resumen;
disputa de consumos (proceso formal).

Fuente: [`specs/tarjetas/spec.md`](../../specs/tarjetas/spec.md).

### Limitaciones conocidas

1. Ventana de consumos y tope por mensaje: valores configurables (la fuente no fija cifras).
2. Validación adicional del desbloqueo: no definida por negocio; hoy es un booleano de configuración.
3. Recomendaciones del aviso de viaje: texto parametrizable, no detallado en la fuente.
4. No hay alta de tarjetas: los datos se provisionan fuera del servicio.

## Probar la API

- Postman: `delivery/postman/wa-cards-service.postman_collection.json` + entorno `.postman_environment.json`
- Bruno: abrir `delivery/bruno/wa-cards-service/`
- Paso a paso QA: [`delivery/qa-manual.md`](delivery/qa-manual.md)
- Datos de prueba locales: [`delivery/sql/001-datos-qa.sql`](delivery/sql/001-datos-qa.sql)

## Tests

```sh
./mvnw -q -B verify
```

Unitarios + integración con MockMvc y WireMock (perfil `test` / H2). JaCoCo ≥ 80 % en `verify`.
El test `OpenApiExportTest` regenera `delivery/openapi/`.

## Estructura del proyecto

```
com.bcra.cards.ws/
├── CardsApplication.java
├── tarjeta/
│   ├── controller/   # Tarjeta, Consumo, Bloqueo, AvisoViaje
│   ├── service/      # Tarjeta, Consumo, Bloqueo, AvisoViaje, AuditoriaTarjeta
│   ├── repository/   # tarjetas, consumos, bloqueos, avisos, auditoría
│   ├── mapper/       # MapStruct
│   ├── entity/       # Tarjeta, Consumo, Bloqueo, AvisoViaje, AuditoriaTarjeta
│   ├── dto/          # Request / Response
│   ├── client/       # H2 CredencialesServiceClient
│   └── exception/    # dominio (NotFound, Conflict, …)
├── config/           # OpenApiConfig, RestClientConfig
└── exception/        # ErrorResponse + GlobalExceptionHandler
```

## Documentación

| Documento | Para qué |
|---|---|
| [`delivery/api.md`](delivery/api.md) | Uso de la API con curl |
| [`delivery/architecture-overview.md`](delivery/architecture-overview.md) | Capas, decisiones y flujo |
| [`delivery/qa-manual.md`](delivery/qa-manual.md) | Postman / Bruno |
| [`delivery/openapi/openapi.json`](delivery/openapi/openapi.json) | Contrato OpenAPI exportado |
| [`../../delivery/devops-local.md`](../../delivery/devops-local.md) | Docker, CI, Helm |
| [`specs/tarjetas/spec.md`](../../specs/tarjetas/spec.md) | Requisitos de negocio |
