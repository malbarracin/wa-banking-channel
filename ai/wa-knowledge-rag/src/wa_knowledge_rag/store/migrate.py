"""Apply packaged SQL migrations in order in one transaction."""

from pathlib import Path

import psycopg

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.store.database import database_url


def migrate(settings: Settings) -> None:
    package_dir = Path(__file__).resolve().parents[1]
    migrations = package_dir / "migrations"
    if not migrations.is_dir():
        migrations = package_dir.parents[1] / "migrations"
    scripts = sorted(migrations.glob("[0-9][0-9][0-9][0-9]_*.sql"))
    if not scripts:
        raise RuntimeError("No se encontraron migraciones SQL.")

    with psycopg.connect(database_url(settings)) as connection:
        # Serialize concurrent invocations, including ledger creation on an empty database.
        connection.execute("SELECT pg_advisory_xact_lock(hashtext('wa_knowledge_rag.migrations'))")
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS kb_schema_migration (
                name text PRIMARY KEY,
                applied_at timestamptz NOT NULL DEFAULT now()
            )
            """
        )
        applied = {row[0] for row in connection.execute("SELECT name FROM kb_schema_migration")}
        for script in scripts:
            if script.name not in applied:
                connection.execute(script.read_text(encoding="utf-8"), prepare=False)
                connection.execute("INSERT INTO kb_schema_migration (name) VALUES (%s)", (script.name,))
