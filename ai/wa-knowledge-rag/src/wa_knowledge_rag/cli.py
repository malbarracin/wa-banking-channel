"""Knowledge base command-line entry point."""

import re
import sys
from argparse import ArgumentParser, Namespace
from contextlib import ExitStack
from functools import cache
from pathlib import Path
from time import sleep

from psycopg import Error

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.content import Family
from wa_knowledge_rag.ingestion.embeddings import GoogleEmbedder
from wa_knowledge_rag.store.database import transaction
from wa_knowledge_rag.store.migrate import migrate
from wa_knowledge_rag.store.repositories import audit
from wa_knowledge_rag.workflow.approvals import approve, submit
from wa_knowledge_rag.workflow.ingest import CONTENT, ingest_parameters, ingest_piece, inventory
from wa_knowledge_rag.workflow.publish import lock_releases, publish, restore_or_retire, verify_pii
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

    publishing = commands.add_parser("publish", help="Publicar contenido aprobado")
    selection = publishing.add_mutually_exclusive_group(required=True)
    selection.add_argument("subject", metavar="id", nargs="?")
    selection.add_argument("--all-approved", action="store_true")
    commands.add_parser("retire", help="Retirar una pieza publicada").add_argument("subject", metavar="id")
    rollback = commands.add_parser("rollback", help="Restaurar una publicación o versión anterior")
    target = rollback.add_mutually_exclusive_group()
    target.add_argument("subject", metavar="id", nargs="?")
    target.add_argument("--to", type=_release_number, metavar="kv-N")
    rollback.add_argument("--to-version", type=int, metavar="N")
    commands.add_parser("sync", help="Registrar cambios y actualizar la publicación").add_argument(
        "--loop", action="store_true"
    )
    commands.add_parser("verify-pii", help="Verificar datos personales en contenido publicado")

    listing = commands.add_parser("list", help="Listar las versiones de las piezas")
    listing.add_argument("--family", choices=list(Family))
    listing.add_argument("--status", choices=list(Status))
    return parser


def _release_number(value: str) -> int:
    if not re.fullmatch(r"kv-[1-9][0-9]{0,9}", value) or int(value[3:]) > 2_147_483_647:
        raise ValueError("La publicación debe tener formato kv-N")
    return int(value[3:])


def _sync_interval(value: str) -> int:
    match = re.fullmatch(r"([1-9][0-9]{0,8})([smh])", value)
    if match is None:
        raise ValueError("KB_SYNC_INTERVAL debe ser una duración positiva en s, m o h")
    return int(match[1]) * {"s": 1, "m": 60, "h": 3600}[match[2]]


def _release_command(args: Namespace, settings: Settings) -> list[str]:
    with ExitStack() as resources, transaction(settings) as connection:

        @cache
        def embedder() -> GoogleEmbedder:
            provider = GoogleEmbedder(settings)
            resources.callback(provider.close)
            return provider

        if args.command == "verify-pii":
            error = verify_pii(connection)
            return [error] if error else []
        if args.command == "sync":
            lock_releases(connection)
            errors = []
            for path in sorted(CONTENT.rglob("*.md")):
                warnings, error = ingest_piece(
                    connection, path, proposal=False, max_chars=settings.max_chars_whatsapp, changed_only=True
                )
                for warning in warnings:
                    print(warning, file=sys.stderr)
                if error:
                    errors.append(error)
            if (CONTENT / "parameters" / "parametros.yaml").exists():
                errors.extend(ingest_parameters(connection, changed_only=True))
            if errors:
                audit.insert(connection, action="reject", subject="release", detail={"operation": "sync"})
                return errors
            number, error = publish(connection, settings, embedder, sync=True)
        elif args.command == "publish":
            number, error = publish(connection, settings, embedder, subject=args.subject)
        else:
            number, error = restore_or_retire(
                connection,
                settings,
                retire=args.command == "retire",
                subject=args.subject,
                target_release=getattr(args, "to", None),
                target_version=getattr(args, "to_version", None),
            )
    if error:
        return [error]
    if number is not None:
        print(f"kv-{number}")
    return []


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

    if args.command in {"publish", "retire", "rollback", "sync", "verify-pii"}:
        return _release_command(args, settings)

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


def _database_error(error: Error) -> str:
    return f"Falló la operación de PostgreSQL ({error.sqlstate or 'conexión'})."


def main() -> None:
    parser = _parser()
    args = parser.parse_args()
    if args.command is None:
        parser.print_help()
        return
    if args.command == "rollback" and (
        (args.subject is None) != (args.to_version is None)
        or (args.to_version is not None and not 0 < args.to_version <= 2_147_483_647)
    ):
        parser.error("El id de pieza requiere --to-version N con una versión positiva")
    try:
        settings = Settings()
        if args.command == "sync" and args.loop:
            interval = _sync_interval(settings.sync_interval)
            while True:
                try:
                    errors = _run(args, settings)
                except RuntimeError as error:
                    errors = [str(error)]
                except Error as error:
                    errors = [_database_error(error)]
                for message in errors:
                    print(message, file=sys.stderr)
                sleep(interval)
        errors = _run(args, settings)
    except KeyboardInterrupt:
        return
    except (ValueError, RuntimeError) as error:
        parser.exit(1, f"{error}\n")
    except Error as error:
        parser.exit(1, _database_error(error) + "\n")
    if errors:
        parser.exit(1, "\n".join(errors) + "\n")
