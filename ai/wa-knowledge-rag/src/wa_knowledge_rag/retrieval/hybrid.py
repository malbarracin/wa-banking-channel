"""Rank eligible chunks in a single snapshot of the active release."""

from collections.abc import Sequence

from pgvector import Vector
from psycopg import Connection
from psycopg.rows import DictRow

_SEARCH = """
WITH active AS (
    SELECT release_no FROM kb_release WHERE is_active
),
eligible AS NOT MATERIALIZED (
    SELECT c.id, c.embedding, c.tsv
    FROM active
    JOIN kb_release_item i USING (release_no)
    JOIN kb_chunk c USING (piece_id, version, rendered_hash)
    JOIN kb_piece_version p USING (piece_id, version)
    WHERE c.embedding_model = %(model)s
      AND (NOT %(real)s OR i.approval_kind = 'real')
      AND p.status IN ('aprobada', 'publicada')
      AND p.valid_from <= now() AND (p.valid_to IS NULL OR p.valid_to > now())
      AND c.audience = p.audience
      AND (%(internal)s OR p.audience = 'publico')
      AND (%(families)s::text[] IS NULL OR p.family = ANY(%(families)s))
      AND (%(types)s::text[] IS NULL OR p.type = ANY(%(types)s))
      AND vector_norm(c.embedding) > 0
      AND EXISTS (
          SELECT 1 FROM kb_approval a
          WHERE a.piece_id = p.piece_id AND a.version = p.version AND a.subject_kind = 'piece'
      )
),
nearest AS (
    SELECT id, embedding <=> %(vector)s AS distance
    FROM eligible
    ORDER BY distance
    LIMIT 20
),
vector_rank AS (
    SELECT id, row_number() OVER (ORDER BY distance, id) AS rank FROM nearest
),
lexical_matches AS (
    SELECT id, ts_rank_cd(tsv, query) AS relevance
    FROM eligible, websearch_to_tsquery('spanish', %(query)s) AS query
    WHERE tsv @@ query
    ORDER BY relevance DESC, id
    LIMIT 20
),
lexical_rank AS (
    SELECT id, row_number() OVER (ORDER BY relevance DESC, id) AS rank FROM lexical_matches
),
fused AS (
    SELECT id,
           coalesce(1.0 / (60 + v.rank), 0) + coalesce(1.0 / (60 + l.rank), 0) AS rrf,
           l.rank IS NOT NULL AS lexical
    FROM vector_rank v FULL JOIN lexical_rank l USING (id)
),
best_per_piece AS (
    SELECT DISTINCT ON (c.piece_id) c.id, f.rrf,
           least(1.0, greatest(-1.0, 1 - (c.embedding <=> %(vector)s))
                      + CASE WHEN f.lexical THEN %(bonus)s ELSE 0 END) AS score
    FROM fused f JOIN kb_chunk c USING (id)
    ORDER BY c.piece_id, f.rrf DESC, score DESC, c.id
),
top_pieces AS (
    SELECT id, rrf, score FROM best_per_piece ORDER BY rrf DESC, score DESC, id LIMIT %(top_k)s
)
SELECT active.release_no, c.piece_id, p.title, p.type, p.family, p.audience,
       c.version, c.section, p.owner_area, c.text, top.score,
       coalesce(i.approved_at, approval.approved_at) AS approved_at,
       coalesce(i.approval_kind = 'prueba', approval.is_test) AS is_test
FROM active
LEFT JOIN top_pieces top ON true
LEFT JOIN kb_chunk c ON c.id = top.id
LEFT JOIN kb_piece_version p USING (piece_id, version)
LEFT JOIN kb_release_item i ON (i.release_no, i.piece_id) = (active.release_no, c.piece_id)
LEFT JOIN LATERAL (
    SELECT max(a.approved_at) AS approved_at, bool_or(a.is_test) AS is_test
    FROM kb_approval a
    WHERE a.piece_id = c.piece_id AND a.version = c.version AND a.subject_kind = 'piece'
) approval ON true
ORDER BY top.rrf DESC, top.score DESC, top.id
"""


def search(
    connection: Connection[DictRow],
    *,
    query: str,
    embedding: list[float],
    model: str,
    internal: bool,
    families: Sequence[str] | None,
    types: Sequence[str] | None,
    lexical_bonus: float,
    top_k: int,
    real: bool = False,
) -> list[DictRow]:
    """Return ranked pieces, or a release-only row when no chunk matches."""
    # HNSW applies release and metadata filters after scanning the index.
    connection.execute("SET LOCAL hnsw.iterative_scan = strict_order")
    return connection.execute(
        _SEARCH,
        {
            "query": query,
            "vector": Vector(embedding),
            "model": model,
            "real": real,
            "internal": internal,
            "families": list(families) if families is not None else None,
            "types": list(types) if types is not None else None,
            "bonus": lexical_bonus,
            "top_k": top_k,
        },
    ).fetchall()
