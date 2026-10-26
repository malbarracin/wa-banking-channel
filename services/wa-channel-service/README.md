# wa-channel-service — Servicio Canal WhatsApp (H3)

Gestiona el vínculo entre un número de WhatsApp y un cliente **existente** del banco (H3):
aceptación de términos, verificación de identidad, estado anti-duplicados, perfil y preferencias,
bloqueo, desvinculación, re-vinculación e historial resumido para soporte.

No recibe webhooks ni transporta mensajes (H0/H0.1/H0.2), no da de alta clientes nuevos (H1) y no
emite ni revoca directamente la credencial de sesión (H2). Tras verificar identidad entrega un
resultado verificable para que H0.2 coordine la emisión de la credencial con H2.

Identidad Maven: `com.bcra:wa-channel-service` · paquete base `com.bcra.channel.ws` · puerto `8081`.

## Funcionalidades

- Aceptar términos del canal — `POST /api/v1/vinculos/terminos`
- Verificar identidad y vincular/re-vincular — `POST /api/v1/vinculos/{numero}/verificaciones`
- Consultar si el número ya está vinculado — `GET /api/v1/vinculos/{numero}/estado`
- Consultar perfil permitido del canal — `GET /api/v1/vinculos/{numero}/perfil`
- Actualizar preferencias — `PATCH /api/v1/vinculos/{numero}/preferencias`
- Bloquear el vínculo — `PATCH /api/v1/vinculos/{numero}/bloqueo`
- Desvincular el canal — `PATCH /api/v1/vinculos/{numero}/desvinculacion`
- Historial resumido para soporte — `GET /api/v1/vinculos/{numero}/historial`
- Auditoría interna del ciclo de vida (sin endpoint HTTP)

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Base de datos | PostgreSQL 16 (H2 en perfil `test` y en `dev`) |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi 2.8.8 (Swagger UI) |
| Mapeo | MapStruct · Lombok |
| Cobertura | JaCoCo ≥ 80 % de líneas en `verify` |

## Requisitos

- JDK 21
- Docker + Compose v2 (para levantar app + Postgres)
- **H1 `wa-users-service`** accesible para verificar identidad y consultar perfil
- H2 `credenciales-service` opcional: solo para la revocación best-effort de bloqueo/desvinculación
- Variables de entorno según perfil (ver abajo)

## Cómo correrlo

Desde `services/wa-channel-service`. En PowerShell usar `.\mvnw.cmd` en lugar de `./mvnw`.

### Local con H2 (perfil Spring `dev`)

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

H2 en memoria (`jdbc:h2:mem:wa_channel_dev`), consola en `http://localhost:8081/h2-console` y
`ddl-auto=update`. Los datos se pierden al detener el proceso.

### Local con PostgreSQL (perfil default)

```sh
# export DB_URL=jdbc:postgresql://localhost:5433/wa_channel
# export DB_USERNAME=app
# export DB_PASSWORD=cambiar
./mvnw spring-boot:run
```

El esquema se aplica una vez con `src/main/resources/db/postgresql/V001__vinculo.sql`
(`ddl-auto=none`).

### Con Docker

```sh
cp .env.example .env   # PowerShell: copy .env.example .env
# Editar .env (no commitear). Postgres host 5433 para no chocar con H1.
docker compose up -d --build
curl http://localhost:8081/actuator/health
```

Detalle de Compose, CI y Helm: [`../../delivery/devops-local.md`](../../delivery/devops-local.md).

### Dependencias entre servicios en local

| Servicio | URL por defecto | Uso desde H3 |
|---|---|---|
| H1 `wa-users-service` | `http://localhost:8080` | Estado del cliente y perfil permitido |
| H2 `credenciales-service` | `http://localhost:8082` | Solicitud de revocación (best-effort) |

Si H1 no responde, la verificación y la consulta de perfil fallan con **500** `INTERNAL_ERROR`.
Si H2 no confirma la revocación, el bloqueo/desvinculación local se mantiene y se audita el rechazo.

## Configuración

| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|
| `PORT` / `APP_PORT` | Puerto HTTP | `8081` |
| `DB_URL` | JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/wa_channel` |
| `DB_USERNAME` | Usuario BD | `postgres` (compose: ver `.env`) |
| `DB_PASSWORD` | Contraseña BD | vacío (local); obligatorio en `prod` |
| `DB_NAME` | Nombre BD (compose / docker) | `wa_channel` |
| `DB_HOST_PORT` | Puerto host → Postgres | `5433` |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring | — (`dev`, `docker`, `prod`, `test`) |
| `WA_USERS_SERVICE_BASE_URL` | Base URL de H1 | `http://localhost:8080` |
| `CREDENCIALES_SERVICE_BASE_URL` | Base URL de H2 | `http://localhost:8082` |
| `VERIFICACION_MAX_INTENTOS` / `vinculo.verificacion.max-intentos` | Intentos fallidos permitidos por ventana | `3` |
| `VERIFICACION_VENTANA_MINUTOS` / `vinculo.verificacion.ventana-minutos` | Ventana de conteo y pausa | `15` |
| `CLIENTS_CONNECT_TIMEOUT` | Timeout de conexión a H1/H2 | `1s` |
| `CLIENTS_READ_TIMEOUT` | Timeout de lectura a H1/H2 | `2s` |

Perfiles: `dev` (H2 en memoria, `ddl-auto=update`, consola H2), `docker` (JDBC a host `postgres`),
`prod` (variables obligatorias, `ddl-auto=validate`, Swagger UI off) y `test` (H2, `create-drop`).

## API

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI: http://localhost:8081/v3/api-docs · copia estática en `delivery/openapi/openapi.json` y `.yaml`
- No hay versionado por paquetes: todos los endpoints viven bajo `/api/v1/vinculos`.

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| POST | `/api/v1/vinculos/terminos` | Aceptación explícita; crea vínculo pendiente | 201, 400, 500 |
| POST | `/api/v1/vinculos/{numero}/verificaciones` | Verifica identidad y activa o re-vincula | 200, 400, 404, 409, 423, 500 |
| GET | `/api/v1/vinculos/{numero}/estado` | Anti-duplicados, sin datos del cliente | 200, 500 |
| GET | `/api/v1/vinculos/{numero}/perfil` | Perfil permitido + preferencias | 200, 401, 409, 500 |
| PATCH | `/api/v1/vinculos/{numero}/preferencias` | Idioma, notificaciones y horario | 200, 400, 401, 404, 409, 500 |
| PATCH | `/api/v1/vinculos/{numero}/bloqueo` | Bloqueo por cliente o por banco | 200, 400, 401, 404, 409, 500 |
| PATCH | `/api/v1/vinculos/{numero}/desvinculacion` | Baja del canal con confirmación | 200, 400, 401, 404, 409, 500 |
| GET | `/api/v1/vinculos/{numero}/historial` | Historial resumido para soporte | 200, 404, 500 |

Guía con curl y flujo típico: [`delivery/api.md`](delivery/api.md).

### Contexto de credencial

H3 **no valida tokens**: exige el contexto que H2/H0.2 propagan tras validar la sesión.

| Header | Dónde se exige | Valor |
|---|---|---|
| `X-Canal-Credencial-Valida` | Perfil, desvinculación y bloqueo con `SOLICITUD_CLIENTE` | `true` |
| `X-Credencial-Documento-Tipo` | Preferencias | Tipo de documento del titular |
| `X-Credencial-Documento-Numero` | Preferencias | Número de documento del titular |

Sin ese contexto: **401** `UNAUTHORIZED`.

### Verificación de identidad (placeholder)

La estrategia `VerificadorIdentidad` es reemplazable mediante un bean Spring. Hasta instalar el
método aprobado por Compliance, la implementación predeterminada **rechaza toda prueba**: un intento
válido devuelve **200** con `exitosa=false` e `intentosRestantes`; nunca activa el vínculo.

Con la configuración por defecto se puede recorrer el onboarding completo y observar el límite de
intentos (3 fallos → siguiente intento **423** `LOCKED`), pero **no** se alcanza `ACTIVO` por HTTP.
Las operaciones que exigen vínculo activo (perfil, preferencias, bloqueo y desvinculación) devuelven
**409** `CONFLICT` en ese estado. Ver [`delivery/qa-manual.md`](delivery/qa-manual.md).

### Errores

