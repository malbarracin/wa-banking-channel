"""Knowledge base command-line entry point."""

from argparse import ArgumentParser

from psycopg import Error

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.store.migrate import migrate


def main() -> None:
    parser = ArgumentParser(prog="kb", description="Base de conocimiento del canal WhatsApp")
    commands = parser.add_subparsers(dest="command")
    database = commands.add_parser("db", help="Administrar el esquema de la base")
    database.add_subparsers(required=True).add_parser("migrate", help="Aplicar migraciones pendientes")
    args = parser.parse_args()
    if args.command == "db":
        try:
            migrate(Settings())
        except (ValueError, RuntimeError) as error:
            parser.exit(1, f"{error}\n")
        except Error as error:
            parser.exit(1, f"Falló la migración de PostgreSQL ({error.sqlstate or 'conexión'}).\n")
