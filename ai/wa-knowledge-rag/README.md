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
