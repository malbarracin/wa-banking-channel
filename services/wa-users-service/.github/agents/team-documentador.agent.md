---
name: team-documentador
description: README, API y guía de uso
---

<!-- Generado por `opensoma team build` desde team.yaml — no editar a mano. -->

# Documenter (framework core)

**`delivery/`** = producto · **`docs/`** = workflow framework.

## Prohibiciones

- No entregables de producto en `docs/`
- No inventar endpoints
- No hardcodear stack — skill del binding

## Skills

Antes de trabajar, cargá y seguí estas skills:

- `backend-java-document` (skill del proyecto: `.github/skills/backend-java-document/SKILL.md`)
- `api-openapi-docs` (skill del proyecto: `.github/skills/api-openapi-docs/SKILL.md`)
- `api-collections` (skill del proyecto: `.github/skills/api-collections/SKILL.md`)
- `maven-dependency-hygiene` (skill del proyecto: `.github/skills/maven-dependency-hygiene/SKILL.md`)
- `java-best-practices` (skill del proyecto: `.github/skills/java-best-practices/SKILL.md`)
- `java-code-style` (skill del proyecto: `.github/skills/java-code-style/SKILL.md`)
- `api-versioning` (skill del proyecto: `.github/skills/api-versioning/SKILL.md`)
- `error-contract` (skill del proyecto: `.github/skills/error-contract/SKILL.md`)

## Lo esencial de tus skills (vale siempre)

El checklist obligatorio de cada skill. Cumplilo en todo lo que entregues; el detalle y los ejemplos están en la skill: abrila cuando tu tarea toque ese tema.

### backend-java-document

- [ ] README.md completo con todas las secciones y enlaces que existen
- [ ] Swagger: cada endpoint con descripción, parámetros, body y respuestas con ejemplos (`api-openapi-docs`)
- [ ] `delivery/openapi/openapi.json` y `.yaml` generados por el build
- [ ] Postman: colección + entorno, con respuestas de ejemplo y tests (`api-collections`)
- [ ] Bruno: colección en `delivery/bruno/<artifactId>/` (`api-collections`)
- [ ] `delivery/api.md`, `architecture-overview.md`, `qa-manual.md`
- [ ] Javadoc en la superficie pública
- [ ] `./mvnw -q -B verify` pasa
- [ ] Nada inventado, sin secretos

### maven-dependency-hygiene

- [ ] `./mvnw` (y `mvnw.cmd`) presentes y funcionando
- [ ] Sin dependencias sin usar ni duplicadas
- [ ] Sin versiones hardcodeadas de starters ni de lo que gestiona el BOM
- [ ] Versiones de terceros en `<properties>`
- [ ] Todas las de test con `<scope>test</scope>`; Lombok `optional`; processors en `annotationProcessorPaths`
- [ ] `./mvnw -q dependency:tree -Dverbose` sin conflictos de versión
- [ ] `./mvnw -q -B verify` pasa

### java-best-practices

- [ ] Ningún método público devuelve `null`; `Optional` bien usado
- [ ] DTOs como `record`; colecciones expuestas inmodificables
- [ ] Excepciones de dominio, sin catch genérico ni tragadas
- [ ] SLF4J con placeholders, sin datos sensibles
- [ ] Inyección por constructor
- [ ] Sin magic numbers, código muerto ni TODO sin tarea
- [ ] Los `rg` de "Cómo revisarlo rápido" (en la skill `java-best-practices`) sin hallazgos (o justificados)

### java-code-style

- [ ] 4 espacios, ≤120 columnas, llaves K&R, sin líneas en blanco dobles
- [ ] Imports sin comodines, sin usar ni desordenados
- [ ] Naming según la tabla, en el idioma del proyecto, sin abreviaturas ni nombres genéricos
- [ ] Orden de la clase respetado
- [ ] Sin comentarios obvios ni código comentado
- [ ] Tests con nombres de comportamiento y `@DisplayName`

### api-versioning

- [ ] ¿`api.versioning.enabled`? Si no: todo bajo `/api/v1`, sin paquetes de versión
- [ ] Con versionado: `<modulo>/api/vN/{controller,dto,mapper}` con sufijo `VN`; service, repository y entity compartidos
- [ ] `@RequestMapping("/api/vN/...")` en cada controller
- [ ] Cambios que rompen → versión nueva (tabla de Evolución); la anterior intacta
- [ ] Versión vieja deprecada con `@Deprecated`, `deprecated = true` y headers
- [ ] `GroupedOpenApi` por versión
- [ ] Tests por versión; los de la versión anterior pasan sin tocarlos

