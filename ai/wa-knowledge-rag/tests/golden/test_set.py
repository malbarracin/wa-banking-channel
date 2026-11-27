"""Structural checks for the golden set and threshold documentation."""

from __future__ import annotations

import re
from pathlib import Path

from wa_knowledge_rag.config import Settings

from . import FAMILIES, load_cases, validate_set_structure

ROOT = Path(__file__).resolve().parents[2]
ARCHITECTURE = ROOT.parents[1] / "specs" / "wa-knowledge-rag" / "arquitectura.md"


def test_golden_set_covers_required_scenarios() -> None:
    cases = load_cases()
    summary = validate_set_structure(cases)
    assert summary["has_r1"]
    assert summary["has_r2"]
    assert summary["has_ambiguous"]
    assert summary["covers_families"]
    assert summary["has_pii"]
    assert summary["families"] == FAMILIES

    r1 = next(case for case in cases if case["id"] == "R1")
    assert r1["query"] == "¿Cuál es el horario para transferir?"
    assert r1["expected_piece"] == "cuentas-horario-transferencias"
    assert r1["expected_level"] == "alta"


def test_thresholds_match_config_env_and_architecture() -> None:
    settings = Settings()
    env_file = ROOT / ".env.example"
    values = dict(line.split("=", 1) for line in env_file.read_text(encoding="utf-8").splitlines())
    architecture = ARCHITECTURE.read_text(encoding="utf-8")

    expected = {
        "KB_CONFIDENCE_THRESHOLD": settings.confidence_threshold,
        "KB_MIN_RELEVANCE": settings.min_relevance,
        "KB_AMBIGUITY_MARGIN": settings.ambiguity_margin,
        "KB_LEXICAL_BONUS": settings.lexical_bonus,
    }
    for name, value in expected.items():
        assert float(values[name]) == value
        documented = re.search(
            rf"{name}`?\s*[=(]\s*`?{re.escape(f'{value:.2f}')}",
            architecture,
        )
        assert documented, f"{name}={value:.2f} no aparece en arquitectura §5/§9"
