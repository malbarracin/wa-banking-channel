---
name: api-openapi-docs
description: >
  Swagger / OpenAPI completo con springdoc en Spring Boot 3: OpenApiConfig, cada endpoint con descripción,
  parámetros, body y TODAS sus respuestas (incluidos errores) con ejemplos reales, DTOs con @Schema y ejemplo
  por campo, y exportación automática de delivery/openapi/openapi.json y openapi.yaml desde el build.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: spring-boot
  openapi: springdoc
---

# OpenAPI completo con ejemplos (springdoc)

Objetivo: que un integrador entienda y pruebe **cada endpoint solo con Swagger UI**, con ejemplos que
funcionan tal cual. Solo endpoints, campos y errores que existen en el código.

## 1. Dependencia y configuración

`pom.xml` (si falta):

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>${springdoc.version}</version>
</dependency>
```

`application.yml`:

```yaml
springdoc:
  api-docs.path: /v3/api-docs
  swagger-ui.path: /swagger-ui.html
  swagger-ui.operationsSorter: method
  default-produces-media-type: application/json
```

En el perfil de producción (si existe): `springdoc.swagger-ui.enabled: false`.

## 2. `OpenApiConfig` (un bean `OpenAPI`)

En el paquete de configuración del servicio: `info` (título = artifactId legible, versión del `pom.xml`,
descripción de qué hace el servicio, contacto del equipo si está en `project.yaml`), `servers` (local con el
puerto real), y en `components`:

- el **schema del error** (`ErrorResponse` real) y
- **ejemplos de error reutilizables** (`components.examples`): uno por cada código del contrato (skill
  `error-contract`) que devuelve el `@RestControllerAdvice` (ej. `ValidationError`, `NotFound`, `Duplicate`, `InvalidTransition`,
  `InternalError`), con el JSON exacto que sale del handler (mismos campos, `code` y forma de `details`).

## 3. Cada controller

```java
@Tag(name = "Usuarios", description = "Alta, consulta y gestión del estado de los usuarios del canal")
```

Cada método:

```java
@Operation(
    summary = "Dar de alta un usuario",
    description = "Crea un usuario ACTIVO. El documento (tipo + número) no puede repetirse.",
    requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        content = @Content(schema = @Schema(implementation = CreateUserRequest.class),
            examples = {
                @ExampleObject(name = "DNI", summary = "Alta con DNI", value = """
                    {"tipoDocumento":"DNI","numeroDocumento":"30111222","nombre":"Ana","apellido":"Pérez"}"""),
                @ExampleObject(name = "Pasaporte", summary = "Alta con pasaporte", value = "...")
            })))
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Usuario creado",
        content = @Content(schema = @Schema(implementation = UserResponse.class),
            examples = @ExampleObject(value = "{ ...respuesta real... }"))),
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(ref = "#/components/examples/ValidationError"))),
    @ApiResponse(responseCode = "409", description = "Documento ya registrado",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(ref = "#/components/examples/Duplicate")))
})
```

Reglas:

- **Todas** las respuestas que el endpoint puede devolver (éxito y cada error del handler), cada una con
  ejemplo. Un 404 en todo endpoint que busca por id; 400 si hay validación; 409/422 si hay reglas de conflicto.
- `@Parameter(description = "...", example = "...")` en cada `@PathVariable`, `@RequestParam` y header.
- Si un body admite variantes (tipos de documento, transiciones de estado), un `@ExampleObject` por variante.
- Los ejemplos son **valores realistas y consistentes entre sí** (el mismo usuario en alta, consulta y cambio
  de estado) y respetan las validaciones del DTO (largos, formatos, enums).

## 3b. Con versionado de API

Si `project.yaml` tiene `api.versioning.enabled: true`: un `GroupedOpenApi` por versión en `OpenApiConfig`
(ver skill `api-versioning`), endpoints viejos con `@Operation(deprecated = true)`, y el test de exportación
escribe un archivo por versión: `delivery/openapi/openapi-v1.json` / `.yaml`, `openapi-v2.json` / `.yaml`
(desde `/v3/api-docs/v1`, `/v3/api-docs/v2`).

## 4. DTOs y enums

- En cada campo: `@Schema(description = "...", example = "...")`; `requiredMode = REQUIRED` si es obligatorio;
  `minLength`/`maxLength`/`pattern` si hay validación (que coincidan con las anotaciones de Bean Validation).
- Enums: `@Schema(description = "...")` y documentar qué significa cada valor (o `allowableValues`).
- Fechas: formato ISO-8601 en la descripción y en el ejemplo.

## 5. Exportar `delivery/openapi/` desde el build (obligatorio)

Un test que levanta la app con MockMvc, pide `/v3/api-docs` y escribe el JSON y el YAML. Así el archivo
siempre coincide con el código y se regenera en cada `./mvnw verify`.

`src/test/java/<paquete base>/docs/OpenApiExportTest.java`:

```java
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

    @Autowired MockMvc mvc;

    @Test
    void exportaLaEspecificacionOpenApi() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String yaml = mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Path dir = Path.of("delivery", "openapi");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("openapi.json"), new ObjectMapper().readTree(json).toPrettyString());
        Files.writeString(dir.resolve("openapi.yaml"), yaml);

        // Cada operación documentada, con al menos una respuesta de error si recibe datos
        JsonNode paths = new ObjectMapper().readTree(json).path("paths");
        assertThat(paths.size()).isGreaterThan(0);
    }
}
```

Si el proyecto usa otro perfil o base para los tests de integración, reutilizá la misma configuración que los
tests existentes (anotaciones, `@ActiveProfiles`, Testcontainers) para que levante igual.

## Verificación

1. `./mvnw -q -B verify` pasa.
2. `delivery/openapi/openapi.json` existe y tiene **todos** los paths de los controllers.
3. En el JSON: cada operación tiene `summary`, `description`, sus `parameters` con `example`, `requestBody`
   con `examples` (si recibe body) y una entrada en `responses` por cada código, cada una con `examples` o
   `example`.
4. Abrir `/swagger-ui.html` con la app levantada y probar un endpoint con el ejemplo: tiene que funcionar.
