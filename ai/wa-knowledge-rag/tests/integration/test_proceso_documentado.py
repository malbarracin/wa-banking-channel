"""End-to-end run of docs/proceso-de-contenido.md against PostgreSQL."""

from __future__ import annotations

import json
import re
from hashlib import sha256
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from wa_knowledge_rag import cli
from wa_knowledge_rag.api.app import create_app
from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.content import load_piece
from wa_knowledge_rag.ingestion.embeddings import FakeEmbedder
from wa_knowledge_rag.ingestion.params import load_parameters
from wa_knowledge_rag.store.database import transaction

pytestmark = pytest.mark.integration

COMPONENT = Path(__file__).resolve().parents[2]
DOC = COMPONENT / "docs" / "proceso-de-contenido.md"
EXAMPLE_ID = "canal-ejemplo-proceso"
EXAMPLE_PATH = Path("content/canal/ejemplo-proceso.md")
EXAMPLE_BODY = (
    "Para un trámite de ejemplo, el canal informa el estado aprobado del banco "
    "y, si falta respaldo, deriva a una persona."
)
EXAMPLE_QUERY = "trámite de ejemplo estado aprobado"
DRAFT_ID = "canal-borrador-oculto"
SERVICE_BEARER = "proceso-documentado-credencial"
AUTH = {"Authorization": f"Bearer {SERVICE_BEARER}", "X-Correlation-Id": "proceso-ejemplo"}


def _block(name: str) -> list[list[str]]:
    match = re.search(rf"```kb-{name}\n(.*?)```", DOC.read_text(encoding="utf-8"), re.DOTALL)
    assert match is not None, f"Falta el bloque kb-{name} en {DOC}"
    return [line.split() for line in match.group(1).strip().splitlines() if line.strip()]


PROCESO = _block("proceso")
MINIMO = _block("minimo")


class ProcessEmbedder(FakeEmbedder):
    def close(self) -> None:
        """CLI registers GoogleEmbedder.close; keep the fake compatible."""


def _truncate(settings: Settings) -> None:
    with transaction(settings) as connection:
        connection.execute("TRUNCATE kb_piece, kb_parameter_version, kb_release RESTART IDENTITY CASCADE")


def _run(monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str], *args: str) -> str:
    monkeypatch.setattr("sys.argv", ["kb", *args])
    cli.main()
    output = capsys.readouterr()
    assert not output.err, output.err
    return output.out.strip()


def _write_example_piece() -> None:
    EXAMPLE_PATH.parent.mkdir(parents=True, exist_ok=True)
    EXAMPLE_PATH.write_text(
        f"""---
id: {EXAMPLE_ID}
title: "¿Cómo consulto el estado de un trámite de ejemplo?"
type: faq
family: canal
audience: publico
owner_area: soporte
source_refs:
  - "Guía de proceso de contenido · pieza de ejemplo"
valid_from: 2026-11-27
---
{EXAMPLE_BODY}
""",
        encoding="utf-8",
    )


def _write_draft_piece() -> None:
    path = Path("content/canal/borrador-oculto.md")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        f"""---
id: {DRAFT_ID}
title: "Borrador que no debe publicarse"
type: faq
family: canal
audience: publico
owner_area: soporte
source_refs:
  - "Guía de proceso de contenido · borrador"
valid_from: 2026-11-27
---
Texto exclusivo del borrador oculto que jamás debe aparecer en una consulta.
""",
        encoding="utf-8",
    )


def _settings(migrated: Settings, policy: Path, consumers: list[dict[str, object]]) -> Settings:
    return migrated.model_copy(
        update={
            "environment": "test",
            "approval_policy_path": policy,
            "consumers": SecretStr(json.dumps(consumers)),
        }
    )


def test_documento_cubre_comandos_de_la_arquitectura() -> None:
    text = DOC.read_text(encoding="utf-8")
    required = [
        "kb db migrate",
        "kb ingest",
        "--proposal",
        "kb submit",
        "kb approve",
        "--test",
        "kb params ingest",
        "kb params approve",
        "kb publish",
        "--all-approved",
        "kb list",
        "kb coverage",
        "--published",
        "kb verify-pii",
        "kb retire",
        "kb rollback",
        "--to",
        "--to-version",
        "kb sync",
        "--loop",
        "KB_SYNC_INTERVAL",
        "approval-policy.yaml",
    ]
    missing = [item for item in required if item not in text]
    assert not missing, f"El proceso no documenta: {missing}"
    assert PROCESO[0] == ["db", "migrate"]
    assert PROCESO[-1] == ["verify-pii"]
    assert ["publish", EXAMPLE_ID] in PROCESO
    assert MINIMO == [
        ["ingest"],
        ["params", "ingest"],
        ["publish", "--all-approved"],
        ["coverage", "--published"],
        ["verify-pii"],
    ]


