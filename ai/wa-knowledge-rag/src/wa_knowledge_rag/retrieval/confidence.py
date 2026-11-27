"""Confidence thresholds for distinct, ranked knowledge pieces."""

from collections.abc import Sequence
from math import isclose
from typing import Literal, TypedDict


class Confidence(TypedDict):
    level: Literal["alta", "baja", "sin_respuesta"]
    score: float
    suggestion: Literal["responder", "aclarar", "derivar"]


def assess_confidence(
    scores: Sequence[float], *, min_relevance: float, confidence_threshold: float, ambiguity_margin: float
) -> Confidence:
    ranked = sorted(scores, reverse=True)
    best = ranked[0] if ranked else 0.0
    if not ranked or best < min_relevance:
        return {"level": "sin_respuesta", "score": best, "suggestion": "derivar"}
    if best >= confidence_threshold:
        return {"level": "alta", "score": best, "suggestion": "responder"}
    if len(ranked) > 1 and ranked[1] >= min_relevance:
        gap = best - ranked[1]
        # Preserve the inclusive boundary despite floating-point subtraction.
        if gap <= ambiguity_margin or isclose(gap, ambiguity_margin, rel_tol=0, abs_tol=1e-15):
            return {"level": "baja", "score": best, "suggestion": "aclarar"}
    return {"level": "baja", "score": best, "suggestion": "derivar"}
