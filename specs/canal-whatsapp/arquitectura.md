---
title: Canal WhatsApp — arquitectura
status: borrador
source: specs/canal-whatsapp/spec.md + specs/usuarios/arquitectura.md (servicio
  H1 ya integrado)
updated: 2026-10-26T15:09:42.297Z
---

# Arquitectura — Servicio Canal WhatsApp (H3)

## Base

- **Servicio nuevo** (no existe `pom.xml` todavía): `services/wa-channel-service`, análogo a `services/wa-users-service` (H1).
- Maven groupId: **com.bcra** (de `project.yaml` → `java.group_id`, ya fijado por H1; se reutiliza, es el groupId de la organización).
- Base package: **com.bcra.channel.ws** — **propuesto**, siguiendo el mismo patrón usado por H1
  (`<group_id>.<dominio>.ws` → `com.bcra.users.ws`). `project.yaml` hoy solo tiene un bloque `java.*` (el de H1);
  no define identidad para un segundo servicio. **Antes de TASK-1 (bootstrap)**, el tech-lead debe confirmar este
  valor (o uno distinto) y dejarlo en `project.yaml` — ver Riesgos. No bloquea este plan: es la convención ya
  usada en el repo, no una invención desde texto de negocio.
- Artifact id propuesto: `wa-channel-service`.
- Módulo de dominio: **vinculo** (el vínculo número de WhatsApp ↔ cliente; es el agregado central de H3).
- Puerto propuesto: **8081** (H1 ya usa 8080 por defecto; evita choque al correr ambos servicios en local).
- Persistencia: PostgreSQL en runtime, H2 en test (mismo patrón que H1) — **base de datos propia** del servicio
  (`wa_channel`, separada de `wa_users`): cada microservicio es dueño de sus datos.
- Integraciones salientes (síncronas, REST, sin service discovery — son 2-3 servicios conocidos, no amerita
  Eureka/Consul):
  - **H1 (wa-users-service)**: `GET /api/v1/users/{tipo}/{numero}/status` y `GET /api/v1/users/{tipo}/{numero}`
    para existencia/estado del cliente y datos de perfil. Base URL configurable (`clients.wa-users-service.base-url`).
  - **H2 (servicio de credenciales, aún no integrado al tablero)**: H3 **solicita** la revocación, no la
    ejecuta ni la emite. Llamada REST best-effort a un endpoint de H2 (ej. `POST /api/v1/credenciales/{numero}/revocacion`),
    base URL configurable (`clients.credenciales-service.base-url`). Si H2 no responde, el bloqueo/desvinculación
    de H3 se aplica igual (no deja al cliente colgado) y se audita el fallo de la solicitud — ver Riesgos.

## Árbol de paquetes

```
com.bcra.channel.ws/
├── ChannelApplication.java
├── vinculo/
│   ├── controller/      VinculoController
│   ├── service/         VinculoService, VerificacionService, VinculoAuditService
│   ├── repository/      VinculoRepository, IntentoVerificacionRepository, VinculoAuditLogRepository
│   ├── mapper/          VinculoMapper (MapStruct)
│   ├── entity/          Vinculo, VinculoEstado, MotivoBloqueo, Preferencias (embeddable),
│   │                    IntentoVerificacion, ResultadoIntento, VinculoAuditLog, AuditAccion, AuditResultado
│   ├── dto/             AceptarTerminosRequest, VerificarIdentidadRequest, VerificacionResultadoResponse,
│   │                    VinculoResponse, VinculoEstadoResponse, PerfilCanalResponse,
│   │                    ActualizarPreferenciasRequest, BloquearVinculoRequest, DesvincularRequest,
│   │                    HistorialVinculoResponse, EventoHistorialResponse
│   ├── client/          UsersServiceClient (+ dto ClienteEstadoResponse, ClientePerfilResponse),
│   │                    CredencialesServiceClient (+ dto SolicitudRevocacionRequest)
│   └── exception/       VinculoNoEncontradoException, ClienteNoEncontradoException,
│                        ClienteNoHabilitadoException, NumeroYaVinculadoException, TransicionInvalidaException,
│                        VerificacionPausadaException, CredencialInvalidaException
├── config/              OpenApiConfig, RestClientConfig (timeouts + base URLs de H1/H2)
└── exception/           ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler,
                        NotFoundException, ConflictException, LockedException, UnauthorizedException
```

