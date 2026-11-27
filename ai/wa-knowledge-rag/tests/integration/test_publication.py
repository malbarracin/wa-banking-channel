from collections.abc import Callable, Sequence
from pathlib import Path

import pytest

from wa_knowledge_rag import cli
from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embeddings import FakeEmbedder
from wa_knowledge_rag.retrieval.service import retrieve
from wa_knowledge_rag.store.database import transaction
from wa_knowledge_rag.store.repositories import parameter_versions, release_items, releases
from wa_knowledge_rag.workflow.states import Status

pytestmark = pytest.mark.integration
type Command = Callable[..., str]


class CountingEmbedder(FakeEmbedder):
    def __init__(self) -> None:
        super().__init__()
        self.documents: list[str] = []

    def embed_documents(self, texts: Sequence[str]) -> list[list[float]]:
        self.documents.extend(texts)
        return super().embed_documents(texts)

    def close(self) -> None:
        pass


@pytest.fixture
def knowledge(
    migrated: Settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> tuple[Command, CountingEmbedder]:
    with transaction(migrated) as connection:
        connection.execute("TRUNCATE kb_piece, kb_parameter_version, kb_release RESTART IDENTITY CASCADE")
    assert migrated.database_url is not None
    policy = Path("config/approval-policy.yaml").resolve()
    monkeypatch.setenv("KB_DATABASE_URL", migrated.database_url.get_secret_value())
    monkeypatch.setenv("KB_APPROVAL_POLICY_PATH", str(policy))
    monkeypatch.setenv("KB_ENVIRONMENT", "test")
    monkeypatch.chdir(tmp_path)
    Path("content/parameters").mkdir(parents=True)
    Path("content/parameters/parametros.yaml").write_text(
        'horario_transferencias: {value: "horario de prueba", is_test: false}\n', encoding="utf-8"
    )
    embedder = CountingEmbedder()
    monkeypatch.setattr(cli, "GoogleEmbedder", lambda _settings: embedder)

    def run(*args: str, error: str = "") -> str:
        monkeypatch.setattr("sys.argv", ["kb", *args])
        if error:
            with pytest.raises(SystemExit) as result:
                cli.main()
            assert result.value.code == 1
            assert error in capsys.readouterr().err
            return ""
        cli.main()
        output = capsys.readouterr()
        assert not output.err
        return output.out.strip()

    return run, embedder


def write_piece(subject: str, text: str) -> None:
    Path(f"content/{subject}.md").write_text(
        f"""---
id: {subject}
title: Información del canal
type: faq
family: canal
audience: publico
owner_area: soporte
source_refs: ["H9"]
valid_from: 2000-01-01
---
{text}
""",
        encoding="utf-8",
    )


def approve_piece(run: Command, subject: str, text: str, *flags: str) -> None:
    write_piece(subject, text)
    run("ingest", f"content/{subject}.md")
    run("submit", subject)
    for role in ("area_duena", "compliance"):
        run("approve", subject, "--role", role, *flags)


def approve_parameter(run: Command, *flags: str) -> None:
    run("params", "ingest")
    for role in ("area_duena", "compliance"):
        run("params", "approve", "horario_transferencias", "--role", role, *flags)


def test_missing_compliance(knowledge: tuple[Command, CountingEmbedder]) -> None:
    run, embedder = knowledge
    write_piece("horario", "Consultá el horario del canal.")
    run("ingest")
    run("submit", "horario")
    run("approve", "horario", "--role", "area_duena")
    run("publish", "horario", error="falta aprobación de compliance")
    assert not embedder.documents


def test_parameters_must_be_approved(knowledge: tuple[Command, CountingEmbedder], migrated: Settings) -> None:
    run, embedder = knowledge
    approve_piece(run, "horario", "Transferencias: {{horario_transferencias}}.")
    run("publish", "horario", error="Parámetro sin valor aprobado: horario_transferencias")
    approve_parameter(run)
    assert run("publish", "horario") == "kv-1"
    assert len(embedder.documents) == 1
    assert "horario de prueba" in embedder.documents[0]
    assert "{{" not in embedder.documents[0]
    with transaction(migrated) as connection:
        (chunk,) = connection.execute("SELECT text FROM kb_chunk WHERE piece_id = 'horario'").fetchall()
        assert chunk["text"].strip() == "Transferencias: horario de prueba."


def test_real_rejects_test_approvals(
    knowledge: tuple[Command, CountingEmbedder], migrated: Settings, monkeypatch: pytest.MonkeyPatch
) -> None:
    run, embedder = knowledge
    approve_piece(run, "prueba", "Horario de atención.", "--test")
    approve_piece(run, "horario", "Transferencias: {{horario_transferencias}}.")
    approve_parameter(run, "--test")
    monkeypatch.setenv("KB_ENVIRONMENT", "real")
    run("publish", "prueba", error="prueba: el entorno real rechaza")
    run("publish", "horario", error="horario_transferencias: el entorno real rechaza")
    assert not embedder.documents
    with transaction(migrated) as connection:
        assert not releases.find(connection)
        parameter_versions.insert(
            connection,
            name="horario_transferencias",
            version=2,
            value="por la tarde",
            is_test=True,
            status=Status.IN_REVIEW,
        )
    monkeypatch.setenv("KB_ENVIRONMENT", "test")
    for role in ("area_duena", "compliance"):
        run("params", "approve", "horario_transferencias", "--role", role)
    monkeypatch.setenv("KB_ENVIRONMENT", "real")
    run("publish", "horario", error="horario_transferencias: el entorno real rechaza")


def test_release_replace_retire_and_rollback(knowledge: tuple[Command, CountingEmbedder], migrated: Settings) -> None:
    run, embedder = knowledge

    def active_versions() -> list[int]:
        with transaction(migrated) as connection:
            (active,) = releases.find(connection, is_active=True)
            return [row["version"] for row in release_items.find(connection, release_no=active["release_no"])]

    approve_piece(run, "servicio", "Atención por el canal digital.")
    assert run("publish", "servicio") == "kv-1"
    approve_piece(run, "servicio", "Atención desde el canal oficial.")
    assert run("publish", "--all-approved") == "kv-2"
    assert active_versions() == [2]
    assert run("retire", "servicio") == "kv-3"
    assert active_versions() == []
    assert run("sync") == "kv-3"
    assert run("rollback", "--to", "kv-1") == "kv-4"
    assert active_versions() == [1]
    assert run("rollback", "servicio", "--to-version", "2") == "kv-5"
    assert active_versions() == [2]
    assert run("rollback") == "kv-6"
    result = retrieve(
        migrated, embedder, query="Atención por el canal digital.", correlation_id="prueba", audience="publico"
    )
    assert result["knowledgeVersion"] == "kv-6"
    assert [item["citation"]["version"] for item in result["results"]] == [1]
    assert len(embedder.documents) == 2
    with transaction(migrated) as connection:
        operations = connection.execute(
            "SELECT action, release_no FROM kb_audit WHERE action IN ('publish', 'retire', 'rollback') ORDER BY id"
        ).fetchall()
        assert [(row["action"], row["release_no"]) for row in operations] == [
            ("publish", 1),
            ("publish", 2),
            ("retire", 3),
            ("rollback", 4),
            ("rollback", 5),
            ("rollback", 6),
        ]


def test_sync_expiration_and_noop(knowledge: tuple[Command, CountingEmbedder], migrated: Settings) -> None:
    run, embedder = knowledge
    approve_piece(run, "canal", "Ayuda oficial para el canal.")
    assert run("sync") == "kv-1"
    write_piece("canal", "Borrador de una nueva respuesta.")
    assert run("sync") == "kv-1"
    assert len(embedder.documents) == 1
    with transaction(migrated) as connection:
        connection.execute("UPDATE kb_piece_version SET valid_to = '2001-01-01T00:00:00Z' WHERE version = 1")
    query = "Ayuda oficial para el canal."
    before_sync = retrieve(migrated, embedder, query=query, correlation_id="prueba", audience="publico")
    assert before_sync["knowledgeVersion"] == "kv-1"
    assert before_sync["results"] == []
    assert run("sync") == "kv-2"
    assert run("sync") == "kv-2"
    after_sync = retrieve(migrated, embedder, query=query, correlation_id="prueba", audience="publico")
    assert after_sync["knowledgeVersion"] == "kv-2"
    assert after_sync["results"] == []
    assert len(embedder.documents) == 1
    with transaction(migrated) as connection:
        (active,) = releases.find(connection, is_active=True)
        assert active["reason"] == "expire"
        assert not release_items.find(connection, release_no=active["release_no"])
        assert connection.execute("SELECT count(*) FROM kb_audit WHERE action = 'sync'").fetchone() == {"count": 4}


def test_parameter_update_only_reembeds_dependents(knowledge: tuple[Command, CountingEmbedder]) -> None:
    run, embedder = knowledge
    approve_parameter(run)
    approve_piece(run, "horario", "Transferencias: {{horario_transferencias}}.")
    approve_piece(run, "canal", "Usá el canal oficial.")
    assert run("publish", "--all-approved") == "kv-1"
    assert len(embedder.documents) == 2
    Path("content/parameters/parametros.yaml").write_text(
        'horario_transferencias: {value: "turno de prueba", is_test: false}\n', encoding="utf-8"
    )
    approve_parameter(run)
    assert run("publish", "--all-approved") == "kv-2"
    assert embedder.documents[2:] == ["Información del canal\nTransferencias: turno de prueba.\n"]
    assert run("sync") == "kv-2"
    assert len(embedder.documents) == 3


def test_verify_pii_scans_published_fragments(knowledge: tuple[Command, CountingEmbedder], migrated: Settings) -> None:
    run, _ = knowledge
    Path("content/parameters/parametros.yaml").write_text(
        'horario_transferencias: {value: "08005550123", is_test: false}\n', encoding="utf-8"
    )
    approve_parameter(run)
    approve_piece(run, "canal", "Contactá al banco: {{horario_transferencias}}.")
    assert run("publish", "canal") == "kv-1"
    run("verify-pii")
    run("retire", "canal")
    with transaction(migrated) as connection:
        connection.execute("UPDATE kb_chunk SET text = 'Documento sintético: 12345678'")
    run("verify-pii", error="Se encontraron datos personales")


def test_rejected_batch_keeps_active_release_and_rolls_back_chunks(
    knowledge: tuple[Command, CountingEmbedder], migrated: Settings
) -> None:
    run, _ = knowledge
    approve_piece(run, "anterior", "Información original.")
    assert run("publish", "anterior") == "kv-1"
    approve_piece(run, "primero", "Información nueva.")
    approve_piece(run, "ultimo", "Transferencias: {{horario_transferencias}}.")
    run("publish", "--all-approved", error="Parámetro sin valor aprobado: horario_transferencias")
    with transaction(migrated) as connection:
        assert connection.execute("SELECT piece_id FROM kb_chunk").fetchall() == [{"piece_id": "anterior"}]
        (active,) = releases.find(connection, is_active=True)
        assert active["release_no"] == 1
        assert connection.execute("SELECT action FROM kb_audit ORDER BY id DESC LIMIT 1").fetchone() == {
            "action": "reject"
        }


def test_test_parameter_is_reflected_in_citation_and_excluded_in_real(
    knowledge: tuple[Command, CountingEmbedder], migrated: Settings
) -> None:
    run, embedder = knowledge
    approve_parameter(run, "--test")
    approve_piece(run, "horario", "Transferencias: {{horario_transferencias}}.")
    assert run("publish", "horario") == "kv-1"
    options = {
        "query": "Información del canal Transferencias horario de prueba",
        "correlation_id": "prueba",
        "audience": "publico",
    }
    result = retrieve(migrated, embedder, **options)
    assert [item["citation"]["approvalKind"] for item in result["results"]] == ["prueba"]
    assert retrieve(migrated.model_copy(update={"environment": "real"}), embedder, **options)["results"] == []
