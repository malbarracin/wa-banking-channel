"""Knowledge base command-line entry point."""

import sys
from argparse import ArgumentParser, Namespace
from pathlib import Path

from psycopg import Error

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.content import Family
from wa_knowledge_rag.coverage import check_coverage
from wa_knowledge_rag.store.database import transaction
from wa_knowledge_rag.store.migrate import migrate
from wa_knowledge_rag.workflow.approvals import approve, submit
from wa_knowledge_rag.workflow.ingest import CONTENT, ingest_parameters, ingest_piece, inventory
from wa_knowledge_rag.workflow.states import Status


def _approval_arguments(parser: ArgumentParser, subject: str) -> None:
    parser.add_argument(subject, metavar=subject)
    parser.add_argument("--role", required=True)
    parser.add_argument("--test", action="store_true")


def _parser() -> ArgumentParser:
    parser = ArgumentParser(prog="kb", description="Base de conocimiento del canal WhatsApp")
    commands = parser.add_subparsers(dest="command")
    database = commands.add_parser("db", help="Administrar el esquema de la base")
    database.add_subparsers(required=True).add_parser("migrate", help="Aplicar migraciones pendientes")
    ingest = commands.add_parser("ingest", help="Registrar piezas nuevas o modificadas")
    ingest.add_argument("path", nargs="?", type=Path, default=CONTENT)
    ingest.add_argument("--proposal", action="store_true")
    commands.add_parser("submit", help="Enviar una pieza a revisión").add_argument("subject", metavar="id")
    _approval_arguments(commands.add_parser("approve", help="Aprobar una pieza"), "id")

    params = commands.add_parser("params", help="Gestionar parámetros del banco")
    parameter_commands = params.add_subparsers(dest="parameter_command", required=True)
    parameter_commands.add_parser("ingest", help="Registrar valores nuevos o modificados para revisión")
    _approval_arguments(parameter_commands.add_parser("approve", help="Aprobar un parámetro"), "name")

    listing = commands.add_parser("list", help="Listar las versiones de las piezas")
    listing.add_argument("--family", choices=list(Family))
    listing.add_argument("--status", choices=list(Status))
    coverage = commands.add_parser("coverage", help="Comprobar la cobertura de las preguntas frecuentes mínimas")
    coverage.add_argument("--published", action="store_true", help="Comprobar la versión activa en PostgreSQL")
    return parser


def _ingest(args: Namespace, settings: Settings) -> list[str]:
    path: Path = args.path
    if path.is_dir():
        paths = sorted(path.rglob("*.md"))
    elif path == CONTENT and not path.exists():
        paths = []
    else:
        paths = [path]

    errors = []
    for source in paths:
        with transaction(settings) as connection:
            warnings, error = ingest_piece(
                connection, source, proposal=args.proposal, max_chars=settings.max_chars_whatsapp
            )
        for warning in warnings:
            print(warning, file=sys.stderr)
        if error:
            errors.append(error)
    return errors


def _run(args: Namespace, settings: Settings) -> list[str]:
    if args.command == "db":
        migrate(settings)
        return []
    if args.command == "ingest":
        return _ingest(args, settings)
    if args.command == "coverage":
        errors = check_coverage(CONTENT, settings, published=args.published)
        if not errors:
            scope = "publicación activa" if args.published else "archivos fuente"
            print(f"Cobertura completa en {scope}.")
        return errors

    parameter_approval = args.command == "params" and args.parameter_command == "approve"
    with transaction(settings) as connection:
        if args.command == "list":
            for row in inventory(connection, family=args.family, status=args.status):
                print(row["piece_id"], row["version"], row["family"], row["status"], sep="\t")
            return []
        if args.command == "submit":
            error = submit(connection, args.subject)
        elif args.command == "approve" or parameter_approval:
            error = approve(
                connection,
                "parameter" if parameter_approval else "piece",
                args.name if parameter_approval else args.id,
                role=args.role,
                is_test=args.test,
                real=settings.environment == "real",
                policy_path=settings.approval_policy_path,
            )
        else:
            return ingest_parameters(connection)
    return [error] if error else []


def main() -> None:
    parser = _parser()
    args = parser.parse_args()
    if args.command is None:
        parser.print_help()
        return
    try:
        errors = _run(args, Settings())
    except (ValueError, RuntimeError) as error:
        parser.exit(1, f"{error}\n")
    except Error as error:
        parser.exit(1, f"Falló la operación de PostgreSQL ({error.sqlstate or 'conexión'}).\n")
    if errors:
        parser.exit(1, "\n".join(errors) + "\n")
