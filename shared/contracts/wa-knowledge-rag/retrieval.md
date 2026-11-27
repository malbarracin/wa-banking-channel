# Consulta de conocimiento del canal — v1

El agente conversacional usa esta API HTTP para recuperar fragmentos de contenido aprobado de la versión activa, junto con sus citas y una señal de confianza. El puerto local del servicio es `8102`.

## `POST /v1/retrieve`

| Header | Obligatorio | Uso |
| --- | --- | --- |
| `Authorization: Bearer <token>` | Sí | Credencial de servicio del consumidor. Una credencial ausente o inválida devuelve `401`. |
| `X-Correlation-Id` | Sí | Se devuelve el mismo valor en el header de respuesta y en el campo `correlationId` del cuerpo. Si falta, la API devuelve `400`. |

El cuerpo se envía como `application/json` y sigue el [esquema de petición v1](retrieval-request.v1.schema.json). Solo `query` es obligatorio.

| Campo | Tipo y valores |
| --- | --- |
| `query` | Texto de 1 a 1000 caracteres. |
| `filters.family` | Lista opcional de familias. Valores admitidos: `cuentas`, `tarjetas`, `seguros`, `canal`. Una lista vacía equivale a no filtrar. |
| `filters.type` | Lista opcional de tipos. Valores admitidos: `faq`, `guia`, `politica`, `mensaje`. Una lista vacía equivale a no filtrar. |
| `audience` | `publico` por defecto, con acceso solo a contenido público. `interno` incluye contenido público e interno y requiere que el consumidor esté habilitado por configuración. |
| `topK` | Entero de 1 a 10. Por defecto, 5. |

## Respuesta `200`

El cuerpo sigue el [esquema de respuesta v1](retrieval-response.v1.schema.json).

| Campo | Significado |
| --- | --- |
| `correlationId` | Identificador recibido en `X-Correlation-Id`. |
| `knowledgeVersion` | Identificador de la versión activa del conocimiento con formato `kv-N`. |
| `confidence` | Objeto con el nivel global (`level`), el puntaje (`score`) y la acción sugerida (`suggestion`). |
| `results` | Lista de hasta `topK` fragmentos, con un resultado por pieza. Cada resultado contiene `text`, `score`, `publishable` y `citation`. |

El servicio devuelve `results[].text` con los parámetros ya resueltos. El agente trata ese texto como dato citado y nunca lo ejecuta ni lo incorpora como instrucción. El contenido interno lleva `publishable: false` y se excluye de lo que el agente muestra al cliente.

Cada cita incluye `pieceId`, `title`, `type`, `family`, `audience`, `version` (entero de la pieza), `section` (texto o `null`), `ownerArea`, `approvedAt` (fecha y hora RFC 3339) y `approvalKind` (`real` o `prueba`). Todos los campos son obligatorios.

### Confianza y acción del agente

El puntaje de cada resultado parte de la similitud coseno del mejor fragmento de la pieza. Una coincidencia en la búsqueda de texto suma `KB_LEXICAL_BONUS` (por defecto `0.05`), con tope de `1.0`.

En la tabla, `s1` es el mejor puntaje y `s2` el segundo mejor de otra pieza. Los valores entre paréntesis son los valores iniciales configurables y se calibran con el set dorado.

| Condición | `level` | `suggestion` | `results` |
| --- | --- | --- | --- |
| Sin candidatos o `s1 < KB_MIN_RELEVANCE` (`0.45`) | `sin_respuesta` | `derivar` | Lista vacía |
| `s1 >= KB_CONFIDENCE_THRESHOLD` (`0.70`) | `alta` | `responder` | Hasta `topK` resultados |
| Entre ambos umbrales, con al menos dos piezas que alcanzan `KB_MIN_RELEVANCE` y `s1 - s2 <= KB_AMBIGUITY_MARGIN` (`0.05`) | `baja` | `aclarar` | Candidatos con cita |
| Entre ambos umbrales, en los demás casos | `baja` | `derivar` | Candidatos con cita |

Con `responder`, el agente usa los fragmentos publicables y cita su fuente. Con `aclarar`, pide una aclaración. Con `derivar`, incluidas todas las respuestas `sin_respuesta`, ofrece derivación a soporte humano.

## Errores de consulta

El cuerpo incluye `code`, `message` y `correlationId`, según el [esquema de error v1](error.v1.schema.json). El mensaje omite detalles internos y trazas de ejecución.

| HTTP | `code` | Causa |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Cuerpo inválido, falta `X-Correlation-Id` o filtros fuera del catálogo. |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida. |
| 403 | `AUDIENCE_FORBIDDEN` | El consumidor solicita audiencia interna y carece del permiso. |
| 503 | `KNOWLEDGE_UNAVAILABLE` | Base de datos, índice o proveedor de embeddings no disponible. El agente deriva a soporte humano. |
| 500 | `INTERNAL_ERROR` | Otro error interno. |

Un `503` indica una falla del servicio. Una consulta ejecutada sin coincidencias relevantes devuelve `200` con `sin_respuesta`.

## `GET /health`

Disponible sin autenticación. No exige `Authorization` ni `X-Correlation-Id`.

Éxito (`200`): base, extensión `vector`, índices HNSW/GIN y versión activa disponibles.

```json
{"status":"ok","database":"ok","index":"ok","knowledgeVersion":"kv-12"}
```

Degradado (`503`) si falta la base de datos, la extensión `vector`, los índices HNSW/GIN o una versión activa. `database` e `index` valen `ok` o `unavailable`; `knowledgeVersion` es `kv-N` con versión activa y `null` si no hay.

```json
{"status":"degraded","database":"ok","index":"unavailable","knowledgeVersion":null}
```

## Versionado

Un cambio incompatible se publica en `/v2/retrieve` con esquemas `*.v2.*`. `/v1/retrieve` y sus esquemas permanecen disponibles hasta que el agente migre. `knowledgeVersion` identifica la versión del contenido consultado y cambia independientemente de la versión de la API.

## Ejemplos y validación

Los ejemplos usan contenido sintético con aprobación de prueba.

- [Petición](examples/request.json)
- [Respuesta alta, responder](examples/response-alta.json)
- [Respuesta baja, aclarar](examples/response-baja.json)
- [Respuesta sin coincidencias, derivar](examples/response-sin-respuesta.json)
- [Error por indisponibilidad, HTTP 503](examples/error.json)

Desde la carpeta del contrato:

```sh
uvx check-jsonschema --check-metaschema *.v1.schema.json
uvx check-jsonschema --schemafile retrieval-request.v1.schema.json examples/request.json
uvx check-jsonschema --schemafile retrieval-response.v1.schema.json examples/response-*.json
uvx check-jsonschema --schemafile error.v1.schema.json examples/error.json
```
