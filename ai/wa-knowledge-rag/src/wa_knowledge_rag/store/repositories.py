"""Table-level persistence on a caller-owned transaction."""

from dataclasses import dataclass

from pgvector import Vector
from psycopg import Connection, sql
from psycopg.rows import DictRow
from psycopg.types.json import Jsonb


@dataclass(frozen=True)
class Repository:
    table: str
    json_columns: tuple[str, ...] = ()

    def insert(self, connection: Connection[DictRow], **fields: object) -> DictRow:
        """Insert one row, returning database defaults and generated values."""
        statement = sql.SQL("INSERT INTO {} ({}) VALUES ({}) RETURNING *").format(
            sql.Identifier(self.table),
            sql.SQL(", ").join(map(sql.Identifier, fields)),
            sql.SQL(", ").join(sql.Placeholder() for _ in fields),
        )
        values = [Jsonb(value) if name in self.json_columns else value for name, value in fields.items()]
        row = connection.execute(statement, values).fetchone()
        if row is None:
            raise RuntimeError(f"No se devolvió la fila insertada en {self.table}.")
        return row

    def find(self, connection: Connection[DictRow], **filters: object) -> list[DictRow]:
        """Read rows matching the supplied columns without imposing a result order."""
        statement = sql.SQL("SELECT * FROM {}").format(sql.Identifier(self.table))
        if filters:
            statement += sql.SQL(" WHERE ") + sql.SQL(" AND ").join(
                sql.SQL("{} IS NOT DISTINCT FROM {}").format(sql.Identifier(name), sql.Placeholder())
                if value is None
                else sql.SQL("{} = {}").format(sql.Identifier(name), sql.Placeholder())
                for name, value in filters.items()
            )
        return connection.execute(statement, tuple(filters.values())).fetchall()


class ChunkRepository(Repository):
    def insert(self, connection: Connection[DictRow], **fields: object) -> DictRow:
        embedding = fields["embedding"]
        if isinstance(embedding, (list, tuple)):
            fields["embedding"] = Vector(embedding)
        return super().insert(connection, **fields)


class VersionRepository(Repository):
    def set_piece_status(self, connection: Connection[DictRow], *, piece_id: str, version: int, status: str) -> bool:
        return (
            connection.execute(
                "UPDATE kb_piece_version SET status = %s WHERE piece_id = %s AND version = %s",
                (status, piece_id, version),
            ).rowcount
            == 1
        )


class ReleaseRepository(Repository):
    def set_active(self, connection: Connection[DictRow], *, release_no: int, is_active: bool) -> bool:
        return (
            connection.execute(
                "UPDATE kb_release SET is_active = %s WHERE release_no = %s", (is_active, release_no)
            ).rowcount
            == 1
        )


pieces = Repository("kb_piece")
piece_versions = VersionRepository("kb_piece_version", json_columns=("source_refs",))
approvals = Repository("kb_approval")
parameter_versions = Repository("kb_parameter_version")
releases = ReleaseRepository("kb_release")
release_items = Repository("kb_release_item", json_columns=("parameter_versions",))
chunks = ChunkRepository("kb_chunk")
audit = Repository("kb_audit", json_columns=("detail",))
query_log = Repository("kb_query_log", json_columns=("cited",))
