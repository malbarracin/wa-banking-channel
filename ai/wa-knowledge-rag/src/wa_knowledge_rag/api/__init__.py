"""Aplicación H8: uvicorn wa_knowledge_rag.api:app."""

import re
from uuid import uuid4

import structlog
import uvicorn
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from wa_knowledge_rag.config import Settings

from .health import router as health_router
from .retrieval import error_response
from .retrieval import router as retrieval_router


def create_app() -> FastAPI:
    structlog.configure(processors=[structlog.contextvars.merge_contextvars, structlog.processors.JSONRenderer()])
    application = FastAPI(title="wa-knowledge-rag", version="1.0.0")
    application.include_router(health_router)
    application.include_router(retrieval_router)

    @application.exception_handler(RequestValidationError)
    async def invalid_request(request: Request, exc: RequestValidationError) -> JSONResponse:
        body = exc.body
        correlation = body.get("correlationId") if isinstance(body, dict) else None
        if not isinstance(correlation, str) or re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", correlation) is None:
            correlation = str(uuid4())
        return error_response("CONSULTA_INVALIDA", "Revisá los datos de la consulta.", correlation, 400)

    return application


app = create_app()


def main() -> None:
    settings = Settings()
    uvicorn.run(app, host="0.0.0.0", port=settings.port, log_level=settings.log_level.lower())  # noqa: S104 — API del contenedor.
