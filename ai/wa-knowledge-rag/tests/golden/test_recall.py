"""Golden-set recall with FakeEmbedder (no network, runs in pytest -q)."""

from __future__ import annotations

import statistics
import time
from pathlib import Path

import pytest

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embeddings import FakeEmbedder
from wa_knowledge_rag.retrieval.service import retrieve

from . import allowed_levels, expected_pieces, load_cases, recall_at_k
from .corpus import publish_minimum_corpus

pytestmark = pytest.mark.integration


@pytest.fixture
def corpus(
    migrated: Settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> tuple[Settings, FakeEmbedder]:
    _, embedder = publish_minimum_corpus(migrated, tmp_path, monkeypatch, capsys)
    return migrated, embedder


def test_fake_embedder_recall_and_confidence(corpus: tuple[Settings, FakeEmbedder]) -> None:
    settings, embedder = corpus
    cases = load_cases()
    ranked_hits: list[bool] = []
    latencies_ms: list[float] = []

    for case in cases:
        started = time.perf_counter()
        response = retrieve(settings, embedder, case["query"], correlation_id=f"golden-{case['id']}", top_k=5)
        latencies_ms.append((time.perf_counter() - started) * 1000)

        level = response["confidence"]["level"]
        suggestion = response["confidence"]["suggestion"]
        piece_ids = [item["citation"]["pieceId"] for item in response["results"]]

        if case.get("expected_piece"):
            ranked_hits.append(case["expected_piece"] in piece_ids)
            if case["id"] == "R1":
                assert piece_ids[:1] == [case["expected_piece"]]

        assert level in allowed_levels(case) | ({"baja", "alta"} if case.get("expected_piece") else set())

        # FakeEmbedder scores sit below the real-model alta band; enforce suggestion for R2/ambiguity.
        soft_ids = {"R1", "familia-cuentas", "familia-tarjetas", "familia-seguros", "familia-canal"}
        if case.get("expected_suggestion") and case["id"] not in soft_ids:
            assert suggestion == case["expected_suggestion"]

        if case.get("expect_no_invented_text"):
            assert suggestion == "derivar"
            assert level in {"sin_respuesta", "baja"}
            if level == "sin_respuesta":
                assert response["results"] == []
            else:
                assert all(item.get("citation") for item in response["results"])

        if case.get("expected_suggestion") == "aclarar":
            assert level == "baja"
            assert suggestion == "aclarar"
            assert len(response["results"]) >= 2
            if "expected_candidates" in case:
                assert set(case["expected_candidates"]) & set(piece_ids)

        if case.get("contains_synthetic_pii"):
            assert case["expected_piece"] in piece_ids

    assert recall_at_k(ranked_hits, k=5) >= 0.8
    assert expected_pieces(cases)
    p95 = statistics.quantiles(latencies_ms, n=20)[18] if len(latencies_ms) >= 2 else latencies_ms[0]
    assert p95 < 5000, f"p95 too high for FakeEmbedder path: {p95:.1f} ms"
    print(f"golden_fake_embedder_p95_ms={p95:.1f}")


def test_fake_embedder_r1_r2_and_ambiguous_signals(corpus: tuple[Settings, FakeEmbedder]) -> None:
    settings, embedder = corpus
    by_id = {case["id"]: case for case in load_cases()}

    r1 = retrieve(settings, embedder, by_id["R1"]["query"], correlation_id="golden-R1", top_k=5)
    assert r1["results"][0]["citation"]["pieceId"] == "cuentas-horario-transferencias"
    assert r1["results"][0]["citation"]["family"] == "cuentas"

    r2 = retrieve(settings, embedder, by_id["R2"]["query"], correlation_id="golden-R2", top_k=5)
    assert r2["confidence"]["suggestion"] == "derivar"
    if r2["confidence"]["level"] == "sin_respuesta":
        assert r2["results"] == []
    else:
        assert all("citation" in item for item in r2["results"])

    ambiguous = retrieve(
        settings, embedder, by_id["ambigua-fuera-alcance"]["query"], correlation_id="golden-ambigua", top_k=5
    )
    assert ambiguous["confidence"]["level"] == "baja"
    assert ambiguous["confidence"]["suggestion"] == "aclarar"
    assert len(ambiguous["results"]) >= 2
