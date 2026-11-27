"""HTTP request and response models for the retrieval v1 contract."""

from typing import Annotated, Literal, Self

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, model_validator

from wa_knowledge_rag.content import Audience, Family, PieceType

Score = Annotated[float, Field(ge=-1, le=1, strict=True)]
Level = Literal["alta", "baja", "sin_respuesta"]


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)


class Filters(ContractModel):
    family: list[Family] = Field(default_factory=list)
    type: list[PieceType] = Field(default_factory=list)


class RetrieveRequest(ContractModel):
    query: Annotated[str, Field(min_length=1, max_length=1000)]
    filters: Filters = Field(default_factory=Filters)
    audience: Audience = Audience.PUBLIC
    topK: Annotated[int, Field(ge=1, le=10, strict=True)] = 5


class Citation(ContractModel):
    pieceId: str
    title: str
    type: PieceType
    family: Family
    audience: Audience
    version: Annotated[int, Field(strict=True)]
    section: str | None
    ownerArea: str
    approvedAt: AwareDatetime
    approvalKind: Literal["real", "prueba"]


class Result(ContractModel):
    text: str
    score: Score
    publishable: Annotated[bool, Field(strict=True)]
    citation: Citation

    @model_validator(mode="after")
    def validate_publishable(self) -> Self:
        if self.publishable != (self.citation.audience == Audience.PUBLIC):
            raise ValueError("La publicación debe coincidir con la audiencia de la cita.")
        return self


class Confidence(ContractModel):
    level: Level
    score: Score
    suggestion: Literal["responder", "aclarar", "derivar"]


class RetrieveResponse(ContractModel):
    correlationId: str
    knowledgeVersion: Annotated[str, Field(pattern=r"^kv-[0-9]+$")]
    confidence: Confidence
    results: Annotated[list[Result], Field(max_length=10)]

    @model_validator(mode="after")
    def validate_confidence(self) -> Self:
        level = self.confidence.level
        suggestions = {"alta": {"responder"}, "baja": {"aclarar", "derivar"}, "sin_respuesta": {"derivar"}}
        if self.confidence.suggestion not in suggestions[level] or bool(self.results) != (level != "sin_respuesta"):
            raise ValueError("El nivel de confianza no coincide con la sugerencia o los resultados.")
        return self