`client/` es un subpaquete adicional del módulo (adaptador saliente a H1/H2), igual de aceptable que
`config/`/`exception/` transversales: no mezcla Controller/Service/Repository en un paquete plano, que es lo
que prohíbe `architecture.md`.

## Entidades

- **vinculo.entity.Vinculo**: id (UUID), `numeroWhatsapp` (string normalizado, E.164), `documentoTipo` +
  `documentoNumero` (referencia al cliente en H1, **sin FK física** — igual que `AuditLog` en H1), `estado`
  (`VinculoEstado`), `motivoBloqueo` (`MotivoBloqueo`, nullable), `terminosAceptados` (boolean) +
  `fechaAceptacionTerminos`, `preferencias` (`Preferencias` embeddable), `fechaActivacion`,
  `fechaUltimoCambioEstado`, auditoría (createdAt/updatedAt).
- **vinculo.entity.VinculoEstado** (enum): `PENDIENTE_VERIFICACION`, `ACTIVO`, `BLOQUEADO`, `DESVINCULADO`.
  Interpretación (de las Preguntas abiertas de la spec, a confirmar por TL): "sin vínculo" = no hay fila;
  "verificación fallida" = evento transitorio dentro de `PENDIENTE_VERIFICACION` (no es un estado persistido);
  intentos agotados = transición a `BLOQUEADO` con `motivoBloqueo=INTENTOS_AGOTADOS`.
- **vinculo.entity.MotivoBloqueo** (enum): `SOLICITUD_CLIENTE`, `POLITICA_BANCO`, `INTENTOS_AGOTADOS`.
- **vinculo.entity.Preferencias** (embeddable): `idioma`, `notificacionesHabilitadas`, `horarioPreferido` (nullable).
- **vinculo.entity.IntentoVerificacion**: id, `vinculoId` (FK a Vinculo), `fecha`, `resultado`
  (`ResultadoIntento`: `EXITOSO`/`FALLIDO`). Sin datos de verificación en texto plano (ni siquiera en el
  intento fallido) — solo el resultado, para contar intentos dentro de la ventana.
- **vinculo.entity.VinculoAuditLog**: mismo patrón que H1 (`AuditLog`): `accion` (`AuditAccion`: ej.
  `ACEPTACION_TERMINOS`, `VERIFICACION_EXITOSA`, `VERIFICACION_FALLIDA`, `VINCULACION`, `REVINCULACION`,
  `BLOQUEO`, `DESVINCULACION`), `resultado` (`AuditResultado`: `EXITOSO`/`RECHAZADO`), `numeroWhatsapp`,
  `documentoTipo`/`documentoNumero` (sin FK), `motivo` (breve, sin datos sensibles ni de verificación), fecha.
  Sirve dos requisitos a la vez: auditoría obligatoria y el historial resumido para soporte (mismo registro, dos
  consumidores).

## DTOs

- `AceptarTerminosRequest(numeroWhatsapp)` / `VinculoResponse(id, numeroWhatsapp, estado, fechaActivacion?)`
- `VerificarIdentidadRequest(documentoTipo, documentoNumero, datosVerificacion: Map<String,String>)` — el método
  exacto de verificación es parametrizable (Pregunta abierta de la spec: Compliance lo define); `datosVerificacion`
  es un mapa genérico validado por una estrategia pluggable (ver Riesgos), no campos fijos.
- `VerificacionResultadoResponse(exitosa, vinculoId?, numeroWhatsapp, documentoTipo?, documentoNumero?, estadoVinculo, intentosRestantes?, verificadoEn?)`
  — en éxito, **es** el resultado verificable que consume el Orquestador (H0.2) para coordinar la credencial con
  H2; no hace falta un endpoint de consulta aparte.
