"""Confianza sobre RRF normalizado, no una probabilidad de verdad."""

import math
from typing import Literal

from wa_knowledge_rag.config import Settings

Confidence = Literal["high", "medium", "low"]
RRF_K = 60
RRF_MAX = 2 / (RRF_K + 1)


def normalized_score(raw_score: float) -> float:
    """Dos listas con rango inicial 1: el máximo teórico es 2/61."""
    if not math.isfinite(raw_score) or raw_score < 0 or raw_score > RRF_MAX + 1e-12:
        raise ValueError("Puntaje RRF inválido")
    return min(1.0, raw_score / RRF_MAX)


def confidence(score: float, settings: Settings) -> Confidence:
    """Sin ambos umbrales calibrados se cierra de forma segura (low)."""
    high = settings.rag_confidence_high_min
    medium = settings.rag_confidence_medium_min
    if not math.isfinite(score) or not 0 < score <= 1 or high is None or medium is None:
        return "low"
    if score >= high:
        return "high"
    if score >= medium:
        return "medium"
    return "low"
