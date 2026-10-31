# wa-credentials-service

Servicio de Acceso y Sesión (H2) del canal WhatsApp: emite una credencial de sesión (JWT HS256) cuando H3
confirma identidad verificada y vínculo activo, la valida para los servicios de productos, la renueva
mientras no haya riesgo y la revoca ante bloqueo, desvinculación, fraude o decisión del banco.

Identidad Maven: `com.bcra:wa-credentials-service` · paquete base `com.bcra.credentials.ws` · base
`wa_credentials` · puerto `8082`. No gestiona usuarios (H1) ni el flujo conversacional o el vínculo (H3).

## Funcionalidades

- **Emisión** (`POST /api/v1/credenciales/emitir`): exige `vinculoActivo` e `identidadVerificada` en
  `true` y cliente `ACTIVE` en H1. Devuelve `credencial` y `fechaExpiracion`; invalida la credencial
  `ACTIVA` anterior del número con motivo `REEMPLAZO`.
- **Validación** (`POST /api/v1/credenciales/validar`): verifica firma HS256, `exp`, `iat`, `jti`, `sub`
  y `doc`, el estado persistido (activa, sin riesgo, no revocada, no vencida) y el estado del cliente en
  H1. Cualquier rechazo responde **401** con el mismo mensaje y audita `VALIDACION_FALLIDA`.
- **Revocación individual** (`POST /api/v1/credenciales/{numero}/revocacion`): revoca de inmediato la
  credencial activa. Motivos: `BLOQUEO`, `DESVINCULACION`, `FRAUDE`, `POLITICA_BANCO`,
  `REVOCACION_MASIVA`, `REEMPLAZO`; `SOLICITUD_CLIENTE` e `INTENTOS_AGOTADOS` se interpretan como
  `BLOQUEO`. `solicitadoPor` es opcional (default `CANAL`). Sin credencial activa: **404**.
- **Revocación masiva** (`POST /api/v1/credenciales/revocacion-masiva`): `TODAS` revoca todas las
  `ACTIVA`; `POR_CLIENTE` exige `segmento` (tipo y número de documento) y revoca las de ese cliente. Es
  atómica (todo o nada), idempotente y devuelve `revocadas`.
- **Historial resumido** (`GET /api/v1/credenciales/{numero}/historial`): identidad, estado efectivo de
  la última emisión y eventos, del más reciente al más antiguo. Calcula `EXPIRADA` sin modificar la
  credencial. No devuelve JWT ni `jti`.
- **Renovación automática**: job interno que prepara una entrega durable para credenciales próximas a expirar. Con
  `riesgo = true` no renueva, audita `RENOVACION` / `RECHAZADO` / `REVERIFICACION_REQUERIDA` y la
  validación responde **401**. H3 obtiene el JWT nuevo mediante el contrato de entrega en dos fases
  [`renovacion.md`](../../shared/contracts/wa-credentials-service/renovacion.md). La credencial anterior
  sigue activa hasta que H3 confirma posesión del reemplazo ya guardado; el vencimiento natural no se extiende.
- **Auditoría**: `EMISION`, `RENOVACION`, `REVOCACION` y `VALIDACION_FALLIDA`, con resultado `EXITOSO` o
  `RECHAZADO`. Nunca guarda el JWT ni claims no confiables; el motivo libre se reemplaza por un texto
  fijo.

## Stack

| Componente | Versión / uso |
|------------|---------------|
| Java | 21 |
| Spring Boot | 3.5.16 (Web MVC, Validation, Data JPA, Actuator) |
| PostgreSQL | 16 (base `wa_credentials`), Flyway para migraciones |
| H2 | Solo tests (perfil `test`, en memoria) |
| JWT | `spring-security-oauth2-jose` (HS256) |
| Mapeo | MapStruct 1.6.3 |
| Boilerplate | Lombok |
| OpenAPI | springdoc 2.8.8 |
| Build | Maven Wrapper 3.9.16 (`./mvnw`) |
| Cobertura | JaCoCo 0.8.13, mínimo 80 % de líneas |

## Requisitos

- JDK 21.
- PostgreSQL 16 accesible (local o Docker).
- `CREDENTIAL_JWT_SECRET` de al menos 32 bytes UTF-8 (obligatorio, sin default).
- Docker + Docker Compose solo si se usa el camino contenedor.
- H1 `wa-users-service` disponible para emisión y validación (default `http://localhost:8080`).
  Revocación, revocación masiva e historial no consultan H1.

## Cómo correrlo

Desde `services/wa-credentials-service`.

### Local con PostgreSQL

