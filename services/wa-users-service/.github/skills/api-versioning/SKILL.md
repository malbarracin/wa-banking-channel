---
name: api-versioning
description: >
  Versionado de la API REST para evolucionar sin romper a los clientes: versión en el path (/api/v1, /api/v2),
  paquetes por versión dentro de cada módulo (controller, dto y mapper versionados; service, repository y
  entity compartidos), qué cambios necesitan versión nueva, deprecación y Swagger por versión. Se activa con
  api.versioning en project.yaml.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: spring-boot-3
  applies-to: "**/controller/**, **/api/**, **/dto/**"
---

# Versionado de API REST

## ¿Aplica?

Mirá `project.yaml`:

```yaml
api:
  versioning:
    enabled: true          # false o ausente: una sola versión (abajo)
    default_version: v1
    supported_versions: [v1, v2]
```

- **`enabled: false` o sin `api.versioning`** (lo normal al empezar): una sola versión. Todos los controllers
  bajo **`/api/v1/...`** (`@RequestMapping("/api/v1/users")`), en `<modulo>/controller` como dice
  `architecture.md`, **sin** paquetes ni sufijos de versión. Igual vale la tabla de "Evolución": si un cambio
  rompe a los clientes, hay que activar el versionado y crear la v2 (no romper la v1).
- **`enabled: true`**: todo lo de abajo.

## Estructura con versiones (por módulo)

El dominio **no** se versiona; el contrato HTTP sí:

```
<base_package>/<modulo>/
├── api/
│   ├── v1/
│   │   ├── controller/   UserControllerV1        @RequestMapping("/api/v1/users")
│   │   ├── dto/          CreateUserRequestV1, UserResponseV1
│   │   └── mapper/       UserMapperV1            (MapStruct: entity ↔ DTOs de la v1)
│   └── v2/
│       ├── controller/   UserControllerV2        @RequestMapping("/api/v2/users")
│       ├── dto/          CreateUserRequestV2, UserResponseV2
│       └── mapper/       UserMapperV2
├── service/              UserService             (compartido por todas las versiones)
├── repository/           UserRepository          (compartido)
└── entity/               User                    (compartido)
```

- **Sufijo de versión obligatorio** en controllers, mappers y DTOs (`UserControllerV1`, `UserMapperV1`,
  `UserResponseV1`): Spring registra los beans por nombre de clase y dos `UserController` en paquetes distintos
  no arrancan; además evita importar el DTO de la versión equivocada.
- El **service** trabaja con el dominio (entidades o comandos internos), nunca con DTOs de una versión: cada
  controller convierte con su mapper.
- Las excepciones y el contrato de error (`exception/`) son **los mismos** en todas las versiones.
- Pasar un proyecto de una sola versión a versionado: mover `controller/` y `dto/` del módulo a `api/v1/`,
  renombrar con sufijo `V1` y **sin cambiar las rutas ni el JSON** (los clientes no se tienen que enterar).

## Evolución: ¿misma versión o nueva?

| Se puede en la misma versión | Necesita versión nueva |
|------------------------------|------------------------|
| Agregar un campo **opcional** en la request | Sacar o renombrar un campo |
| Agregar un campo en la response | Cambiar el tipo o el formato de un campo |
| Agregar un endpoint | Cambiar lo que hace un endpoint existente |
| Agregar un valor de enum que el cliente puede ignorar | Hacer obligatorio un campo que era opcional |
| Mensajes de error más claros (mismo `code`) | Cambiar códigos HTTP o `code` de error existentes |

Ante la duda: si un cliente que hoy funciona puede dejar de funcionar, es versión nueva.

## Deprecación (conviven v1 y v2)

- Controller viejo: `@Deprecated` en la clase y `@Operation(deprecated = true)` en cada endpoint.
- Respuestas de la versión deprecada con headers (un filtro o `ResponseEntity`):
  `Deprecation: true`, `Sunset: <fecha HTTP>`, `Link: </api/v2/users>; rel="successor-version"`.
- Una versión se retira recién **6 meses** después de anunciada la deprecación (o lo que diga la
  especificación), nunca en la misma entrega que publica la nueva.

## Swagger por versión

Con versionado, un grupo de springdoc por versión (lo arma la skill `api-openapi-docs` en `OpenApiConfig`):

```java
@Bean
GroupedOpenApi apiV1() {
    return GroupedOpenApi.builder().group("v1").pathsToMatch("/api/v1/**").build();
}

@Bean
GroupedOpenApi apiV2() {
    return GroupedOpenApi.builder().group("v2").pathsToMatch("/api/v2/**").build();
}
```

Swagger UI muestra un selector de versión; la especificación de cada una sale en `/v3/api-docs/v1` y
`/v3/api-docs/v2`.

## Tests

Separados por versión, en el mismo paquete que su controller: `UserControllerV1Test`, `UserControllerV2Test`
(o `CreateUserV1IntegrationTest`). Cuando sale la v2, los tests de la v1 **siguen pasando sin cambios**: es la
prueba de que no se rompió a los clientes.

## Configuración (opcional)

Solo si el código necesita leerla (ej. para el header `Sunset`), en `application.yml`:

```yaml
api:
  versioning:
    default-version: v1
    supported-versions: v1,v2
```

## Checklist

- [ ] ¿`api.versioning.enabled`? Si no: todo bajo `/api/v1`, sin paquetes de versión
- [ ] Con versionado: `<modulo>/api/vN/{controller,dto,mapper}` con sufijo `VN`; service, repository y entity compartidos
- [ ] `@RequestMapping("/api/vN/...")` en cada controller
- [ ] Cambios que rompen → versión nueva (tabla de Evolución); la anterior intacta
- [ ] Versión vieja deprecada con `@Deprecated`, `deprecated = true` y headers
- [ ] `GroupedOpenApi` por versión
- [ ] Tests por versión; los de la versión anterior pasan sin tocarlos
