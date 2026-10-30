"""Sobres comunes; los secretos y la identidad no aparecen en repr."""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, JsonValue, SecretStr


class ContextoSesion(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)

    numero_whatsapp: str = Field(pattern=r"^\+[1-9][0-9]{6,14}$", repr=False)
    credencial: SecretStr | None = Field(default=None, repr=False, exclude=True)
    correlation_id: str = Field(min_length=1, max_length=128, pattern=r"^[\w.-]+$")


class ErrorNegocio(BaseModel):
    codigo: str
    mensaje: str
    reintentable: bool = False


class Auditoria(BaseModel):
    capacidad: str
    version: int = Field(ge=1)
    correlation_id: str
    timestamp: datetime


class Resultado(BaseModel):
    resultado: JsonValue
    auditoria: Auditoria


class Fallo(BaseModel):
    error: ErrorNegocio
    auditoria: Auditoria
