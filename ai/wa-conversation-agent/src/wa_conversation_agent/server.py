"""Frontera HTTP de H9; el Runner se integrará en una tarea posterior."""

from secrets import compare_digest
from typing import Annotated

import uvicorn
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from wa_conversation_agent.config import Settings
from wa_conversation_agent.models import Reply, Text, TurnRequest, TurnResponse

bearer = HTTPBearer(auto_error=False)


class TurnUnavailable(Exception):
    """El procesador no puede atender el turno temporalmente."""


def process_turn(payload: TurnRequest) -> TurnResponse:
    return TurnResponse(
        correlationId=payload.correlationId,
        conversationId=payload.conversationId,
        messageId=payload.messageId,
        reply=Reply(body="Recibimos tu mensaje."),
        escalate=False,
        toolsUsed=[],
    )


def create_app(settings: Settings | None = None) -> FastAPI:
    config = settings if settings is not None else Settings()
    app = FastAPI(title="H9 — Agente conversacional")

    async def authorize(
        credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    ) -> None:
        expected = config.service_token.get_secret_value()
        if (
            not expected
            or credentials is None
            or not compare_digest(credentials.credentials.encode(), expected.encode())
        ):
            raise HTTPException(status_code=401, detail="No autorizado", headers={"WWW-Authenticate": "Bearer"})

    @app.exception_handler(RequestValidationError)
    async def invalid_request(request: Request, error: RequestValidationError) -> JSONResponse:
        # No devolver errores de Pydantic: pueden contener PII o credenciales del contexto.
        return JSONResponse(status_code=400, content={"detail": "Revisá los datos del mensaje para continuar."})

    @app.exception_handler(TurnUnavailable)
    async def unavailable(request: Request, error: TurnUnavailable) -> JSONResponse:
        return JSONResponse(status_code=503, content={"detail": "No podemos atender tu mensaje en este momento."})

    @app.post("/api/v1/agent/turn", response_model=TurnResponse, dependencies=[Depends(authorize)])
    async def turn(
        payload: TurnRequest,
        correlation: Annotated[Text, Header(alias="X-Correlation-Id")],
        conversation: Annotated[Text, Header(alias="X-Conversation-Id")],
        message: Annotated[Text, Header(alias="X-Message-Id")],
        idempotency: Annotated[Text, Header(alias="X-Idempotency-Key")],
    ) -> TurnResponse:
        return process_turn(payload)

    return app


app = create_app()


def main() -> None:
    uvicorn.run(app, host="0.0.0.0", port=Settings().port)  # noqa: S104 — servicio dentro del contenedor.


if __name__ == "__main__":
    main()
