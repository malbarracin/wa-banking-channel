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
