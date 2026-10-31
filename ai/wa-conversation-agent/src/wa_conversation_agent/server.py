"""Frontera HTTP de H9: contrato de turno con Runner e idempotencia."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from secrets import compare_digest
from typing import Annotated

import uvicorn
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from wa_conversation_agent.config import Settings
from wa_conversation_agent.models import Text, TurnRequest, TurnResponse
from wa_conversation_agent.turn import ConflictError, TurnProcessor

bearer = HTTPBearer(auto_error=False)


class TurnUnavailable(Exception):
    """El procesador no puede atender el turno temporalmente."""


def create_app(
    settings: Settings | None = None,
    processor: TurnProcessor | None = None,
) -> FastAPI:
    config = settings if settings is not None else Settings()
    turn_processor = processor if processor is not None else TurnProcessor.from_settings(config)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        try:
            yield
        finally:
            await turn_processor.aclose()

    app = FastAPI(title="H9 — Agente conversacional", lifespan=lifespan)
    app.state.settings = config
    app.state.processor = turn_processor

    @app.get("/health", include_in_schema=False)
    async def health() -> dict[str, str]:
        return {"status": "ok"}

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

    @app.exception_handler(ConflictError)
    async def conflict(request: Request, error: ConflictError) -> JSONResponse:
        # H0.2 trata 409 como REJECTED sin reintentar (no es éxito cacheado).
        return JSONResponse(status_code=409, content={"detail": "El mensaje ya se está procesando."})

    @app.post("/api/v1/agent/turn", response_model=TurnResponse, dependencies=[Depends(authorize)])
    async def turn(
        payload: TurnRequest,
        correlation: Annotated[Text, Header(alias="X-Correlation-Id")],
        conversation: Annotated[Text, Header(alias="X-Conversation-Id")],
        message: Annotated[Text, Header(alias="X-Message-Id")],
        idempotency: Annotated[Text, Header(alias="X-Idempotency-Key")],
    ) -> TurnResponse:
        try:
            return await turn_processor.process(payload, idempotency)
        except ConflictError:
            raise
        except TurnUnavailable:
            raise
        except Exception as error:
            raise TurnUnavailable() from error

    return app


app = create_app()


def main() -> None:
    uvicorn.run(app, host="0.0.0.0", port=Settings().port)  # noqa: S104 — servicio dentro del contenedor.


if __name__ == "__main__":
    main()