def test_proceso_documentado_publica_y_consulta_sin_reiniciar(
    migrated: Settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    _truncate(migrated)
    assert migrated.database_url is not None
    policy = (COMPONENT / "config" / "approval-policy.yaml").resolve()
    monkeypatch.setenv("KB_DATABASE_URL", migrated.database_url.get_secret_value())
    monkeypatch.setenv("KB_APPROVAL_POLICY_PATH", str(policy))
    monkeypatch.setenv("KB_ENVIRONMENT", "test")
    consumers = [
        {"id": "proceso-test", "token_sha256": sha256(SERVICE_BEARER.encode()).hexdigest(), "allow_internal": False}
    ]
    monkeypatch.setenv("KB_CONSUMERS", json.dumps(consumers))
    monkeypatch.chdir(tmp_path)
    Path("content/parameters").mkdir(parents=True)
    Path("content/parameters/parametros.yaml").write_text("{}\n", encoding="utf-8")
    _write_example_piece()
    _write_draft_piece()
    embedder = ProcessEmbedder()
    monkeypatch.setattr(cli, "GoogleEmbedder", lambda _settings: embedder)
    settings = _settings(migrated, policy, consumers)

    with TestClient(create_app(settings, embedder=embedder)) as client:
        assert client.get("/health").status_code == 503

        _run(monkeypatch, capsys, "ingest", "content/canal/borrador-oculto.md")
        for args in PROCESO:
            if args == ["publish", EXAMPLE_ID]:
                before = client.post(
                    "/v1/retrieve",
                    headers=AUTH,
                    json={"query": EXAMPLE_QUERY, "audience": "publico", "topK": 5},
                )
                # Sin release activa la API responde no disponible; la pieza en revisión aún no se sirve.
                assert before.status_code == 503
                assert before.json()["code"] == "KNOWLEDGE_UNAVAILABLE"
            _run(monkeypatch, capsys, *args)

        after = client.post(
            "/v1/retrieve",
            headers=AUTH,
            json={"query": EXAMPLE_QUERY, "audience": "publico", "topK": 5},
        )
        assert after.status_code == 200
        body = after.json()
        assert body["knowledgeVersion"].startswith("kv-")
        cited = [item["citation"]["pieceId"] for item in body["results"]]
        assert EXAMPLE_ID in cited
        assert DRAFT_ID not in cited

        draft_query = client.post(
            "/v1/retrieve",
            headers={**AUTH, "X-Correlation-Id": "borrador-oculto"},
            json={"query": "borrador oculto jamás debe aparecer", "audience": "publico", "topK": 5},
        )
        assert draft_query.status_code == 200
        assert all(item["citation"]["pieceId"] != DRAFT_ID for item in draft_query.json()["results"])


def test_contenido_minimo_con_aprobaciones_de_prueba(
    migrated: Settings, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    _truncate(migrated)
    assert migrated.database_url is not None
    policy = (COMPONENT / "config" / "approval-policy.yaml").resolve()
    monkeypatch.setenv("KB_DATABASE_URL", migrated.database_url.get_secret_value())
    monkeypatch.setenv("KB_APPROVAL_POLICY_PATH", str(policy))
    monkeypatch.setenv("KB_ENVIRONMENT", "test")
    consumers = [
        {"id": "proceso-test", "token_sha256": sha256(SERVICE_BEARER.encode()).hexdigest(), "allow_internal": True}
    ]
    monkeypatch.setenv("KB_CONSUMERS", json.dumps(consumers))
    monkeypatch.chdir(COMPONENT)
    embedder = ProcessEmbedder()
    monkeypatch.setattr(cli, "GoogleEmbedder", lambda _settings: embedder)
    settings = _settings(migrated, policy, consumers)

    assert MINIMO[0] == ["ingest"]
    _run(monkeypatch, capsys, *MINIMO[0])
    for path in sorted(Path("content").rglob("*.md")):
        piece = load_piece(path)
        _run(monkeypatch, capsys, "submit", piece.id)
        for role in ("area_duena", "compliance"):
            _run(monkeypatch, capsys, "approve", piece.id, "--role", role, "--test")

    assert MINIMO[1] == ["params", "ingest"]
    _run(monkeypatch, capsys, *MINIMO[1])
    for name in sorted(load_parameters()):
        for role in ("area_duena", "compliance"):
            _run(monkeypatch, capsys, "params", "approve", name, "--role", role, "--test")

    release = _run(monkeypatch, capsys, *MINIMO[2])
    assert release.startswith("kv-")
    assert _run(monkeypatch, capsys, *MINIMO[3]) == "Cobertura completa en publicación activa."
    assert _run(monkeypatch, capsys, *MINIMO[4]) == ""

    with TestClient(create_app(settings, embedder=embedder)) as client:
        response = client.post(
            "/v1/retrieve",
            headers={**AUTH, "X-Correlation-Id": "cobertura-minima"},
            json={"query": "horario para transferir", "audience": "publico", "topK": 5},
        )
        assert response.status_code == 200
        body = response.json()
        assert body["results"], body
        assert {item["citation"]["approvalKind"] for item in body["results"]} == {"prueba"}
