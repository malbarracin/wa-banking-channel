# wa-channel-service — vínculo WhatsApp ↔ cliente

Administra el vínculo entre un número de WhatsApp y un cliente del banco: aceptación de términos, verificación de identidad, estados del canal, preferencias, bloqueo, desvinculación e historial de auditoría. Lo consume el orquestador del canal; no envía ni recibe mensajes ni emite credenciales.

## Funcionalidades

- Consultar estado del vínculo — `GET /api/v1/bindings/{whatsappNumber}`
- Aceptar / rechazar términos — `POST /api/v1/bindings/{whatsappNumber}/terms`
- Verificar identidad y activar — `POST /api/v1/bindings/{whatsappNumber}/verify`
- Perfil del canal — `GET /api/v1/bindings/{whatsappNumber}/profile`
- Preferencias — `PATCH /api/v1/bindings/{whatsappNumber}/preferences`
- Bloquear — `POST /api/v1/bindings/{whatsappNumber}/block`
- Desvincular — `POST /api/v1/bindings/{whatsappNumber}/unbind`
- Historial para soporte — `GET /api/v1/bindings/{whatsappNumber}/history`

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5 |
| Base de datos | PostgreSQL (H2 en tests) |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi (Swagger UI) |

## Requisitos

- JDK 21
- PostgreSQL (local o vía Docker Compose)
- `wa-users-service` reachable para verificación y perfil (`bank.channel.users-service.base-url`)
- Docker (opcional, para compose)

## Cómo correrlo

### Local

Desde `services/wa-channel-service`:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Puerto: `8080` (`SERVER_PORT`). Datasource: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (por defecto JDBC a `localhost:5432/wa_channel`).

### Con Docker

```bash
cp .env.example .env   # completar DB_PASSWORD y USERS_SERVICE_BASE_URL
docker compose up -d --build
curl http://localhost:8080/actuator/health
```

Detalle: [`delivery/devops-local.md`](delivery/devops-local.md).

## Configuración

| Variable / propiedad | Para qué | Valor por defecto (dev) |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP | `8080` |
| `DATABASE_URL` / `USERNAME` / `PASSWORD` | PostgreSQL | JDBC local `wa_channel` |
| `bank.channel.verification.policy` | Política de verificación | `document-code` |
| `bank.channel.verification.max-attempts` | Tope de intentos por ventana | `3` |
| `bank.channel.verification.window-minutes` | Ventana de intentos | `60` |
| `bank.channel.name` | Nombre del banco en mensajes | `Banco` |
| `bank.channel.support-phone` | Teléfono de soporte en mensajes | `0800-XXX-XXXX` |
| `bank.channel.onboarding-url` | URL de alta en mensajes de derivación | `https://example.com/alta` |
| `bank.channel.internal-roles.support` | Valor de `X-Internal-Role` para historial | `support` |
| `bank.channel.internal-roles.bank-policy` | Valor de `X-Internal-Role` para block/unbind BANCO | `bank-policy` |
| `bank.channel.users-service.base-url` | Base URL de wa-users-service | `http://localhost:8081` |
| `bank.channel.users-service.timeout-ms` | Timeout HTTP a usuarios | `3000` |

Las cifras de negocio las define el banco por entorno; no hardcodear secretos.

## Onboarding (estados)

1. `SIN_VINCULO` → aceptar términos → `PENDIENTE_VERIFICACION`
2. Verificar con `clientId` (ACTIVE en usuarios) → `ACTIVO` + `verifiableResult`
3. Bloqueo / desvinculación cortan el acceso (`credentialRevocationRequired=true`)
4. Desde `DESVINCULADO`, re-vinculación = términos + verificación completa

Sin aceptar términos no se piden datos de identificación. Un segundo vínculo del mismo número a otro cliente se rechaza (409).

## API

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs · copia estática en `delivery/openapi/openapi.json` y `.yaml`
- Contrato publicado: [`../../shared/contracts/wa-channel-service/`](../../shared/contracts/wa-channel-service/)

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| GET | `/api/v1/bindings/{n}` | Estado del vínculo | 200, 400, 500 |
| POST | `/api/v1/bindings/{n}/terms` | Aceptar/rechazar términos | 200, 400, 409, 500 |
| POST | `/api/v1/bindings/{n}/verify` | Verificar identidad | 200, 400, 404, 409, 423, 500 |
| GET | `/api/v1/bindings/{n}/profile` | Perfil del canal | 200, 400, 404, 500 |
| PATCH | `/api/v1/bindings/{n}/preferences` | Preferencias | 200, 400, 404, 500 |
| POST | `/api/v1/bindings/{n}/block` | Bloquear | 200, 400, 403, 404, 409, 500 |
| POST | `/api/v1/bindings/{n}/unbind` | Desvincular | 200, 400, 403, 404, 409, 500 |
| GET | `/api/v1/bindings/{n}/history` | Historial (soporte) | 200, 400, 403, 404, 500 |

### Errores

```json
{
  "code": "VERIFICATION_LOCKED",
  "message": "Por seguridad, pausamos los intentos de verificación. Comunicate con soporte.",
  "details": [],
  "timestamp": "2026-11-27T15:30:00Z"
}
```

Códigos: `VALIDATION_ERROR`, `BAD_REQUEST`, `FORBIDDEN` (403), `NOT_FOUND`, `CONFLICT`, `VERIFICATION_LOCKED` (423), `INTERNAL_ERROR`.

## Probar la API

- Postman: importar `delivery/postman/wa-channel-service.postman_collection.json` y el entorno `delivery/postman/wa-channel-service.postman_environment.json`
- Bruno: abrir la colección `delivery/bruno/wa-channel-service/`
- Paso a paso para QA: [`delivery/qa-manual.md`](delivery/qa-manual.md)

## Tests

```bash
.\mvnw.cmd -q -B clean verify   # Windows
./mvnw -q -B clean verify       # Linux / macOS
```

Unitarios + integración (MockMvc, H2, WireMock de usuarios). JaCoCo ≥ 80 %. El verify regenera OpenAPI en `delivery/openapi/` y el contrato compartido.

## Estructura del proyecto

```
src/main/java/com/bcra/channel/ws/
├── binding/     # vínculo, perfil, verificación, ciclo de vida
├── audit/       # historial de soporte
├── client/      # HTTP a wa-users-service
├── config/      # propiedades, OpenAPI, auth interna
└── exception/   # contrato de error global
```

## Documentación

| Documento | Para qué |
|---|---|
| `delivery/api.md` | Uso de la API con ejemplos curl |
| `delivery/architecture-overview.md` | Capas, estados y flujo |
| `delivery/qa-manual.md` | Cómo probar con Postman/Bruno |
| `delivery/devops-local.md` | Docker, CI y despliegue |
| `AGENTS.md` | Guía para quien toque el código |
| `specs/canal-whatsapp/` | Requisitos y arquitectura de negocio |