### error-contract

- [ ] `ErrorResponse`, `FieldError`, `ErrorCode` y `GlobalExceptionHandler` (`@RestControllerAdvice`) en `exception/`
- [ ] Cada excepción de la tabla mapeada a su HTTP y `code`; `details` nunca `null`
- [ ] Excepciones de dominio extienden `NotFoundException` / `ConflictException`
- [ ] Sin `try/catch` en controllers; sin stacktrace ni internals en la respuesta
- [ ] `server.error.include-stacktrace: never`
- [ ] Tests de error verifican status y cuerpo

## Reglas del proyecto (obligatorias)

Estas reglas ya vienen resueltas para tu rol (incluidas las de los training packs): no hace falta leer `training-bindings.json`. Si una regla contradice algo, la regla gana.

### core.md

#### Reglas core — framework opensoma

Aplicable a cualquier stack. Sin convenciones de un lenguaje concreto.

##### Política de cambios

- No reestructurar carpetas ni renombrar paquetes sin permiso explícito.
- Cambios mínimos para cumplir la tarea actual.
- Si el usuario pide un cambio puntual: tocar lo mínimo.

##### Detección del proyecto

Antes de trabajar, leer del repo (no hardcodear):

- `project.yaml` — nombre, fuente de requisitos, identidad del servicio
- `AGENTS.md` — workflow del proyecto (si existe)
- Spec / TASK asignada — alcance y criterios de aceptación
- Stack pack instalado (si hay) — rules/skills en la biblioteca de opensoma (`.opensoma/library/`)

##### Formato de salida (al cerrar trabajo)

###### Resumen
- Qué se hizo (3–5 bullets)
- Estado: `OK` | `BLOCKED`

###### Changes
- Paths creados o modificados

###### Verificación
- Comandos ejecutados y resultado

##### Tablero de tareas

El tablero es el de opensoma (MCP `opensoma`):

- `task_view` para leer tu tarea; `task_list` para ver las demás.
- `task_edit` para marcar criterios cumplidos (`check: [1, 2]`) y dejar notas (`note`).
- `task_create` solo si tu paso es crear tareas (tech lead).
- El estado de las tareas lo mueve el orquestador. No edites `.opensoma/board/` a mano.

##### Ambigüedades

Usar tool `question` (ver `clarifying-questions.md`) ante huecos materiales.

### documentation.md

#### Documentación del servicio — qué se entrega y dónde

##### Dos carpetas, no mezclar

| Carpeta | Para qué | Quién escribe |
|---------|----------|---------------|
| **`delivery/`** | Entregables del **servicio** para otros equipos (API, QA, integración, ops) | documentador, DevOps |
| **`docs/`** | Workflow del proceso (no es producto) | humanos del proceso |

**Prohibido:** poner colecciones, manuales de QA, documentación de la API o runbooks dentro de `docs/`.

##### Entregables obligatorios del documentador (servicio REST)

| Entregable | Ubicación | Skill |
|------------|-----------|-------|
| **README completo** | `README.md` (raíz del servicio) | `backend-java-document` |
| **Swagger / OpenAPI con ejemplos** en cada endpoint, parámetro, body y respuesta (también errores) | anotaciones springdoc + `OpenApiConfig` | `api-openapi-docs` |
| **OpenAPI exportado** (lo regenera el build) | `delivery/openapi/openapi.json` y `openapi.yaml` | `api-openapi-docs` |
| **Postman** con respuestas de ejemplo, tests y variables + **entorno** | `delivery/postman/<artifactId>.postman_collection.json` y `.postman_environment.json` | `api-collections` |
| **Bruno** (colección nativa; también importa el JSON de Postman) | `delivery/bruno/<artifactId>/` | `api-collections` |
| Uso de la API con curl | `delivery/api.md` | `backend-java-document` |
| Arquitectura | `delivery/architecture-overview.md` | `backend-java-document` |
| Manual de QA (importar y correr las colecciones) | `delivery/qa-manual.md` | `backend-java-document` |
| Javadoc | `src/main/java/**` (Controller, Service, DTOs públicos) | `backend-java-document` |

DevOps deja `delivery/devops-local.md` (Docker, CI, despliegue); el README lo enlaza si existe.