Crear la base `wa_credentials` y exportar las variables:

```bash
export DB_PASSWORD=tu-password
export CREDENTIAL_JWT_SECRET=clave-de-al-menos-32-bytes-utf8
./mvnw spring-boot:run
```

```powershell
$env:DB_PASSWORD="tu-password"
$env:CREDENTIAL_JWT_SECRET="clave-de-al-menos-32-bytes-utf8"
.\mvnw.cmd spring-boot:run
```

`DB_URL` por defecto: `jdbc:postgresql://localhost:5432/wa_credentials`; `DB_USERNAME` por defecto:
`postgres`. Flyway aplica `src/main/resources/db/migration` al arrancar y Hibernate no modifica el
esquema (`ddl-auto=none`). El perfil `dev` mantiene PostgreSQL y solo silencia el SQL.

### Con Docker Compose

```bash
cp .env.example .env
docker compose up -d --build
```

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

Editar `.env` antes de levantar: `DB_PASSWORD` y `CREDENTIAL_JWT_SECRET` son obligatorios. El compose
publica H2 en `8082` y PostgreSQL en `5434` (para no chocar con H1 `5432` ni H3 `5433`), espera el
healthcheck de PostgreSQL y usa el perfil `docker`. H1 se toma de
`WA_USERS_SERVICE_BASE_URL` (default `http://host.docker.internal:8080`).

Verificar:

```bash
curl http://localhost:8082/actuator/health
# {"status":"UP"}
```

Runbook completo de puertos, variables y CI: [`../../delivery/devops-local.md`](../../delivery/devops-local.md).

## Configuración

Variables de entorno (perfil default y `docker`):

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8082` | Puerto HTTP |
| `APP_PORT` | `8082` | Puerto publicado por Compose |
| `DB_URL` | `jdbc:postgresql://localhost:5432/wa_credentials` | JDBC de PostgreSQL (perfil `docker`: host `postgres`) |
| `DB_USERNAME` | `postgres` | Usuario de la base |
| `DB_PASSWORD` | — | Obligatorio; sin valor la aplicación no arranca |
| `DB_NAME` | `wa_credentials` | Base usada por Compose |
| `DB_HOST_PORT` | `5434` | Puerto host de PostgreSQL en Compose |
| `CREDENTIAL_JWT_SECRET` | — | Obligatorio, ≥ 32 bytes UTF-8, sin default |
| `CREDENTIAL_H3_SERVICE_TOKEN` | — | Secreto servicio a servicio de H3 (emitir, revocar, renovación); sin valor se deniega |
| `CREDENTIAL_BANK_SERVICE_TOKEN` | — | Secreto del banco, exclusivo para la revocación masiva; distinto del de H3 |
| `CREDENTIAL_VIGENCIA_MINUTOS` | `1440` | Vigencia del JWT en minutos (positiva) |
| `CREDENTIAL_RENOVACION_VENTANA_MINUTOS` | `30` | Ventana de renovación; positiva y menor que la vigencia |
| `CREDENTIAL_RENOVACION_HABILITADA` | `true` | Habilita el job de renovación |
| `CREDENTIAL_RENOVACION_INTERVALO_MS` | `60000` | Intervalo del job en milisegundos |
| `WA_USERS_SERVICE_BASE_URL` | `http://localhost:8080` | Base de H1 |
| `WA_USERS_SERVICE_CONNECT_TIMEOUT` | `1s` | Timeout de conexión a H1 (positivo) |
| `WA_USERS_SERVICE_READ_TIMEOUT` | `2s` | Timeout de lectura de H1 (positivo) |
| `SPRING_PROFILES_ACTIVE` | — | `dev`, `docker` o `prod` según el entorno (`test` en los tests) |

Perfiles:

| Perfil | Base | Notas |
|--------|------|-------|
| default | PostgreSQL | Flyway, `ddl-auto=none` |
| `dev` | PostgreSQL | Igual que default, `show-sql=false` |
| `docker` | PostgreSQL (`postgres`) | Pensado para Compose; puerto `APP_PORT` |
| `test` | H2 en memoria | Flyway off, `create-drop`, renovación off, secreto de test |
| `prod` | PostgreSQL | Helm; Swagger UI deshabilitada (`application-prod.yml`) |

`V001__credencial.sql` impone un `jti` único y una sola credencial `ACTIVA` por número con un índice
único parcial de PostgreSQL; por eso el esquema de `test` lo genera JPA y Flyway se desactiva (H2 no
soporta el índice parcial). `V002__auditoria_validacion_sin_identidad.sql` permite auditar rechazos sin
identidad. `V003__entrega_renovacion.sql` crea la tabla de entregas durables de renovación (sin JWT:
solo `jti`, emisión, expiración y la credencial anterior). Actuator expone solo `health` e `info`.

