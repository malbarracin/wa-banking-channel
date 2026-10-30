"""Emisor JSON aislado de contextvars, excepciones y configuración global."""

import math
import re
from datetime import UTC, datetime

import structlog

from wa_mcp_layer.envelope import ContextoSesion
from wa_mcp_layer.security.pii import mask_phone

_logger = structlog.wrap_logger(
    structlog.PrintLogger(),
    processors=[structlog.processors.JSONRenderer()],
    wrapper_class=structlog.BoundLogger,
    context_class=dict,
    cache_logger_on_first_use=True,
)


def log_invocation(
    *,
    context: ContextoSesion,
    capacidad: str,
    version: int,
    servicio_origen: str,
    resultado: str,
    duracion_ms: float,
) -> None:
    """Recibe metadatos del catálogo, nunca parámetros de la operación ni mensajes de excepción."""
    for value in (capacidad, servicio_origen, resultado):
        if re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,127}", value) is None:
            raise ValueError("Metadatos de auditoría inválidos")
    if version < 1 or not math.isfinite(duracion_ms) or duracion_ms < 0:
        raise ValueError("Medidas de auditoría inválidas")
    _logger.info(
        "invocacion_capacidad",
        timestamp=datetime.now(UTC).isoformat(),
        correlation_id=context.correlation_id,
        capacidad=capacidad,
        version=version,
        servicio_origen=servicio_origen,
        actor=mask_phone(context.numero_whatsapp),
        resultado=resultado,
        duracion_ms=duracion_ms,
    )
