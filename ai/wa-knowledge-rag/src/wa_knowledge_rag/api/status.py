"""Health probes and release metrics backed by PostgreSQL."""

import psycopg
from prometheus_client import CollectorRegistry, Counter, Gauge, Histogram, generate_latest
from psycopg.rows import dict_row

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.content import Audience, Family
from wa_knowledge_rag.store.database import database_url, transaction
from wa_knowledge_rag.store.indexes import indexes_ready


def health(settings: Settings) -> tuple[int, dict[str, str | None]]:
    body: dict[str, str | None] = {
        "status": "degraded",
        "database": "unavailable",
        "index": "unavailable",
        "knowledgeVersion": None,
    }
    if settings.database_url is None:
        return 503, body
    try:
        with psycopg.connect(database_url(settings), connect_timeout=2, row_factory=dict_row) as connection:
            body["database"] = "ok"
            connection.execute("SET LOCAL statement_timeout = '2s'")
            vector = connection.execute("SELECT 1 FROM pg_extension WHERE extname = 'vector'").fetchone()
            if vector is None or not indexes_ready(connection):
                return 503, body
            body["index"] = "ok"
            active = connection.execute("SELECT release_no FROM kb_release WHERE is_active").fetchone()
            if active is not None:
                body.update(status="ok", knowledgeVersion=f"kv-{active['release_no']}")
                return 200, body
    except (psycopg.Error, ValueError):
        pass
    return 503, body


class Metrics:
    def __init__(self) -> None:
        self.registry = CollectorRegistry()
        self.latency = Histogram("kb_retrieve_latency_seconds", "Latencia de consulta.", registry=self.registry)
        self.retrieved = Counter(
            "kb_retrieve_total", "Consultas respondidas por nivel de confianza.", ["level"], registry=self.registry
        )
        self.embedding_errors = Counter(
            "kb_embedding_errors_total", "Consultas con falla de embeddings.", registry=self.registry
        )
        for level in ("alta", "baja", "sin_respuesta"):
            self.retrieved.labels(level)

    def render(self, settings: Settings) -> bytes:
        # Scrape-time gauges use a fresh registry so concurrent scrapes cannot overwrite each other.
        snapshot = CollectorRegistry()
        active_release = Gauge("kb_active_release", "Número de la versión activa.", registry=snapshot)
        published = Gauge(
            "kb_published_pieces", "Piezas por familia y audiencia.", ["family", "audience"], registry=snapshot
        )
        for family in Family:
            for audience in Audience:
                published.labels(family, audience).set(0)
        if settings.database_url is not None:
            try:
                with transaction(settings, query=True) as connection:
                    rows = connection.execute(
                        """
                        SELECT r.release_no, p.family, p.audience, count(p.piece_id) AS pieces
                        FROM kb_release r
                        LEFT JOIN kb_release_item i USING (release_no)
                        LEFT JOIN kb_piece_version p USING (piece_id, version)
                        WHERE r.is_active
                        GROUP BY r.release_no, p.family, p.audience
                        """
                    ).fetchall()
                for row in rows:
                    active_release.set(row["release_no"])
                    if row["family"] in tuple(Family) and row["audience"] in tuple(Audience):
                        published.labels(row["family"], row["audience"]).set(row["pieces"])
            except (psycopg.Error, ValueError):
                # Keep counters available and distinguish a failed scrape of release data from an empty release.
                active_release.set(float("nan"))
                for family in Family:
                    for audience in Audience:
                        published.labels(family, audience).set(float("nan"))
        return generate_latest(self.registry) + generate_latest(snapshot)
