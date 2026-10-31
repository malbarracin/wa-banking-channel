"""Comprobaciones locales de forma; la vigencia siempre la decide H2."""

import re

from pydantic import BaseModel, ConfigDict, Field

from wa_mcp_layer.envelope import ContextoSesion, ErrorNegocio

REVERIFY = "Por seguridad, volvé a verificar tu identidad para continuar"


class BusinessError(Exception):
    def __init__(self, error: ErrorNegocio) -> None:
        self.error = error
        super().__init__(error.mensaje)


def require_credential(context: ContextoSesion) -> str:
    if context.credencial is None or not context.credencial.get_secret_value():
        raise BusinessError(ErrorNegocio(codigo="CREDENCIAL_REQUERIDA", mensaje=REVERIFY))
    token = context.credencial.get_secret_value()
    if len(token) > 8192 or re.fullmatch(r"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", token) is None:
        raise BusinessError(ErrorNegocio(codigo="CREDENCIAL_INVALIDA", mensaje=REVERIFY))
    return token


class ValidatedIdentity(BaseModel):
    """Solo construir a partir de una respuesta exitosa de H2, nunca del modelo."""

    model_config = ConfigDict(extra="ignore", hide_input_in_errors=True, frozen=True)

    vigente: bool
    numeroWhatsapp: str = Field(repr=False, pattern=r"^\+[1-9][0-9]{6,14}$")
    documentoTipo: str = Field(repr=False, pattern=r"^[A-Za-z0-9_-]{1,20}$")
    documentoNumero: str = Field(repr=False, pattern=r"^[A-Za-z0-9.-]{1,30}$")

    def headers(self, context: ContextoSesion) -> dict[str, str]:
        require_credential(context)
        if not self.vigente or self.numeroWhatsapp != context.numero_whatsapp:
            raise BusinessError(ErrorNegocio(codigo="CREDENCIAL_INVALIDA", mensaje=REVERIFY))
        return {
            "X-Canal-Credencial-Valida": "true",
            "X-Credencial-Documento-Tipo": self.documentoTipo,
            "X-Credencial-Documento-Numero": self.documentoNumero,
        }
