import os
import subprocess
import sys
from collections.abc import Iterator
from pathlib import Path

import pytest
from pydantic import SecretStr
from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import LogMessageWaitStrategy

from wa_knowledge_rag.config import Settings


@pytest.fixture(scope="module")
def postgres() -> Iterator[Settings]:
    # Disposable test instance with no host port fixed or real credentials.
    with (
        DockerContainer("pgvector/pgvector:pg16")
        .with_env("POSTGRES_HOST_AUTH_METHOD", "trust")
        .with_exposed_ports(5432)
        .waiting_for(LogMessageWaitStrategy("database system is ready to accept connections", times=2))
    ) as container:
        host = container.get_container_host_ip()
        port = container.get_exposed_port(5432)
        yield Settings(database_url=SecretStr(f"host={host} port={port} dbname=postgres user=postgres"))


@pytest.fixture(scope="module")
def migrated(postgres: Settings) -> Settings:
    assert postgres.database_url is not None
    env = os.environ | {
        "KB_DATABASE_URL": postgres.database_url.get_secret_value(),
        "KB_EMBEDDING_DIMENSIONS": "768",
    }
    cli = Path(sys.executable).with_name("kb")
    for _ in range(2):
        # The entry point is installed beside this interpreter and takes fixed arguments.
        result = subprocess.run(  # noqa: S603
            [cli, "db", "migrate"], env=env, capture_output=True, check=False, timeout=30
        )
        assert result.returncode == 0, result.stderr.decode()
    return postgres