- `VinculoEstadoResponse(numeroWhatsapp, vinculado: boolean, estado?)` — para la consulta anti-duplicados, sin
  datos del cliente.
- `PerfilCanalResponse(nombre, apellido, nombreTratamiento, email, telefono, preferencias)` — combina datos
  propios (preferencias) con los de H1 (nombre/apellido/contacto), filtrando datos legales sensibles.
- `ActualizarPreferenciasRequest(idioma?, notificacionesHabilitadas?, horarioPreferido?)` — con
  `@JsonAnySetter` para rechazar propiedades desconocidas, igual que `UpdateUserRequest` de H1.
- `BloquearVinculoRequest(motivo, confirmacion)`, `DesvincularRequest(confirmacion)`
- `HistorialVinculoResponse(numeroWhatsapp, documentoTipo, documentoNumero, estadoActual, eventos: List<EventoHistorialResponse>)`
- `EventoHistorialResponse(accion, resultado, fecha)`

## Endpoints

Todos bajo `/api/v1/vinculos` (sin versionado por ahora — `api.versioning` no está habilitado en `project.yaml`).

| Método y path | Request | Éxito | Errores (HTTP — `code`) |
|---|---|---|---|
| `POST /api/v1/vinculos/terminos` | `AceptarTerminosRequest` | 201 `VinculoResponse` (estado `PENDIENTE_VERIFICACION`) | 400 `VALIDATION_ERROR` |
| `POST /api/v1/vinculos/{numero}/verificaciones` | `VerificarIdentidadRequest` | 200 `VerificacionResultadoResponse` (éxito o fallo con intentos restantes) | 400 `VALIDATION_ERROR`; 404 `NOT_FOUND` (cliente no existe en H1, o no aceptó términos); 409 `CONFLICT` (cliente no activo en H1; número ya vinculado a otro cliente); 423 `LOCKED` (intentos agotados en la ventana — deriva a soporte) |
| `GET /api/v1/vinculos/{numero}/estado` | — | 200 `VinculoEstadoResponse` | — (número no vinculado: 200 con `vinculado:false`, no 404 — no es un error, es información) |
| `GET /api/v1/vinculos/{numero}/perfil` | — | 200 `PerfilCanalResponse` | 401 `UNAUTHORIZED` (sin contexto de credencial válida → el cliente que llama debe re-verificar); 409 `CONFLICT` (vínculo no activo) |
| `PATCH /api/v1/vinculos/{numero}/preferencias` | `ActualizarPreferenciasRequest` | 200 `VinculoResponse` | 400 `VALIDATION_ERROR`; 401 `UNAUTHORIZED`; 404 `NOT_FOUND`; 409 `CONFLICT` (vínculo no activo) |
| `PATCH /api/v1/vinculos/{numero}/bloqueo` | `BloquearVinculoRequest` | 200 `VinculoResponse` (estado `BLOQUEADO`) + solicita revocación a H2 | 400 `VALIDATION_ERROR` (confirmación faltante); 401 `UNAUTHORIZED` (si el motivo es `SOLICITUD_CLIENTE`); 404 `NOT_FOUND`; 409 `CONFLICT` (ya bloqueado/desvinculado) |
| `PATCH /api/v1/vinculos/{numero}/desvinculacion` | `DesvincularRequest` | 200 `VinculoResponse` (estado `DESVINCULADO`) + solicita revocación a H2 | 400 `VALIDATION_ERROR`; 401 `UNAUTHORIZED`; 404 `NOT_FOUND`; 409 `CONFLICT` |
| `GET /api/v1/vinculos/{numero}/historial` | — (uso de soporte, acceso interno) | 200 `HistorialVinculoResponse` | 404 `NOT_FOUND` |

Notas:
- La **re-vinculación** (requisito de la spec) **no** tiene endpoint propio: reusa
  `POST /api/v1/vinculos/{numero}/verificaciones`. `VinculoService` distingue, según el estado previo del
  vínculo para ese número+documento, si audita `VINCULACION` (primera vez) o `REVINCULACION` (venía
  `DESVINCULADO`/`BLOQUEADO`), exigiendo siempre el proceso completo de verificación y sin reutilizar intentos
  ni credenciales anteriores (nuevo `IntentoVerificacion` desde cero).
