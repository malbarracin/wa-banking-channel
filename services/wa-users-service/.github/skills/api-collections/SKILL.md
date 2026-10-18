---
name: api-collections
description: >
  Colecciones para probar la API con ejemplos completos: Postman Collection v2.1 (pedidos, respuestas de ejemplo
  guardadas, tests y variables), archivo de entorno de Postman, y colección nativa de Bruno. El JSON de Postman
  también se importa en Bruno. Solo endpoints reales; ejemplos alineados con OpenAPI.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: rest-api
---

# Colecciones Postman y Bruno con ejemplos completos

Objetivo: que QA o un integrador **importe un archivo y pruebe todo el servicio en un clic** (Collection
Runner), viendo para cada pedido qué respuesta esperar. Fuente de verdad: los controllers y los ejemplos de
OpenAPI (`delivery/openapi/openapi.json` si ya existe). **Nunca** endpoints ni campos inventados.

## Archivos

```
delivery/postman/<artifactId>.postman_collection.json    # Collection v2.1 (Postman y Bruno)
delivery/postman/<artifactId>.postman_environment.json   # entorno "local"
delivery/bruno/<artifactId>/                             # colección Bruno nativa
```

`<artifactId>` = el del `pom.xml` (ej. `wa-users-service`).

## 1. Postman Collection v2.1

Estructura del JSON:

```json
{
  "info": {
    "name": "<artifactId>",
    "description": "Qué hace el servicio. Orden sugerido: carpeta 1 → 2 → 3.",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    { "key": "baseUrl", "value": "http://localhost:8080" },
    { "key": "userId", "value": "" }
  ],
  "item": [
    { "name": "1. Flujo feliz", "item": [ /* pedidos en orden: alta → consulta → cambios */ ] },
    { "name": "2. Errores", "item": [ /* 400, 404, 409… uno por caso real */ ] },
    { "name": "3. Salud", "item": [ /* actuator/health si existe */ ] }
  ]
}
```

Cada pedido (`item`):

```json
{
  "name": "Alta de usuario (DNI)",
  "request": {
    "method": "POST",
    "header": [{ "key": "Content-Type", "value": "application/json" }],
    "url": { "raw": "{{baseUrl}}/api/v1/users", "host": ["{{baseUrl}}"], "path": ["api", "v1", "users"] },
    "body": { "mode": "raw", "raw": "{\n  \"tipoDocumento\": \"DNI\", ...\n}", "options": { "raw": { "language": "json" } } },
    "description": "Qué hace, reglas de negocio relevantes y respuestas posibles."
  },
  "event": [{
    "listen": "test",
    "script": { "type": "text/javascript", "exec": [
      "pm.test('201 Created', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.test('devuelve el id', () => pm.expect(body.id).to.be.a('string'));",
      "pm.collectionVariables.set('userId', body.id);"
    ]}
  }],
  "response": [{
    "name": "201 - creado",
    "originalRequest": { /* copia del request de arriba */ },
    "status": "Created", "code": 201,
    "header": [{ "key": "Content-Type", "value": "application/json" }],
    "_postman_previewlanguage": "json",
    "body": "{\n  \"id\": \"...\", ...\n}"
  }]
}
```

Con versionado de API (`api.versioning.enabled: true`): una carpeta raíz por versión (`v1`, `v2`), cada una con
sus subcarpetas de flujo feliz y errores; los pedidos de una versión deprecada lo dicen en su descripción.

Reglas:

- **Un pedido por endpoint del flujo feliz** y **uno por cada error real** (validación, no encontrado,
  duplicado, transición inválida…), con su body que provoca ese error.
- Cada pedido trae **respuestas de ejemplo guardadas** (`response`) con el JSON real (éxito o
  `ErrorResponse`), iguales a los ejemplos de OpenAPI.
- **Tests** en cada pedido: status esperado + campos clave. El alta guarda los ids en variables
  (`pm.collectionVariables.set`) y los pedidos siguientes los usan (`{{userId}}`): el Runner corre de punta a
  punta sin editar nada.
- Datos únicos cuando la API no admite repetidos: en un `prerequest`, generar el número de documento
  (`pm.collectionVariables.set('docNumber', String(Date.now()).slice(-8))`) y usar `{{docNumber}}`.
- Descripción en la colección, en cada carpeta y en cada pedido.

## 2. Entorno de Postman

```json
{
  "name": "<artifactId> - local",
  "values": [
    { "key": "baseUrl", "value": "http://localhost:8080", "type": "default", "enabled": true }
  ],
  "_postman_variable_scope": "environment"
}
```

Sin secretos reales: si la API pide tokens, la variable va vacía con una descripción de cómo obtenerla.

## 3. Bruno

Bruno importa el JSON de Postman (Import Collection → Postman Collection) y también el
`delivery/openapi/openapi.json`. Además dejá la **colección nativa**, que se versiona y se abre directo
(Open Collection → la carpeta):

```
delivery/bruno/<artifactId>/
├── bruno.json
├── environments/local.bru
├── 1-flujo-feliz/01-alta-usuario.bru
├── 1-flujo-feliz/02-consultar-usuario.bru
└── 2-errores/01-alta-documento-duplicado.bru
```

`bruno.json`:

```json
{ "version": "1", "name": "<artifactId>", "type": "collection", "ignore": ["node_modules", ".git"] }
```

`environments/local.bru`:

```
vars {
  baseUrl: http://localhost:8080
}
```

Cada pedido (`.bru`):

```
meta {
  name: Alta de usuario (DNI)
  type: http
  seq: 1
}

post {
  url: {{baseUrl}}/api/v1/users
  body: json
  auth: none
}

body:json {
  {
    "tipoDocumento": "DNI",
    "numeroDocumento": "30111222"
  }
}

assert {
  res.status: eq 201
}

script:post-response {
  bru.setVar("userId", res.body.id);
}

docs {
  Crea un usuario ACTIVO. Respuestas: 201 creado, 400 datos inválidos, 409 documento repetido.
}
```

Mismos pedidos, nombres, orden y datos que en Postman.

## Verificación (obligatoria)

1. Los JSON son válidos:
   `node -e "for (const f of process.argv.slice(1)) JSON.parse(require('fs').readFileSync(f,'utf8'))" delivery/postman/*.json`
2. `info.schema` es el de v2.1 y la cantidad de pedidos del flujo feliz coincide con los endpoints de los
   controllers (o los `paths` de `openapi.json`).
3. Cada pedido tiene al menos una respuesta de ejemplo y un test.
4. Si está instalado `newman` y la app corre: `newman run delivery/postman/<artifactId>.postman_collection.json
   -e delivery/postman/<artifactId>.postman_environment.json` pasa (si no, explicá en `qa-manual.md` cómo correrlo).
5. `delivery/qa-manual.md` explica cómo importar en Postman y en Bruno, en qué orden correr y qué se espera.
