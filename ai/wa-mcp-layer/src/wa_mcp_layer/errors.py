"""Taxonomía estable: nunca se aceptan textos ni cuerpos del servicio origen."""

from wa_mcp_layer.envelope import ErrorNegocio
from wa_mcp_layer.security.session_context import REVERIFY


def map_error(status: int | None) -> ErrorNegocio:
    """None representa timeout o ausencia de respuesta del servicio."""
    codes: dict[int | None, tuple[str, str]] = {
        400: ("SOLICITUD_INVALIDA", "Revisá los datos de la solicitud e intentá nuevamente."),
        401: ("CREDENCIAL_INVALIDA", REVERIFY),
        403: ("VINCULO_INACTIVO", REVERIFY),
        404: ("NO_ENCONTRADO", "No encontramos ese dato."),
        409: ("CONFLICTO", "No podemos completar la operación en su estado actual. Consultá a soporte."),
        423: ("CONFLICTO", "No podemos completar la operación en su estado actual. Consultá a soporte."),
    }
    code, message = codes.get(
        status,
        ("SERVICIO_NO_DISPONIBLE", "No podemos atender la solicitud ahora. Intentá más tarde o contactá a soporte."),
    )
    return ErrorNegocio(codigo=code, mensaje=message, reintentable=code == "SERVICIO_NO_DISPONIBLE")