- `401 UNAUTHORIZED` y `423 LOCKED` son códigos nuevos sobre la tabla base de `error-contract` (justificados:
  "sin credencial válida → derivar a verificación" y "intentos agotados → pausa temporal", ambos escenarios
  explícitos de la spec). `GlobalExceptionHandler` de H3 agrega `UnauthorizedException` → 401 y
  `LockedException` → 423, además de `NotFoundException`/`ConflictException` ya estándar.
- `perfil`/`preferencias`/`bloqueo` (a pedido del cliente) asumen un contexto de credencial ya validado aguas
  arriba (el Orquestador/H2 verificaron el token y propagan el número+documento, p. ej. por header); H3 no
  emite ni valida tokens, solo exige que ese contexto esté presente (`CredencialInvalidaException` si falta).
  `bloqueo` por política del banco e `historial` para soporte son llamadas internas (service-to-service / banco),
  sin ese contexto de cliente.

## Mappers

- `vinculo.mapper.VinculoMapper` (MapStruct): `Vinculo` ↔ `VinculoResponse`/`VinculoEstadoResponse`;
  `Preferencias` ↔ `ActualizarPreferenciasRequest` (ignora nulls, igual que `UserMapper` de H1).

## Repositories

- `VinculoRepository` (JpaRepository): `findByNumeroWhatsappAndEstado`, `findByDocumentoTipoAndDocumentoNumeroOrderByFechaUltimoCambioEstadoDesc`.
  Índice único parcial en Postgres sobre `numero_whatsapp` **donde** `estado = 'ACTIVO'` (blindaje de unicidad
  del vínculo activo por número ante condiciones de carrera; el chequeo transaccional en el service es la
  primera línea, el índice es la red de seguridad).
- `IntentoVerificacionRepository`: `countByVinculoIdAndResultadoAndFechaAfter` (para la ventana de intentos).
- `VinculoAuditLogRepository`: `findByNumeroWhatsappOrderByFechaDesc` (historial para soporte).

## Services

- `VinculoService`: aceptar términos, consultar estado (anti-duplicados), perfil (combina local + cliente
  `UsersServiceClient`), preferencias, bloqueo, desvinculación — cada operación termina llamando a
  `VinculoAuditService.registrar(...)` (éxito o rechazo).
- `VerificacionService`: orquesta un intento de verificación — consulta `UsersServiceClient` (existencia +
  activo en H1), cuenta intentos fallidos en la ventana vigente (`vinculo.verificacion.max-intentos` /
  `vinculo.verificacion.ventana-minutos`, configurables), valida unicidad del número, aplica la estrategia de
  verificación de identidad (pluggable, ver Riesgos) y transiciona el estado del vínculo. Si el bloqueo o la
  desvinculación tienen éxito, dispara `CredencialesServiceClient.solicitarRevocacion(...)` en el mismo
  intercambio (best-effort: un fallo de H2 no revierte el bloqueo/desvinculación local).
- `VinculoAuditService`: igual contrato que H1 (`registrar(accion, resultado, numeroWhatsapp, documentoTipo?, documentoNumero?, motivo?)`,
  `REQUIRES_NEW`) + `consultarHistorial(numeroWhatsapp)` para el endpoint de soporte (a diferencia de H1, acá
  sí hay un endpoint HTTP de lectura).

## Clientes externos

- `UsersServiceClient` (interfaz) + impl con `RestClient` (Spring 6, síncrono — no se suma WebFlux, coherente
  con `stack.md`): `consultarEstado(tipo, numero): ClienteEstadoResponse`, `consultarPerfil(tipo, numero): ClientePerfilResponse`.
  Timeouts configurados en `RestClientConfig` (connect/read cortos — es una llamada en el camino crítico de
  verificación).
