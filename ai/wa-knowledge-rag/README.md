# Base de conocimiento del canal WhatsApp

Para preparar el entorno con Python 3.12, ejecutá `uv sync` en esta carpeta. Consultá la ayuda con `uv run kb --help`.

La configuración se lee del entorno. Podés usar `.env.example` como guía para crear un `.env` local.
Desde la raíz del repositorio, ejecutá `python scripts/verify_ia.py` para verificar el componente.

## Esquema PostgreSQL

Configurá `KB_DATABASE_URL` y ejecutá `uv run kb db migrate` contra PostgreSQL 16 con pgvector
disponible y permisos para instalar la extensión. El comando registra las migraciones aplicadas en
`kb_schema_migration` y se puede repetir.

La migración inicial fija `vector(768)`. Las conexiones del repositorio requieren
`KB_EMBEDDING_DIMENSIONS=768`. Cambiar la dimensión requiere una migración y reindexar los embeddings.
Las operaciones de varios repositorios comparten una conexión de `store.database.transaction`.
Para consultas, usá `transaction(settings, query=True)` para limitar cada sentencia a dos segundos.

Con Docker disponible, ejecutá `uv run pytest -m integration` para probar el esquema y los repositorios.

## API de consulta

Con `KB_DATABASE_URL`, `GOOGLE_API_KEY` y `KB_CONSUMERS` configuradas, ejecutá `uv run kb-api`.
El servicio escucha en `KB_PORT` (8102 por defecto). Usá `GET /health` para comprobar la
disponibilidad y `GET /metrics` para consultar las métricas Prometheus. El formato de
`KB_CONSUMERS` se muestra en `config/consumers.example.yaml` y se carga como JSON.

## Cobertura de las preguntas frecuentes

Ejecutá `uv run kb coverage` para comprobar que los borradores cubren los temas de
`content/coverage.yaml`. Con PostgreSQL configurado, `uv run kb coverage --published`
comprueba las preguntas frecuentes públicas y vigentes de la publicación activa. Los dos
comandos salen con código distinto de cero si falta algún tema.

Las piezas se redactaron a partir de la tabla de contenido mínimo de H8. Los parámetros de
`content/parameters/parametros.yaml` son candidatos sintéticos exclusivos de desarrollo y pruebas.