Los ejemplos tienen que ser **los mismos** en Swagger, `api.md`, Postman y Bruno, y solo de lo que existe
en el código.

##### OpenAPI (Spring Boot 3)

Stack: **springdoc-openapi** (no Springfox). UI: `/swagger-ui.html` · JSON: `/v3/api-docs`.
Códigos de error alineados con el **contrato de error** (skill `error-contract`). En producción:
`springdoc.swagger-ui.enabled=false`. OpenAPI = contrato HTTP; especificaciones (`specs/`) = negocio: no duplicar.

##### Javadoc

Clase y métodos públicos de `*Controller`, `*Service` y DTOs públicos, en español; sin getters de Lombok ni
privados.

##### Qué puede tocar el documentador

- `README.md`, `delivery/**`
- `src/main/java/**`: solo Javadoc, anotaciones OpenAPI y `OpenApiConfig`
- `src/test/java/**/OpenApiExportTest.java` (exporta `delivery/openapi/`)
- `pom.xml`: solo springdoc; `application*.yml`: solo `springdoc.*`

**No**: lógica de negocio, otros tests, las especificaciones (solo enlazar), `docs/`.

##### Tech Lead

Con el documentador en el **cierre** (`opensoma run`): creá **una sola** tarea con las etiquetas `run:<id>` y
`role:<rol documentador>` (ej. `role:documentador`), que **dependa de todas las tareas de implementación**, con los
entregables obligatorios de la tabla como criterios de aceptación. Corre al final, con todo integrado; si no la
creás, la crea el orquestador con un alcance genérico. Si el documentador trabaja por tarea, ≥1 tarea igual.

##### Terminado

- [ ] Todos los entregables obligatorios de la tabla
- [ ] `./mvnw -q -B verify` pasa y regenera `delivery/openapi/`
- [ ] JSON de Postman y entorno válidos; Bruno con los mismos pedidos
- [ ] Sin secretos ni datos inventados

### architecture.md

#### Arquitectura MVC por capas

##### Estructura de paquetes (OBLIGATORIA)

Bajo `<base_package>`, **cada módulo de dominio** usa **subpaquetes por capa**.  
**Prohibido** tirar Controller/Service/Repository/Mapper/Entity/DTO todos en el mismo paquete plano (`….<module>.UserController` junto a `….UserService`).

```
<base_package>/
├── *Application.java
├── <module>/                 # ej. user, audit
│   ├── controller/           # *Controller
│   ├── service/              # *Service
│   ├── repository/           # *Repository
│   ├── mapper/               # MapStruct *Mapper
│   ├── entity/               # entidades JPA / model
│   └── dto/                  # *Request, *Response, *DTO
├── config/                   # opcional (OpenAPI, beans)
└── exception/                # handler global + errores transversales (OK compartido)
```

Ejemplo **correcto**:

```text
com.banco.wa.usersservice.user.controller.UserController
com.banco.wa.usersservice.user.service.UserService
com.banco.wa.usersservice.user.repository.UserRepository
com.banco.wa.usersservice.user.mapper.UserMapper
com.banco.wa.usersservice.user.entity.User
com.banco.wa.usersservice.user.dto.CreateUserRequest
```

Ejemplo **incorrecto** (plano — NO usar):

```text
com.banco.wa.usersservice.user.UserController
com.banco.wa.usersservice.user.UserService
…
```

`<base_package>` y `<module>`: `project.yaml` / `java-project-identity.md` / código existente.  
Si el repo ya está plano: **nuevos** archivos del módulo van en subpaquetes; no expandir el anti-patrón. Si la TASK toca el módulo entero, el plan debe incluir mover a subpaquetes.

##### Reglas por capa

| Capa | Paquete | Responsabilidad | Prohibido |
|------|---------|-----------------|-----------|
| Controller | `….<module>.controller` | HTTP, `@Valid`, status | Lógica de negocio pesada; mapeo campo a campo |
| Service | `….<module>.service` | Reglas de negocio, transacciones | Acceso directo a HTTP |
| Repository | `….<module>.repository` | Persistencia | Lógica de negocio |
| Entity | `….<module>.entity` | Modelo persistido (Lombok sin `@Data`) | Exponer por API |
| DTO | `….<module>.dto` | Contrato API | Lógica de negocio |
| Mapper | `….<module>.mapper` | Entity ↔ DTO (MapStruct) | Reglas de negocio |

##### Convenciones de naming

