"""Allowed lifecycle transitions for a version of a knowledge piece."""

from enum import StrEnum


class Status(StrEnum):
    PROPOSAL = "propuesta"
    DRAFT = "borrador"
    IN_REVIEW = "en_revision"
    APPROVED = "aprobada"
    PUBLISHED = "publicada"
    RETIRED = "retirada"


_TRANSITIONS = {
    Status.PROPOSAL: {Status.DRAFT, Status.IN_REVIEW},
    Status.DRAFT: {Status.IN_REVIEW},
    Status.IN_REVIEW: {Status.APPROVED},
    Status.APPROVED: {Status.PUBLISHED},
    Status.PUBLISHED: {Status.RETIRED},
}


def require_transition(current: Status, target: Status) -> None:
    if target not in _TRANSITIONS.get(current, set()):
        raise ValueError(f"Transición inválida: {current} → {target}")
