"""Structured logging with a correlation ID scoped to the current context."""

from collections.abc import Iterator
from contextlib import contextmanager

import structlog
from structlog.contextvars import bind_contextvars, get_contextvars, merge_contextvars, reset_contextvars

from wa_knowledge_rag.security.pii import mask_log_pii


@contextmanager
def correlation_context(correlation_id: str) -> Iterator[None]:
    tokens = bind_contextvars(correlationId=correlation_id)
    try:
        yield
    finally:
        reset_contextvars(**tokens)


def get_correlation_id() -> str | None:
    value = get_contextvars().get("correlationId")
    return value if isinstance(value, str) else None


def configure_logging() -> None:
    structlog.configure(
        processors=[
            merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso", utc=True),
            structlog.processors.format_exc_info,
            mask_log_pii,
            structlog.processors.JSONRenderer(),
        ],
    )
