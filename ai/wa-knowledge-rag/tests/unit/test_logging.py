import json

import pytest
import structlog

from wa_knowledge_rag.observability.logging import configure_logging, correlation_context, get_correlation_id


def test_correlation_id_in_json_and_scope(capsys: pytest.CaptureFixture[str]) -> None:
    previous_config = structlog.get_config()
    try:
        configure_logging()
        logger = structlog.get_logger()
        assert get_correlation_id() is None

        with correlation_context("request-1"):
            with correlation_context("request-2"):
                logger.info("retrieval")
                assert get_correlation_id() == "request-2"
            assert get_correlation_id() == "request-1"

        assert get_correlation_id() is None
        logger.info("idle")
        retrieval, idle = map(json.loads, capsys.readouterr().out.splitlines())
        assert retrieval["event"] == "retrieval"
        assert retrieval["correlationId"] == "request-2"
        assert "correlationId" not in idle
    finally:
        structlog.configure(**previous_config)


def test_personal_data_is_masked_in_json(capsys: pytest.CaptureFixture[str]) -> None:
    previous_config = structlog.get_config()
    try:
        configure_logging()
        logger = structlog.get_logger()
        try:
            raise ValueError("DNI 12345678")
        except ValueError:
            logger.exception(
                "Consulta 12345678",
                dni=12345678,
                details={"fixture@example.invalid": ["DNI 12.345.678", ("alias: ejemplo-qa",), 12]},
            )
        output = capsys.readouterr().out
        event = json.loads(output)
        assert event["event"] == "Consulta ***5678"
        assert event["dni"] == "***5678"
        assert event["details"] == {"***alid": ["DNI ***.678", ["alias: ***o-qa"], 12]}
        assert "ValueError: DNI ***5678" in event["exception"]
        assert "12345678" not in output
    finally:
        structlog.configure(**previous_config)
