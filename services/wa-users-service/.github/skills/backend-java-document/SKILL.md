---
name: backend-java-document
description: >
  Documentación completa del servicio Java / Spring Boot: README.md en la raíz, carpeta delivery/ (API, QA,
  arquitectura), Javadoc, y coordina las skills api-openapi-docs (Swagger con ejemplos y openapi.json/yaml) y
  api-collections (Postman y Bruno con ejemplos). Usar en el paso del documentador, con o sin tarea del tablero.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  audience: technical-writers
  stack: spring-boot
---

# Backend Java — Documentación del servicio

Sos quien deja el servicio **listo para que otro equipo lo use sin preguntar nada**: arrancarlo, probarlo,
integrarse y operarlo. Se documenta **solo lo que existe en el código** (nunca endpoints, campos ni
comportamientos inventados).

Trabajás en el **cierre** (con todo integrado) o en una tarea de documentación. Si el paso no te asigna una
tarea del tablero, no la busques: hacé todo lo de abajo directamente.

## Orden de trabajo

1. **Relevar** (solo lectura): `pom.xml` (artifactId, versión de Java/Spring, dependencias), `project.yaml`,
   `src/main/resources/application*.yml`, todos los `*Controller`, DTOs (request/response), el contrato de
   error (`ErrorResponse` o equivalente y el `@RestControllerAdvice`), enums, `specs/**` (reglas de
   negocio) y lo que haya dejado DevOps (`Dockerfile`, `docker-compose*.yml`, `.github/workflows`, `helm/`,
   `delivery/devops-local.md`).
2. **Swagger / OpenAPI completo** → skill **`api-openapi-docs`** (anotaciones con ejemplos + `delivery/openapi/`).
3. **Colecciones para probar la API** → skill **`api-collections`** (Postman + entorno + Bruno).
4. **Javadoc** en la superficie pública (ver abajo).
5. **`delivery/`**: `api.md`, `architecture-overview.md`, `qa-manual.md`.
6. **`README.md`** en la raíz del servicio (plantilla abajo), al final, cuando ya existen todos los archivos
   que enlaza.
7. **Verificar**: `./mvnw -q -B verify` (en Windows `mvnw.cmd -q -B verify`) pasa y genera
   `delivery/openapi/openapi.json`; los JSON de las colecciones son válidos (ver `api-collections`).

## README.md (raíz del servicio) — obligatorio

Siempre en la carpeta del servicio (donde está el `pom.xml`). Si ya existe, completalo sin borrar lo que siga
siendo cierto. Secciones, en este orden:

```markdown
# <artifactId> — <nombre legible>

<Qué hace el servicio en 2–3 líneas, para quién y dentro de qué sistema.>

## Funcionalidades
- <una línea por capacidad real (alta, consulta, …), con el endpoint>

## Stack
| | |
|---|---|
| Lenguaje | Java <versión> |
| Framework | Spring Boot <versión> |
| Base de datos | <motor real; H2 en tests si aplica> |
| Build | Maven (wrapper incluido) |
| Documentación API | springdoc-openapi (Swagger UI) |

## Requisitos
- JDK <versión>, Docker (si hay compose), …

## Cómo correrlo
### Local
<comandos exactos: ./mvnw spring-boot:run, perfil, variables de entorno con ejemplo y sin secretos reales>
### Con Docker
<docker compose up … si existe; si no, decirlo>

## Configuración
| Variable / propiedad | Para qué | Valor por defecto |
|---|---|---|

## API
- Swagger UI: http://localhost:<puerto>/swagger-ui.html
- OpenAPI: http://localhost:<puerto>/v3/api-docs · copia estática en `delivery/openapi/openapi.json` y `.yaml`

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| POST | /api/v1/… | … | 201, 400, 409 |

### Errores
<contrato uniforme (skill error-contract) con un ejemplo JSON real y la tabla de códigos (VALIDATION_ERROR, NOT_FOUND, …)>

## Probar la API
- Postman: importar `delivery/postman/<artifactId>.postman_collection.json` y el entorno
  `delivery/postman/<artifactId>.postman_environment.json`
- Bruno: abrir la colección `delivery/bruno/<artifactId>/` (o importar el JSON de Postman)
- Paso a paso para QA: `delivery/qa-manual.md`

## Tests
<./mvnw verify, qué cubre (unitarios, integración), reporte de cobertura si hay>

## Estructura del proyecto
<árbol corto de paquetes con una línea por capa>

## Documentación
| Documento | Para qué |
|---|---|
| `delivery/api.md` | Uso de la API con ejemplos curl |
| `delivery/architecture-overview.md` | Capas, decisiones y flujo de una petición |
| `delivery/qa-manual.md` | Cómo probar todo con Postman/Bruno |
| `delivery/devops-local.md` | Docker, CI y despliegue (si DevOps lo dejó) |
| `specs/` | Requisitos de negocio (fuente) |
```

Reglas del README: comandos copiables y probados, puertos y rutas reales, sin secretos, en español,
enlaces relativos que existan.

## `delivery/` — entregables para otros equipos

```
delivery/
├── api.md                         # uso de cada endpoint con curl (request y response reales)
├── architecture-overview.md       # capas, paquetes, flujo de una petición, decisiones
├── qa-manual.md                   # importar colecciones, orden de ejecución, casos de error
├── openapi/
│   ├── openapi.json               # lo genera el build (skill api-openapi-docs)
│   └── openapi.yaml
├── postman/
│   ├── <artifactId>.postman_collection.json
│   └── <artifactId>.postman_environment.json
└── bruno/<artifactId>/            # colección Bruno nativa (skill api-collections)
```

`api.md`: por endpoint, un bloque con el `curl` completo, el body de ejemplo y la respuesta esperada (status +
JSON), más los errores posibles con su JSON. Los ejemplos tienen que coincidir con los de Swagger y Postman.

## Javadoc

Clase y métodos públicos de `*Controller`, `*Service` y DTOs públicos; en español, explicando el **porqué**
y las reglas de negocio (no repetir el nombre del método). Sin getters de Lombok ni privados.

## Qué podés tocar

- `README.md`, `delivery/**`
- `src/main/java/**`: **solo** Javadoc, anotaciones de OpenAPI y una clase `OpenApiConfig`
- `src/test/java/**/OpenApiExportTest.java` (lo pide `api-openapi-docs`)
- `pom.xml`: solo la dependencia de springdoc si falta
- `src/main/resources/application*.yml`: solo propiedades `springdoc.*`

**Nunca**: lógica de negocio, otros tests, las especificaciones (solo enlazar), `docs/` (es del workflow, no del producto).

## Checklist antes de terminar

- [ ] README.md completo con todas las secciones y enlaces que existen
- [ ] Swagger: cada endpoint con descripción, parámetros, body y respuestas con ejemplos (`api-openapi-docs`)
- [ ] `delivery/openapi/openapi.json` y `.yaml` generados por el build
- [ ] Postman: colección + entorno, con respuestas de ejemplo y tests (`api-collections`)
- [ ] Bruno: colección en `delivery/bruno/<artifactId>/` (`api-collections`)
- [ ] `delivery/api.md`, `architecture-overview.md`, `qa-manual.md`
- [ ] Javadoc en la superficie pública
- [ ] `./mvnw -q -B verify` pasa
- [ ] Nada inventado, sin secretos

Terminá contando qué archivos creaste o cambiaste y cómo se verifica cada entregable.