El JWT contiene `sub` (número E.164), `doc` (`tipo:numero`), `iat`, `exp` y `jti`, firmado HS256 con
`CREDENTIAL_JWT_SECRET`. Como el contrato de H3 no envía el UUID del vínculo, `vinculoId` conserva una
referencia interna estable derivada de número y documento; no es el ID externo de H3. El token es
exclusivamente para H3: nunca debe aparecer en mensajes al cliente, logs ni auditoría.

## API

Base: `http://localhost:8082`. Swagger UI: `/swagger-ui.html` · OpenAPI: `/v3/api-docs` (también
`/v3/api-docs.yaml`). En `prod` la UI está deshabilitada; el contrato sigue en `/v3/api-docs` y en
`delivery/openapi/`.

| Método | Ruta | Éxito | Errores principales |
|--------|------|-------|---------------------|
| POST | `/api/v1/credenciales/emitir` | 201 `credencial`, `fechaExpiracion` | 400, 401, 403, 404, 409, 503 |
| POST | `/api/v1/credenciales/validar` | 200 `vigente`, identidad y vencimiento | 400, 401 |
| POST | `/api/v1/credenciales/{numero}/revocacion` | 204 sin cuerpo | 400, 401, 403, 404, 500 |
| POST | `/api/v1/credenciales/revocacion-masiva` | 200 `revocadas` | 400, 401, 403, 500 |
| POST | `/api/v1/credenciales/renovacion/obtener` | 200 JWT sustituto (`Cache-Control: no-store`) | 400, 401, 404, 409, 500 |
| POST | `/api/v1/credenciales/renovacion/confirmar` | 204 sin cuerpo | 400, 401, 409, 500 |
| GET | `/api/v1/credenciales/{numero}/historial` | 200 `estadoActual`, `eventos` | 400, 404 |
| GET | `/actuator/health` | 200 `UP` | — |

La validación, la entrega de renovación y la confirmación reciben `{"credencial":"<JWT>"}`.
El número se expresa en E.164 (`+[1-9][0-9]{1,14}`). Emitir, revocar individualmente y revocar
masivamente exigen `Authorization: Bearer <secreto-de-servicio>` (ver la sección **Autenticación de
operaciones de credenciales** más abajo).
Las respuestas de emisión/validación solo van a servicios internos; no se muestran al cliente final.

## Errores

Un solo contrato (`code`, `message`, `details`, `timestamp` UTC) generado por `GlobalExceptionHandler`
(`@RestControllerAdvice`). `details` nunca es `null` y no se exponen valores rechazados, stacktraces ni
detalles internos. `server.error.include-stacktrace: never`.

```json
{
  "code": "UNAUTHORIZED",
  "message": "La credencial no es válida; se requiere verificar la identidad nuevamente",
  "details": [],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

| HTTP | `code` | Cuándo |
|------|--------|--------|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` | Bean Validation o número fuera de E.164 / JSON ilegible |
| 401 | `UNAUTHORIZED` | Credencial inválida, expirada, revocada, con riesgo o cliente no `ACTIVE` en H1 |
| 403 | `FORBIDDEN` | Emisión sin confirmaciones verdaderas o cliente inactivo en H1 |
| 404 | `NOT_FOUND` | Cliente inexistente en H1, sin credencial activa o sin historial |
| 409 | `CONFLICT` | Conflicto de persistencia al emitir (`jti` o credencial activa duplicada) |
| 500 | `INTERNAL_ERROR` | Error inesperado, sin stacktrace |
| 503 | `SERVICE_UNAVAILABLE` | H1 no disponible o respuesta inválida |

Detalle y ejemplos completos: [`delivery/api.md`](delivery/api.md).

## Probar la API

1. Levantar H1 (`wa-users-service`) en `8080` y H2 en `8082`.
2. Importar en Postman `delivery/postman/wa-credentials-service.postman_collection.json` y el entorno
   `delivery/postman/wa-credentials-service.postman_environment.json`.
3. Correr en orden **0. Preparación H1 → 1. Flujo feliz → 2. Errores → 3. Salud**. La carpeta 0 crea
   un cliente `ACTIVE` con documento y número únicos por corrida.
4. Alternativa nativa: abrir `delivery/bruno/wa-credentials-service/` con el entorno **local**.

La guía con la tabla de resultados esperados y los casos manuales (H1 caído, cliente suspendido,
renovación) está en [`delivery/qa-manual.md`](delivery/qa-manual.md).