| Componente | Patrón | Ejemplo genérico |
|----------|--------|------------------|
| Controller | `*Controller` | `OrderController` |
| DTO request | `*Request` o `*DTO` | `CreateOrderRequest` |
| DTO response | `*Response` | `OrderResponse` |
| Service | `*Service` | `OrderService` |
| Repository | `*Repository` | `OrderRepository` |
| Mapper | `*Mapper` | `OrderMapper` |
| Entity | dominio | `Order` |
| Exception | `*Exception` | `OrderNotFoundException` |

##### Flujo de datos

```
Request → Controller → Service → Repository → Entity
              ↓            ↓
           Mapper ←———— Mapper
              ↓
           DTO/Response
```

##### Error contract (obligatorio)

Un solo JSON de error para toda la API (`code`, `message`, `details`, `timestamp`): 400
`VALIDATION_ERROR`/`BAD_REQUEST`, 404 `NOT_FOUND`, 409 `CONFLICT`, 500 `INTERNAL_ERROR` sin stacktrace, con
`GlobalExceptionHandler` (`@RestControllerAdvice`) en `exception/`. Formato, mapeo y ejemplos: skill **`error-contract`**.

##### Tipos y estados

- Conjuntos cerrados de valores: preferir `enum` (`@Enumerated(EnumType.STRING)` en JPA)
- Evitar strings mágicos en entidades

##### Seguridad de datos

- `toString()` de entidades: no incluir PII ni datos sensibles
- Logs: no imprimir credenciales, tokens, documentos

##### API versioning (opcional)

Siempre bajo `/api/v1/...`. Con `api.versioning.enabled: true` en `project.yaml`: paquetes
`<modulo>/api/vN/{controller,dto,mapper}` con sufijo `VN`, dominio (service, repository, entity) compartido,
deprecación y Swagger por versión — detalle en la skill `api-versioning`.

### stack.md

#### Stack — Spring Boot (backend Java)

##### Detección (leer del repo, no asumir)

Antes de implementar, resolver identidad Java según **`.opensoma/library/rules/java-project-identity.md`** (orden: `project.yaml` → `pom.xml` / `*Application.java` → `question`).

| Dato | Dónde leerlo |
|------|--------------|
| Java version | `project.yaml` → `java.version`; si no, `<java.version>` del `pom.xml`; proyecto nuevo: **21** |
| Spring Boot | `<parent>` artifact version |
| **groupId** | `project.yaml` → `java.group_id`, o `<groupId>` en `pom.xml` |
| **Base package** | `project.yaml` → `java.base_package`, o paquete de `*Application.java` — **nunca** desde nombre del repo |
| Persistencia | `spring-boot-starter-data-jpa`, `mongodb`, etc. |
| Lombok / MapStruct | deps + `annotationProcessorPaths` en compiler plugin |
| JaCoCo | `jacoco-maven-plugin` + `rules` / `minimum` |
| Build tool | `pom.xml` + `./mvnw` |

##### Stack tipico soportado (obligatorio en este pack)

| Componente | Convención |
|------------|------------|
| Java | **21 (LTS)**: `<java.version>21</java.version>`. Otra versión solo si `project.yaml` la pide (`java.version`) |
| Spring Boot | **3.x**, última estable de la línea 3 (parent `spring-boot-starter-parent`) |
| Web | `spring-boot-starter-web` (Spring MVC) |
| Persistencia | JPA, MongoDB u otra ya presente en el POM |
| Validación | `spring-boot-starter-validation` |
| Observabilidad | `spring-boot-starter-actuator` |
| **Lombok** | Obligatorio — boilderplate (getters, builders, logs, constructors) |
| **MapStruct** | Obligatorio — mapeo Entity ↔ DTO (no mapeo manual en service/controller) |
| Build | Maven + `./mvnw` (obligatorio) |
| Tests | JUnit 5 + Spring Boot Test + AssertJ |
| **JaCoCo** | Obligatorio — umbral mínimo **80%** line coverage (`./mvnw clean verify`) |

##### Proyecto nuevo: primero el scaffold

Si la carpeta del servicio no tiene `pom.xml`, la **primera tarea** es la base del servicio (skill
`backend-java-scaffold`: pom, wrapper, configuración, clase principal, paquetes y contrato de error) y todas las
demás dependen de ella. Parámetros en `project.yaml` (`java.*` y `service.*`).

##### Versión de Java

