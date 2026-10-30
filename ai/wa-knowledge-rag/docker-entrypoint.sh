#!/bin/sh
set -e

# Si DATABASE_URL está configurada, aplicar migraciones pendientes antes de levantar la API
if [ -n "$DATABASE_URL" ]; then
    python - <<'EOF' || true
from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.store.db import connect

settings = Settings()
if settings.database_url:
    try:
        with connect(settings, apply_migrations=True):
            pass
    except Exception as exc:
        print(f"Advertencia al aplicar migraciones: {exc}")
EOF
fi

exec "$@"
