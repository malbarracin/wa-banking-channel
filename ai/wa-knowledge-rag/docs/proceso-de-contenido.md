# Proceso de actualización y aprobación del conocimiento

Guía operativa para publicar, retirar o volver atrás contenido de la base de conocimiento del canal WhatsApp.
Los estados viven en PostgreSQL; el texto fuente vive en `content/` (versionado en git). No aparecen nombres de personas: solo roles.

Orden de los comandos `kb` (el mismo de la arquitectura del componente):

1. `kb db migrate`
2. `kb ingest` (opcional `--proposal`)
3. `kb submit`
4. `kb approve` (opcional `--test`)
5. `kb params ingest` / `kb params approve` (opcional `--test`)
6. `kb publish` (una pieza o `--all-approved`)
7. `kb list` / `kb coverage` (inventario y cobertura)
8. `kb verify-pii`
9. `kb retire`
10. `kb rollback`
11. `kb sync` (opcional `--loop`; intervalo `KB_SYNC_INTERVAL`)

## 1. Preparar el entorno

Desde `ai/wa-knowledge-rag`, con `KB_DATABASE_URL` apuntando a PostgreSQL 16 + pgvector:

```bash
uv sync
uv run kb db migrate
```

`kb db migrate` aplica `migrations/` y se puede repetir. Para desarrollo y pruebas usá `KB_ENVIRONMENT=dev` o `test`. En un entorno `real` no se aceptan aprobaciones ni parámetros marcados como prueba.

## 2. Redactar o proponer una pieza

Creá un archivo Markdown en `content/<familia>/` con frontmatter:

- Obligatorios: `id`, `title`, `type`, `family`, `audience`, `owner_area`, `source_refs`, `valid_from`
- Opcionales: `valid_to`, `section`
- El cuerpo puede usar parámetros del banco con `{{nombre}}` (sin cifras fijas inventadas)
- Sin datos personales ni instrucciones al modelo (“ignorá…”, “system:”, “actuá como…”)

Ejemplo de pieza de proceso (archivo `content/canal/ejemplo-proceso.md`):

```markdown
---
id: canal-ejemplo-proceso
title: "¿Cómo consulto el estado de un trámite de ejemplo?"
type: faq
family: canal
audience: publico
owner_area: soporte
source_refs:
  - "Guía de proceso de contenido · pieza de ejemplo"
valid_from: 2026-11-27
---
Para un trámite de ejemplo, el canal informa el estado aprobado del banco y, si falta respaldo, deriva a una persona.
```

Soporte puede aportar un texto nuevo; al ingresarlo con `--proposal` queda en estado `propuesta` (no se publica sin revisión y aprobación).

## 3. Ingestar

```bash
uv run kb ingest content/canal/ejemplo-proceso.md --proposal
```

Sin `--proposal`, una pieza nueva o modificada entra en `borrador`. Podés pasar un directorio o omitir la ruta para recorrer todo `content/`:

```bash
uv run kb ingest
```

La ingesta rechaza metadatos incompletos, datos personales, patrones de instrucción y parámetros desconocidos. Si el texto supera `KB_MAX_CHARS_WHATSAPP` (1000) avisa, pero no bloquea.

## 4. Enviar a revisión

```bash
uv run kb submit canal-ejemplo-proceso
```

Pasa de `propuesta` o `borrador` a `en_revision`.

## 5. Aprobar (área dueña y compliance)

Con la política por defecto (`config/approval-policy.yaml`) hace falta el rol `area_duena` y el rol `compliance`:

```bash
uv run kb approve canal-ejemplo-proceso --role area_duena --test
uv run kb approve canal-ejemplo-proceso --role compliance --test
```

Cuando están todas las aprobaciones requeridas, la versión pasa a `aprobada`.

### Qué significa `--test`

`--test` registra una **aprobación de prueba**. Sirve solo en `dev` / `test` para ejercitar el flujo con contenido sintético. **No vale en un entorno real**: con `KB_ENVIRONMENT=real`, `kb approve --test` se rechaza y `kb publish` rechaza piezas o parámetros cuya aprobación sea solo de prueba. En producción las aprobaciones se registran sin `--test`, por los roles definidos en la política.

## 6. Cargar y aprobar parámetros

Los valores del banco (horarios, límites, teléfonos de soporte) viven en `content/parameters/parametros.yaml`. Cada cambio se versiona y se aprueba igual que una pieza:

```bash
uv run kb params ingest
uv run kb params approve horario_transferencias --role area_duena --test
uv run kb params approve horario_transferencias --role compliance --test
```

Repetí `params approve` por cada nombre del archivo. En un entorno real usá valores no marcados como prueba y aprobaciones sin `--test`.

## 7. Publicar

Una pieza ya aprobada:

```bash
uv run kb publish canal-ejemplo-proceso
```

O todo lo aprobado pendiente:

```bash
uv run kb publish --all-approved
```

La salida imprime la versión del conocimiento (`kv-N`). Publicar crea una release nueva, renderiza parámetros, embebe solo lo cambiado y **activa la release sin reiniciar la API**: la próxima consulta a `POST /v1/retrieve` ya ve el contenido.

## 8. Verificar con una consulta

Con la API levantada (`uv run kb-api`) y un consumidor configurado en `KB_CONSUMERS`:

```bash
curl -sS -X POST "http://127.0.0.1:8102/v1/retrieve" \
  -H "Authorization: Bearer <token-de-servicio>" \
  -H "X-Correlation-Id: proceso-ejemplo" \
  -H "Content-Type: application/json" \
  -d '{"query":"trámite de ejemplo estado aprobado","audience":"publico","topK":5}'
```

La respuesta incluye `knowledgeVersion`, fragmentos con cita (`pieceId`, `version`, `approvalKind`) y el nivel de confianza. Una pieza en `borrador`, `propuesta` o `en_revision` **no aparece** en ninguna consulta.

## 9. Inventario y cobertura

```bash
uv run kb list
uv run kb list --family canal --status publicada
uv run kb coverage
uv run kb coverage --published
```

`kb coverage` comprueba los temas mínimos de `content/coverage.yaml` contra los archivos fuente. Con `--published`, comprueba las FAQ públicas vigentes de la release activa (requiere `KB_DATABASE_URL`). Salida 0 = cobertura completa.

## 10. Verificación de datos personales

```bash
uv run kb verify-pii
```

Escanea el contenido publicado (incluidas publicaciones históricas). Sale distinto de 0 si encuentra datos personales no amparados por un parámetro aprobado. Correrlo en CI antes de promover un entorno.

## 11. Retirar

```bash
uv run kb retire canal-ejemplo-proceso
```

Crea una release nueva sin esa pieza. Deja de servirse de inmediato.

## 12. Volver atrás

A una versión del conocimiento anterior:

```bash
uv run kb rollback --to kv-1
```

A una versión concreta de una pieza:

```bash
uv run kb rollback canal-ejemplo-proceso --to-version 1
```

Sin argumentos, vuelve al contenido de la release padre inmediata. En todos los casos se crea una release nueva: el historial no se borra y `knowledgeVersion` cambia.

## 13. Actualización periódica

```bash
uv run kb sync
```

Ingesta solo lo cambiado en `content/`, publica lo aprobado pendiente y refleja vencimientos de vigencia. Si no hay cambios aprobados ni vencimientos, no crea release. La frecuencia la define `KB_SYNC_INTERVAL` (por defecto `24h`). En Docker Compose el servicio `wa-knowledge-rag-sync` ejecuta `kb sync --loop` con ese intervalo (`docker compose --env-file .env.compose up`). Para una corrida local continua sin compose:

```bash
uv run kb sync --loop
```

(`--loop` espera `KB_SYNC_INTERVAL` entre vueltas.)

## 14. Cómo cambiar la política de aprobación sin tocar código

Editá `config/approval-policy.yaml` (o el archivo que indique `KB_APPROVAL_POLICY_PATH`). Cada clave (`faq`, `guia`, `politica`, `mensaje`, `parameter`) lista los roles obligatorios. Ejemplo por defecto:

```yaml
faq: [area_duena, compliance]
guia: [area_duena, compliance]
politica: [area_duena, compliance]
mensaje: [area_duena, compliance]
parameter: [area_duena, compliance]
```

Para exigir un rol adicional (por ejemplo `legal` en políticas), agregalo a la lista correspondiente y volvé a aprobar las versiones en revisión con ese rol. No hace falta cambiar código ni redeployar la lógica de estados: `kb approve` y `kb publish` leen la política en cada ejecución.

## Secuencia ejecutable de ejemplo

Los comandos siguientes, en este orden, llevan la pieza de ejemplo desde propuesta hasta publicada. El test de integración `tests/integration/test_proceso_documentado.py` los ejecuta tal cual (vía el entry point `kb`) y comprueba `POST /v1/retrieve` sin reiniciar la API.

```kb-proceso
db migrate
ingest content/canal/ejemplo-proceso.md --proposal
submit canal-ejemplo-proceso
approve canal-ejemplo-proceso --role area_duena --test
approve canal-ejemplo-proceso --role compliance --test
publish canal-ejemplo-proceso
list --family canal
verify-pii
```

Después de esa secuencia, el mismo test publica el contenido mínimo completo con aprobaciones de prueba (`approve` / `params approve` con `--test` para cada pieza y parámetro según la política), y corre:

```kb-minimo
ingest
params ingest
publish --all-approved
coverage --published
verify-pii
```
