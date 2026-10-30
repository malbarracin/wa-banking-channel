"""Nueva versión y retiro previo se publican en una única transacción PostgreSQL."""

from uuid import UUID, uuid4

import psycopg
import structlog
from psycopg.types.json import Jsonb

from .chunker import chunk
from .classifier import classify
from .embedder import Embedder
from .validator import IngestionError, Source, validate

logger = structlog.get_logger()


async def ingest(
    source: Source, embedder: Embedder, connection: psycopg.Connection[tuple[object, ...]], correlation_id: UUID
) -> int:
    validate(source)
    if embedder.settings.embedding_dimensions != 768:
        raise IngestionError("DATABASE_DIMENSION_MISMATCH")
    source = source.model_copy(update={"audience": classify(source)})
    chunks = chunk(source)
    vectors = await embedder.embed([item.content for item in chunks])
    # source_id es la identidad lógica; id es la identidad de esta revisión.
    revision_id = uuid4()
    with connection.transaction():
        with connection.cursor() as cursor:
            cursor.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s, 0))", (str(source.source_id),))
            cursor.execute("SELECT max(version) FROM kb_source WHERE source_id = %s", (source.source_id,))
            previous = cursor.fetchone()
            if previous and previous[0] is not None and source.version <= int(str(previous[0])):
                raise IngestionError("VERSION_MUST_INCREASE")
            cursor.execute(
                "UPDATE kb_source SET status = 'retired' WHERE source_id = %s AND status = 'approved'",
                (source.source_id,),
            )
            cursor.execute(
                "INSERT INTO kb_source "
                "(id, source_id, title, owner_area, audience, version, approved_by, approved_at, status) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,'approved')",
                (
                    revision_id,
                    source.source_id,
                    source.title,
                    source.owner_area,
                    source.audience,
                    source.version,
                    source.approved_by,
                    source.approved_at,
                ),
            )
            for item, vector in zip(chunks, vectors, strict=True):
                cursor.execute(
                    "INSERT INTO kb_chunk "
                    "(id, source_id, version, ordinal, content, section, metadata, embedding, tsv) "
                    "VALUES (%s,%s,%s,%s,%s,%s,%s,%s::vector,to_tsvector('spanish',%s))",
                    (
                        uuid4(),
                        revision_id,
                        source.version,
                        item.ordinal,
                        item.content,
                        item.section,
                        Jsonb({"source_id": str(source.source_id), "title": item.title, "audience": item.audience}),
                        str(vector),
                        item.content,
                    ),
                )
    # No se loguean título, texto, aprobador ni errores del proveedor/DB.
    logger.info(
        "knowledge_published",
        correlationId=str(correlation_id),
        revision_id=str(revision_id),
        version=source.version,
        chunks=len(chunks),
    )
    return len(chunks)
