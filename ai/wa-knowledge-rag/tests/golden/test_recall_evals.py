"""Golden-set recall with the real embedding model (requires GOOGLE_API_KEY)."""

from __future__ import annotations

import os
import statistics
import time
from pathlib import Path

import pytest
from pydantic import SecretStr

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embeddings import GoogleEmbedder
from wa_knowledge_rag.retrieval.service import retrieve

from . import allowed_levels, load_cases, recall_at_k
from .corpus import publish_minimum_corpus

pytestmark = [pytest.mark.integration, pytest.mark.evals]


@pytest.fixture
def real_corpus(
    migrated: Settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> tuple[Settings, GoogleEmbedder]:
    api_key = os.environ.get("GOOGLE_API_KEY", "").strip()
    if not api_key:
        pytest.skip("GOOGLE_API_KEY ausente: correr con la clave para el gate del modelo real")
    monkeypatch.setenv("GOOGLE_API_KEY", api_key)
    settings = Settings(
        database_url=migrated.database_url,
        google_api_key=SecretStr(api_key),
        environment="test",
    )
    # Publish with the real embedder so the index matches query embeddings.
    embedder = GoogleEmbedder(settings)
    monkeypatch.setattr(
        "wa_knowledge_rag.cli.GoogleEmbedder",
        lambda _settings: embedder,
    )
    publish_minimum_corpus(migrated, tmp_path, monkeypatch, capsys, embedder=embedder)
    return settings, embedder


def test_real_model_recall_r1_r2_ambiguous_and_latency(real_corpus: tuple[Settings, GoogleEmbedder]) -> None:
    settings, embedder = real_corpus
    cases = load_cases()
    ranked_hits: list[bool] = []
    latencies_ms: list[float] = []

    try:
        for case in cases:
            started = time.perf_counter()
            response = retrieve(settings, embedder, case["query"], correlation_id=f"eval-{case['id']}", top_k=5)
            latencies_ms.append((time.perf_counter() - started) * 1000)

            level = response["confidence"]["level"]
            suggestion = response["confidence"]["suggestion"]
            piece_ids = [item["citation"]["pieceId"] for item in response["results"]]

            if case.get("expected_piece"):
                ranked_hits.append(case["expected_piece"] in piece_ids)

            assert level in allowed_levels(case)
            if "expected_suggestion" in case:
                assert suggestion == case["expected_suggestion"]

            if case["id"] == "R1":
                assert piece_ids[:1] == ["cuentas-horario-transferencias"]
                assert level == "alta"
                assert suggestion == "responder"

            if case.get("expect_no_invented_text"):
                assert suggestion == "derivar"
                if level == "sin_respuesta":
                    assert response["results"] == []
                else:
                    assert all(item.get("citation") for item in response["results"])

            if case.get("expected_suggestion") == "aclarar":
                assert level == "baja"
                assert len(response["results"]) >= 2
    finally:
        embedder.close()

    assert recall_at_k(ranked_hits, k=5) >= 0.8
    p95 = statistics.quantiles(latencies_ms, n=20)[18] if len(latencies_ms) >= 2 else latencies_ms[0]
    print(f"golden_real_model_p95_ms={p95:.1f}")
    assert p95 <= 800, f"latencia p95 {p95:.1f} ms supera el objetivo de 800 ms"
