"""La publicación pública requiere clasificación explícita del aprobador."""

from typing import Literal

from .validator import Source


def classify(source: Source) -> Literal["public", "internal"]:
    return source.audience