## Tests

```bash
./mvnw -q -B verify
```

En PowerShell: `.\mvnw.cmd -q -B verify`. Ejecuta unitarios e integración con el perfil `test` (H2 en
memoria y H1 simulado; no requiere servicios externos), exige JaCoCo ≥ 80 % de líneas y regenera
`delivery/openapi/openapi.json` y `delivery/openapi/openapi.yaml` con `OpenApiExportTest`.

Si `~/.m2` está restringido:

```powershell
.\mvnw.cmd -q -B "-Dmaven.repo.local=../../.maven-cache" verify
```

El wrapper descarga Maven 3.9.16 desde Maven Central.

## Estructura

```text
services/wa-credentials-service/
├── pom.xml, mvnw, mvnw.cmd, Dockerfile, docker-compose.yml, .env.example
├── helm/                                  # chart (perfil prod)
├── src/main/java/com/bcra/credentials/ws/
│   ├── CredentialsApplication.java
│   ├── credencial/
│   │   ├── controller/                    # CredencialController
│   │   ├── service/                       # emisión, validación, renovación, revocación, historial
│   │   ├── repository/                    # credencial y auditoría
│   │   ├── mapper/                        # CredencialMapper (MapStruct)
│   │   ├── entity/                        # CredencialSesion, CredencialAuditLog, enums
│   │   ├── dto/                           # requests y responses
│   │   ├── client/                        # UsersServiceClient (H1)
│   │   └── exception/                     # excepciones de dominio
│   ├── config/                            # OpenAPI, JWT, RestClient, renovación
│   └── exception/                         # contrato de error global
├── src/main/resources/
│   ├── application.yml, application-docker.yml, application-prod.yml, application-test.yml
│   └── db/migration/                      # V001, V002
├── src/test/java/                         # unitarios e integración (perfil test)
└── delivery/
    ├── api.md, architecture-overview.md, qa-manual.md
    ├── openapi/openapi.json, openapi.yaml
    ├── postman/
    └── bruno/wa-credentials-service/
```

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [`delivery/api.md`](delivery/api.md) | Uso de la API con `curl`, cuerpos y errores |
| [`delivery/architecture-overview.md`](delivery/architecture-overview.md) | Capas, flujo, modelo, estados, integración y decisiones |
| [`delivery/qa-manual.md`](delivery/qa-manual.md) | Importar y correr Postman/Bruno, casos esperados |
| [`delivery/openapi/openapi.json`](delivery/openapi/openapi.json) | OpenAPI exportado (JSON) |
| [`delivery/openapi/openapi.yaml`](delivery/openapi/openapi.yaml) | OpenAPI exportado (YAML) |
| [`delivery/postman/`](delivery/postman/) | Colección y entorno de Postman |
| [`delivery/bruno/wa-credentials-service/`](delivery/bruno/wa-credentials-service/) | Colección nativa de Bruno |
| [`../../delivery/devops-local.md`](../../delivery/devops-local.md) | Puertos, Docker, CI y despliegue local |
| [`../../specs/acceso-sesion/spec.md`](../../specs/acceso-sesion/spec.md) | Fuente funcional |
| [`../../specs/acceso-sesion/arquitectura.md`](../../specs/acceso-sesion/arquitectura.md) | Decisiones de arquitectura |

## Autenticación de operaciones de credenciales

H2 verifica `Authorization: Bearer <secreto-de-servicio>` antes de leer el body.
Configurar `CREDENTIAL_H3_SERVICE_TOKEN` en H2 y H3 con el mismo secreto; permite
`POST /api/v1/credenciales/emitir` y `POST /api/v1/credenciales/{numero}/revocacion`.
El banco usa un secreto diferente, `CREDENTIAL_BANK_SERVICE_TOKEN`, exclusivamente
para `POST /api/v1/credenciales/revocacion-masiva`. No son JWT de clientes.
Una credencial ausente o desconocida devuelve 401; un actor sin permiso devuelve 403.
Sin secreto configurado se rechaza el acceso de ese actor; ambos secretos deben ser diferentes.

`vinculoActivo` e `identidadVerificada` son afirmaciones de H3 autenticado, que debe
comprobarlas antes de emitir; ningún cliente de WhatsApp debe conocer el secreto de H3.
H2 conserva los controles de precondiciones y consulta del estado del cliente en H1.
Desplegar con HTTPS, secretos administrados y rotación coordinada entre H2 y H3;
nunca exponer estos secretos al navegador, LLM o usuario. La autenticación no depende
de un proxy externo. Validación e historial conservan su contrato actual.
