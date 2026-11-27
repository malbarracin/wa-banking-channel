"""Public error responses for retrieval v1."""

from typing import Literal

from fastapi.responses import JSONResponse

from wa_knowledge_rag.api.schemas import ContractModel

ErrorCode = Literal[
    "VALIDATION_ERROR", "UNAUTHENTICATED", "AUDIENCE_FORBIDDEN", "KNOWLEDGE_UNAVAILABLE", "INTERNAL_ERROR"
]


class ErrorResponse(ContractModel):
    code: ErrorCode
    message: str
    correlationId: str


_ERRORS: dict[ErrorCode, tuple[int, str]] = {
    "VALIDATION_ERROR": (400, "La petición no es válida."),
    "UNAUTHENTICATED": (401, "Se requiere una credencial de servicio válida."),
    "AUDIENCE_FORBIDDEN": (403, "El consumidor no tiene permiso para consultar contenido interno."),
    "KNOWLEDGE_UNAVAILABLE": (503, "La base de conocimiento no está disponible."),
    "INTERNAL_ERROR": (500, "Ocurrió un error interno."),
}


class ApiError(Exception):
    def __init__(self, code: ErrorCode) -> None:
        self.code = code
        super().__init__(code)


def error_response(code: ErrorCode, correlation_id: str) -> JSONResponse:
    status, message = _ERRORS[code]
    headers = {"WWW-Authenticate": "Bearer"} if status == 401 else {}
    body = ErrorResponse(code=code, message=message, correlationId=correlation_id)
    return JSONResponse(body.model_dump(), status_code=status, headers=headers)