- `CredencialesServiceClient` (interfaz) + impl `RestClient`: `solicitarRevocacion(numeroWhatsapp, motivo)`,
  best-effort (ver Riesgos — sin reintento automático en este hito).

## Error contract

- `exception/ErrorResponse`, `FieldError`, `ErrorCode` (agrega `UNAUTHORIZED` y `LOCKED` a los 5 códigos base),
  `GlobalExceptionHandler`, `NotFoundException`, `ConflictException` + nuevas bases `UnauthorizedException` (401)
  y `LockedException` (423).
- Ninguna respuesta de error incluye datos de verificación ni secretos (requisito explícito de auditoría/errores
  de la spec).

## Tests

- Unit: `VinculoService`, `VerificacionService` (mockeando `UsersServiceClient`, `CredencialesServiceClient` y
  repositorios) — casos de cada escenario Dado/Cuando/Entonces de la spec (términos, verificación exitosa/fallida/agotada,
  unicidad, bloqueo, desvinculación, re-vinculación).
- Integración: MockMvc por endpoint + `@RestClientTest` o WireMock para estabilizar las llamadas a H1/H2 sin
  levantar esos servicios. JaCoCo ≥ 80% (mismo gate que H1).
- Un test de error por endpoint que verifique status + `$.code` + `$.timestamp` (`error-contract`).

## Riesgos / decisiones pendientes

- **Identidad Java del servicio nueva** (`base_package`, `artifactId`, puerto): propuestos arriba por
  convención con H1; el tech-lead debe confirmarlos y completar `project.yaml` antes de la TASK de bootstrap
  (regla `java-project-identity.md` — greenfield para este servicio, aunque el repo ya tenga otro servicio Java).
- **Método de verificación de identidad**: la spec lo deja "según política, a definir con Compliance". Se
  modela como estrategia pluggable (`datosVerificacion` como mapa genérico) para no bloquear el resto del hito;
  cuando Compliance defina el método, se ajusta la validación sin tocar el contrato HTTP.
- **N máximo de intentos y ventana**: configurables en `application.yml`, sin valor fijo de la spec — necesitan
  un default razonable para poder probar (a confirmar con Compliance/producto; no bloquea el desarrollo).
- **Representante autorizado**: fuera de alcance de esta iteración (la spec no cierra el detalle). No se modela
  ningún endpoint ni campo todavía; si se necesita más adelante, es una extensión, no un cambio de contrato.
- **Revocación de credencial en H2**: H2 no está en el tablero todavía. Se modela como llamada REST síncrona
  best-effort desde H3; si falla, el bloqueo/desvinculación se aplica igual y se audita el fallo de la solicitud
  (`AuditResultado` podría necesitar un tercer valor para "aplicado pero sin confirmar revocación" — a definir
  con el tech-lead cuando exista el contrato real de H2). Reintento/cola queda fuera de este hito.
- **Validación de "credencial válida"**: se asume que la verificación del token ocurre aguas arriba (Orquestador
  H0.2 / H2) y H3 solo exige que el contexto llegue; si el mecanismo real es otro (ej. H3 valida un JWT propio),
  ajustar `config/` sin tocar el resto.
- **Formato del número de WhatsApp** en el path (`+`, espacios): normalizar a E.164 sin `+` en la URL, o usar
  codificación explícita — decisión de detalle para quien implemente el controller.
- **Base de datos propia** (`wa_channel`): evita acoplar el esquema de H3 al de H1; devops arma su propio
  `docker-compose` cuando corresponda (no es parte de este plan).

## Dependencias con otros hitos

- **H1 (Servicio de Usuarios)**: ya integrado (`main`/PR #2); H3 lo consume solo por HTTP (`UsersServiceClient`),
  sin compartir base de datos ni código.
- **H2 (credencial de sesión)**: H3 solicita la revocación y consume (indirectamente, vía Orquestador) el
  resultado de la emisión; no implementa nada de H2.
- **H0/H0.1/H0.2**: fuera de alcance total de H3 (webhooks, Kafka, orquestación del ciclo del mensaje).