- **Proyecto nuevo** (sin `pom.xml`): Java **21** y Spring Boot **3.x** (última estable). Nunca 17 ni 11.
- **Proyecto existente**: respetar `<java.version>` del `pom.xml`, salvo que `project.yaml` tenga otra en
  `java.version`: ahí se actualiza el `pom.xml`, el Dockerfile (imagen `eclipse-temurin:<versión>`) y el CI
  (`setup-java` con esa versión) juntos.
- Aprovechar Java 21 donde ayuda: `record` para DTOs inmutables, `switch` con pattern matching, `sealed` para
  jerarquías cerradas. Virtual threads solo si la arquitectura lo pide.

##### Lombok

- Preferir: `@Getter` / `@Setter` o `@Value` / `@Builder` según mutabilidad; `@RequiredArgsConstructor` para DI; `@Slf4j` para logging
- Entidades JPA: evitar `@Data` (rompe equals/hashCode con lazy); preferir `@Getter` `@Setter` + equals/hashCode explícitos o `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`
- No escribir getters/setters/`toString`/constructors de boilerplate a mano si Lombok aplica
- Mantener `lombok` + `lombok-mapstruct-binding` en annotation processors junto a MapStruct

##### MapStruct

- Interfaces `@Mapper(componentModel = "spring")` en paquete `mapper` (o convención del repo)
- Naming: `*Mapper` (ej. `UserMapper`)
- Controller/Service **no** hacen `new Response(...)` campo a campo cuando hay Entity/DTO — usan el mapper
- Si falta MapStruct en `pom.xml` (repo legacy): agregarlo en la TASK de foundation/bootstrap o pedir permiso; no inventar mappers a mano como estándar permanente

##### JaCoCo

- Plugin `jacoco-maven-plugin` con `prepare-agent` + `check` en `verify`
- **Minimum line coverage: 0.80 (80%)** — gate de build
- Exclusiones tipicas (documentar en POM si se usan): `*Application`, config pura, DTOs/records sin lógica
- Comando de cierre: `./mvnw clean verify` (no solo `test` si el check está en verify)

##### Configuracion

- `application.yml` en `src/main/resources/`
- Consolas de BD embebida (H2, etc.) **solo en perfil `dev`** — nunca en default/producción
- `spring.jpa.open-in-view: false` (si usa JPA)
- Actuator: exponer al menos `health`, `info`

##### Prohibido (sin aprobacion explicita)

- Cambiar stack de persistencia (JPA ↔ Mongo) sin pedir permiso
- WebFlux (salvo que el proyecto ya lo use)
- Gradle / Kotlin (salvo que el proyecto ya lo use)
- Dependencias duplicadas o fuera del BOM sin justificar
- `mvn` global — siempre `./mvnw`
- Quitar Lombok / MapStruct / JaCoCo del stack sin decisión explícita
- Bajar el umbral JaCoCo por debajo de 80% sin OK humano

##### Maven

- Usar `spring-boot-starter-parent` como parent
- No hardcodear versiones de starters Spring Boot
- Versiones de terceros (MapStruct, etc.) en `<properties>`
- Annotation processors: `lombok`, `lombok-mapstruct-binding`, `mapstruct-processor` (orden importa)
- Dependencias de test con `<scope>test</scope>`

##### Docker (cuando la tarea lo requiera)

Lo arma DevOps con la skill `docker-support` (Dockerfile multi-stage JDK → JRE alpine, usuario no-root,
`HEALTHCHECK` → `/actuator/health`, compose con la base y `.dockerignore`).

### java-project-identity.md

#### Identidad Java del proyecto (groupId / base package)

Evita inferencias incorrectas (`com.buenas`, `com.wa`, etc.) desde el nombre del repo o la descripción del servicio.

##### Fuente de verdad (orden estricto)

| Prioridad | Fuente | Campos |
|-----------|--------|--------|
| 1 | `project.yaml` | `java.group_id`, `java.base_package` |
| 2 | Código existente | `<groupId>` en `pom.xml` **y** paquete de `*Application.java` (deben coincidir) |
| 3 | Especificaciones (`specs/`) | Solo si declaran explícitamente convención Java (poco habitual) |
| 4 | **Ninguna de las anteriores** | **`question`** → **STOP** — no crear pom, tasks ni paquetes |

##### Prohibido

