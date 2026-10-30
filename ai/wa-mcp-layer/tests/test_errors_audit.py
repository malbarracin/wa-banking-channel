"""Mapeo estable y auditoría JSON sin datos de la operación."""

import json
from datetime import datetime

import pytest
import structlog
from pydantic import SecretStr

from wa_mcp_layer.audit.logger import log_invocation
from wa_mcp_layer.envelope import ContextoSesion
from wa_mcp_layer.errors import map_error
from wa_mcp_layer.security.session_context import REVERIFY


@pytest.mark.parametrize(
    "status,code",
    [
        (400, "SOLICITUD_INVALIDA"),
        (401, "CREDENCIAL_INVALIDA"),
        (403, "VINCULO_INACTIVO"),
        (404, "NO_ENCONTRADO"),
        (409, "CONFLICTO"),
        (423, "CONFLICTO"),
        (500, "SERVICIO_NO_DISPONIBLE"),
        (503, "SERVICIO_NO_DISPONIBLE"),
        (599, "SERVICIO_NO_DISPONIBLE"),
        (None, "SERVICIO_NO_DISPONIBLE"),
    ],
)
def test_business_errors(status: int | None, code: str) -> None:
    error = map_error(status)
    assert error.codigo == code
    assert error.reintentable == (code == "SERVICIO_NO_DISPONIBLE")
    if status in (401, 403):
        assert error.mensaje == REVERIFY
    assert error.mensaje


@pytest.mark.parametrize("resultado", ["EXITO", "CREDENCIAL_INVALIDA", "SERVICIO_NO_DISPONIBLE"])
def test_json_audit_without_secrets(resultado: str, capsys: pytest.CaptureFixture[str]) -> None:
    context = ContextoSesion(
        numero_whatsapp="+5491100001234", credencial=SecretStr("synthetic-secret"), correlation_id="test-123"
    )
    structlog.contextvars.bind_contextvars(credencial="context-secret", body="private-body")
    try:
        log_invocation(
            context=context,
            capacidad="cuentas.listar",
            version=1,
            servicio_origen="H4",
            resultado=resultado,
            duracion_ms=12.5,
        )
    finally:
        structlog.contextvars.clear_contextvars()
    output = capsys.readouterr().out
    event = json.loads(output)
    assert event == {
        "event": "invocacion_capacidad",
        "timestamp": event["timestamp"],
        "correlation_id": "test-123",
        "capacidad": "cuentas.listar",
        "version": 1,
        "servicio_origen": "H4",
        "actor": "***1234",
        "resultado": resultado,
        "duracion_ms": 12.5,
    }
    assert datetime.fromisoformat(event["timestamp"]).tzinfo is not None
    for secret in ("synthetic-secret", "context-secret", "private-body", context.numero_whatsapp):
        assert secret not in output


@pytest.mark.parametrize(
    "changes",
    [
        {"capacidad": "bad\nmetadata"},
        {"servicio_origen": ""},
        {"resultado": "SQL failed"},
        {"version": 0},
        {"duracion_ms": -1},
        {"duracion_ms": float("nan")},
        {"duracion_ms": float("inf")},
    ],
)
def test_invalid_audit_metadata(changes: dict[str, object], capsys: pytest.CaptureFixture[str]) -> None:
    arguments = dict(
        context=ContextoSesion(numero_whatsapp="+5491100001234", correlation_id="test"),
        capacidad="cuentas.listar",
        version=1,
        servicio_origen="H4",
        resultado="EXITO",
        duracion_ms=0,
    )
    arguments.update(changes)
    with pytest.raises(ValueError, match="auditoría inválid"):
        log_invocation(**arguments)
    assert capsys.readouterr().out == ""
