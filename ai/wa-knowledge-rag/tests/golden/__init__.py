"""Golden-set helpers for recall@5 and confidence calibration."""

from __future__ import annotations

from pathlib import Path
from typing import Any, TypedDict

import yaml

GOLDEN_DIR = Path(__file__).resolve().parent
CASES_PATH = GOLDEN_DIR / "cases.yaml"
CONTENT_DIR = GOLDEN_DIR.parents[1] / "content"
FAMILIES = frozenset({"cuentas", "tarjetas", "seguros", "canal"})


class GoldenCase(TypedDict, total=False):
    id: str
    query: str
    family: str
    expected_piece: str
    expected_level: str
    expected_levels: list[str]
    expected_suggestion: str
    expected_candidates: list[str]
    expect_no_invented_text: bool
    contains_synthetic_pii: bool


def load_cases(path: Path = CASES_PATH) -> list[GoldenCase]:
    payload = yaml.safe_load(path.read_text(encoding="utf-8"))
    cases = payload["cases"]
    if not isinstance(cases, list) or not cases:
        raise ValueError("El set dorado debe listar al menos un caso")
    return cases


def allowed_levels(case: GoldenCase) -> set[str]:
    if "expected_levels" in case:
        return set(case["expected_levels"])
    return {case["expected_level"]}


def expected_pieces(cases: list[GoldenCase]) -> list[GoldenCase]:
    return [case for case in cases if case.get("expected_piece")]


def recall_at_k(hits: list[bool], *, k: int = 5) -> float:
    if not hits:
        return 0.0
    return sum(hits) / len(hits)


def validate_set_structure(cases: list[GoldenCase]) -> dict[str, Any]:
    ids = [case["id"] for case in cases]
    families = {case["family"] for case in cases if "family" in case}
    ambiguous = [
        case for case in cases if case.get("expected_suggestion") == "aclarar" or "expected_candidates" in case
    ]
    r2 = [case for case in cases if case["id"] == "R2" or case.get("expect_no_invented_text")]
    pii = [case for case in cases if case.get("contains_synthetic_pii")]
    return {
        "has_r1": "R1" in ids,
        "has_r2": bool(r2),
        "has_ambiguous": bool(ambiguous),
        "families": families,
        "covers_families": FAMILIES <= families,
        "has_pii": bool(pii),
        "count": len(cases),
    }
