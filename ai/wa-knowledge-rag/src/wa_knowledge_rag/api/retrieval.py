"""Frontera HTTP del contrato de retrieval v1."""

from typing import Annotated

import structlog
from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from .deps import Response, RetrievalRequest, RetrievalService, get_service, request_context

router = APIRouter()


def error_response(code: str, message: str, correlation_id: str, status: int) -> JSONResponse:
    structlog.get_logger().warning("retrieval_failed", code=code, correlationId=correlation_id)
    return JSONResponse(
        status_code=status, content={"error": {"code": code, "message": message}, "correlationId": correlation_id}
    )


@router.post("/retrieval", response_model=Response)
def retrieve(
    request: Annotated[RetrievalRequest, Depends(request_context)],
    service: Annotated[RetrievalService, Depends(get_service)],
) -> Response | JSONResponse:
    try:
        response = service.retrieve(request)
        structlog.get_logger().info(
            "retrieval_completed", correlationId=request.correlationId, confidence=response.confidence
        )
        return response
    except Exception:
        # La excepción puede incluir consultas, credenciales o datos del proveedor.
        return error_response(
            "SERVICIO_NO_DISPONIBLE",
            "El servicio de conocimiento no está disponible. Intentá más tarde.",
            request.correlationId,
            503,
        )
