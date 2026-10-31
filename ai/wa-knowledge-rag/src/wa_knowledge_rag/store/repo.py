"""KNN y texto español con filtros aplicados antes del ranking y fusión RRF."""

import json
import math
from collections.abc import Sequence

import psycopg
from psycopg.rows import DictRow

from wa_knowledge_rag.store.models import Audience, SearchHit

# La versión solicitada restringe la vigente; nunca permite recuperar una retirada.
HYBRID_SQL = """
WITH eligible AS NOT MATERIALIZED (
    SELECT c.*, s.title, s.source_uri, s.source_id AS logical_source_id
    FROM kb_chunk c JOIN kb_source s ON s.id = c.source_id
    WHERE s.status = 'approved' AND s.audience = %(audience)s
      AND c.version = s.version
      AND (%(version)s::integer IS NULL OR s.version = %(version)s)
      AND NOT EXISTS (
          SELECT 1 FROM kb_source newer
          WHERE newer.source_id = s.source_id AND newer.status = 'approved' AND newer.version > s.version
      )
), vector_candidates AS (
    SELECT id, embedding <=> %(embedding)s::vector AS distance
    FROM eligible WHERE embedding IS NOT NULL
    ORDER BY embedding <=> %(embedding)s::vector, id LIMIT %(candidates)s
), vector_rank AS (
    SELECT id, row_number() OVER (ORDER BY distance, id) AS rank FROM vector_candidates
), text_candidates AS (
    SELECT id, ts_rank(tsv, plainto_tsquery('spanish', %(query)s)) AS relevance
    FROM eligible WHERE tsv @@ plainto_tsquery('spanish', %(query)s)
    ORDER BY relevance DESC, id LIMIT %(candidates)s
), text_rank AS (
    SELECT id, row_number() OVER (ORDER BY relevance DESC, id) AS rank FROM text_candidates
), fused AS (
    SELECT COALESCE(v.id, t.id) AS id,
           COALESCE(1.0 / (60 + v.rank), 0) + COALESCE(1.0 / (60 + t.rank), 0) AS score
    FROM vector_rank v FULL JOIN text_rank t ON v.id = t.id
)
SELECT e.id, e.logical_source_id AS source_id,
       e.title, e.version, e.content, e.section, e.source_uri, f.score::float8
FROM fused f JOIN eligible e ON e.id = f.id
ORDER BY f.score DESC, e.id LIMIT %(top_k)s
"""


class KnowledgeRepository:
    def __init__(self, connection: psycopg.Connection[DictRow]) -> None:
        self.connection = connection

    def search(
        self, query: str, embedding: Sequence[float], *, audience: Audience, version: int | None = None, top_k: int = 5
    ) -> list[SearchHit]:
        """Devuelve candidatos aprobados, de una sola audiencia y versión vigente."""
        if audience not in ("public", "internal"):
            raise ValueError("Audiencia inválida")
        if not query.strip() or len(query) > 10000:
            raise ValueError("Consulta inválida")
        if not 1 <= top_k <= 100 or (version is not None and version < 1):
            raise ValueError("Límites inválidos")
        if len(embedding) != 768 or not all(math.isfinite(value) for value in embedding):
            raise ValueError("Embedding inválido")
        if not any(embedding):
            raise ValueError("El vector coseno no puede ser cero")
        rows = self.connection.execute(
            HYBRID_SQL,
            {
                "query": query,
                "embedding": json.dumps(list(embedding)),
                "audience": audience,
                "version": version,
                "top_k": top_k,
                "candidates": max(20, top_k * 4),
            },
        ).fetchall()
        return [SearchHit.model_validate(row) for row in rows]
