"""Contrato HTTP compatible con los DTO de H0.2."""

from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, JsonValue, StringConstraints

Text = Annotated[str, StringConstraints(strict=True, pattern=r"\S")]


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)


class TurnContext(ContractModel):
    technicalContext: dict[str, JsonValue]
    history: list[dict[str, JsonValue]]


class TurnRequest(ContractModel):
    correlationId: UUID
    conversationId: Text
    messageId: Text
    customerAddress: Text
    body: str
    context: TurnContext


class Reply(ContractModel):
    body: Text


class TurnResponse(ContractModel):
    correlationId: UUID
    conversationId: Text
    messageId: Text
    reply: Reply
    escalate: bool
    toolsUsed: list[str]