Contrato único (`code`, `message`, `details`, `timestamp` UTC); sin stacktraces ni datos sensibles.
`details` nunca es `null`.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [{ "field": "numeroWhatsapp", "message": "El número de WhatsApp es obligatorio" }],
  "timestamp": "2026-10-26T12:00:00Z"
}
```

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation o propiedad fuera del contrato |
| 400 | `BAD_REQUEST` | JSON ilegible o número fuera de E.164 |
| 401 | `UNAUTHORIZED` | Falta el contexto de credencial |
| 404 | `NOT_FOUND` | Sin términos, sin vínculo o cliente inexistente en H1 |
| 409 | `CONFLICT` | Cliente no activo en H1, número ya vinculado o estado incompatible |
| 423 | `LOCKED` | Intentos agotados en la ventana vigente |
| 500 | `INTERNAL_ERROR` | Error inesperado o fallo de H1 |

## Preguntas abiertas (Compliance / política del banco)

Estas definiciones siguen pendientes en [`specs/canal-whatsapp/spec.md`](../../specs/canal-whatsapp/spec.md)
y condicionan la operación del servicio:

1. **Método de verificación de identidad** — no definido. El puerto `VerificadorIdentidad` y el mapa
   `datosVerificacion` permiten enchufar la estrategia aprobada; el bean por defecto rechaza todo.
2. **Máximo de intentos y ventana/pausa** — configurable por entorno
   (`VERIFICACION_MAX_INTENTOS`, `VERIFICACION_VENTANA_MINUTOS`); los valores 3 y 15 minutos son
   *defaults* técnicos, no una decisión de negocio.
3. **Representante autorizado** — no modelado. Solo se implementa el titular; el alcance y la
   verificación reforzada quedan pendientes.
4. **Criterios de bloqueo por riesgo** — no definidos. Existe el endpoint de bloqueo por
   `POLITICA_BANCO`, pero la decisión y sus reglas las aporta el banco.
5. **Campos exactos de perfil y preferencias** — provisionales: contacto de H1 (nombre, apellido,
   tratamiento, email, teléfono) e idioma/notificaciones/horario. Pueden ampliarse o recortarse.
6. **Contenido y período del historial** — se devuelven todos los eventos de auditoría del número,
   sin límite de antigüedad ni paginación. El detalle y la retención quedan pendientes.

Los motivos libres de auditoría se sustituyen por un texto fijo para no persistir datos sensibles.

## Probar la API

- Postman: `delivery/postman/wa-channel-service.postman_collection.json` + entorno `.postman_environment.json`
- Bruno: abrir `delivery/bruno/wa-channel-service/`
- Paso a paso QA: [`delivery/qa-manual.md`](delivery/qa-manual.md)

## Tests

```sh
./mvnw -q -B verify
```

Unitarios + integración (perfil `test` / H2 con clientes H1/H2 simulados). JaCoCo ≥ 80 % en
`verify`. El test `OpenApiExportTest` regenera `delivery/openapi/openapi.json` y `.yaml`.

## Estructura del proyecto

```
com.bcra.channel.ws/
├── ChannelApplication.java
├── vinculo/
│   ├── controller/     # VinculoController (/api/v1/vinculos)
│   ├── service/        # VinculoService, VerificacionService, VinculoAuditService
│   ├── repository/     # Vinculo, intentos y auditoría
│   ├── mapper/         # MapStruct
│   ├── entity/         # Vinculo, estados, auditoría
│   ├── dto/            # Request / Response
│   ├── client/         # H1 (estado/perfil) y H2 (revocación)
│   └── exception/      # excepciones de dominio del vínculo
├── config/             # OpenApiConfig, clientes, política de verificación
└── exception/          # contrato de error global
```

## Documentación

| Documento | Para qué |
|---|---|
| [`delivery/api.md`](delivery/api.md) | Uso de los 8 endpoints con curl |
| [`delivery/architecture-overview.md`](delivery/architecture-overview.md) | Capas, estados, integraciones y decisiones |
| [`delivery/qa-manual.md`](delivery/qa-manual.md) | Postman / Bruno y escenarios alcanzables |
| [`delivery/openapi/openapi.yaml`](delivery/openapi/openapi.yaml) | Contrato OpenAPI exportado |
| [`../../delivery/devops-local.md`](../../delivery/devops-local.md) | Docker, CI, Helm |
| [`specs/canal-whatsapp/spec.md`](../../specs/canal-whatsapp/spec.md) | Requisitos de negocio y preguntas abiertas |