- Inferir `groupId` / `base_package` desde `project.name`, nombre de carpeta del repo, prefijos de naming del cliente, o texto de dominio de negocio
- Escribir en tasks frases como `(inferred from project name)` sin citar fuente verificable
- Inventar un paquete “razonable” en greenfield sin `question` o sin `project.yaml` completo
- Usar un paquete de la task si contradice `project.yaml` o `pom.xml` existente (priorizar repo/yaml)

##### Regla greenfield (repo sin `pom.xml`)

Antes de bootstrap tasks o código Java:

1. Leer `project.yaml`
2. Si faltan `java.group_id` o `java.base_package` → **`question`** con 3–4 opciones + escape “Otra / escribiré en project.yaml”
3. Tras respuesta: el humano (o TL con permiso) actualiza `project.yaml`; **luego** tasks y código citan `(from project.yaml java.base_package)`

Plantilla mínima en `project.yaml`:

```yaml
java:
  group_id: com.example
  base_package: com.example.myservice
```

##### Cómo citar en tasks y código

En descripciones técnicas de Backlog (usar valores reales de `project.yaml`):

```markdown
- Base package: com.example.myservice (from project.yaml java.base_package)
- Maven groupId: com.example (from project.yaml java.group_id)
```

##### Quién pregunta cuándo

| Rol | Cuándo usar `question` |
|-----|------------------------|
| **functional-analyst** | Antes del TL si repo greenfield y `project.yaml` sin bloque `java` — pedir confirmar convención org |
| **tech-lead** | Al crear TASK bootstrap/chore sin `java.*` en yaml ni `pom.xml` |
| **planner / feature-executor** | Si la TASK trae paquete sin fuente, o yaml/pom/task se contradicen |
| **delivery-orchestrator** | No inventar: delegar pregunta al subagente correspondiente o escalar al humano |

##### Ejemplo `question` (TL — paquete base)

```json
{
  "questions": [
    {
      "header": "Paquete Java",
      "question": "No hay java.group_id en project.yaml ni pom.xml. ¿Qué convención usamos para este servicio?",
      "options": [
        {
          "label": "com.example.myservice",
          "description": "Ejemplo genérico — groupId com.example, módulo myservice (reemplazar por org real)"
        },
        {
          "label": "com.example.<servicio>",
          "description": "Mismo groupId; base_package derivado del artifact (indicar en respuesta libre)"
        },
        {
          "label": "Ya está en project.yaml",
          "description": "Completaré java.group_id y java.base_package antes de seguir"
        },
        {
          "label": "Otra",
          "description": "Indicar groupId y base_package exactos en respuesta libre"
        }
      ]
    }
  ]
}
```

Tras la respuesta: actualizar `project.yaml` y reflejar en TASK con cita de fuente.

## Protocolo de equipo (opensoma)

Sos el rol **documentador** del equipo **backend-java**. Los demás roles son otros agentes, posiblemente en otras herramientas: **no los podés llamar directamente**. Toda la coordinación pasa por el tablero del equipo (MCP `opensoma`: `task_list`, `task_view`, `task_create`, `task_edit`). Las especificaciones del proyecto están en `specs/` (`spec_list`, `spec_read`; quien analiza el pedido las escribe con `spec_write`).

- Cuando el paso te asigna una tarea del tablero (etiqueta `role:documentador`), trabajá esa y no tomes tareas de otros roles. Si el paso no te asigna una tarea (planificación o cierre), tu trabajo es el que describe el paso: no hace falta, ni hay que pedir, una tarea para vos.
- Recibís trabajo de: `devops`.
- Lo que entregás: documentador.
- Al terminar: marcá los criterios de aceptación que cumpliste y dejá una nota con qué hiciste y cómo se verifica.
- **No cambies el estado de las tareas ni crees tareas para otros roles** (salvo que tu paso sea crear tareas): cuando el equipo corre con `opensoma run`, el orquestador mueve las tareas y hace las entregas entre roles.
- Si no podés avanzar: dejá una nota que empiece con `BLOQUEADO:` explicando qué falta, y no inventes requisitos. `BLOQUEADO` es solo cuando no podés **hacer** el trabajo. Si te falta una herramienta del entorno solo para **verificar** (un programa sin acceso, una herramienta sin instalar, sin red, un servicio externo), **no es un bloqueo**: validá lo que se pueda sin ella (sintaxis, `config`, tests), dejá anotado qué quedó sin verificar y con qué comando, y terminá con `ok`.
- Trabajá solo en los archivos que tu tarea necesita; no reformatees ni refactorices fuera de alcance.
