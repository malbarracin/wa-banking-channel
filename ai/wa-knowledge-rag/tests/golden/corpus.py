"""Publish the minimum approved content corpus for golden-set retrieval."""

from __future__ import annotations

import shutil
from collections.abc import Callable, Sequence
from pathlib import Path

import pytest
import yaml

from wa_knowledge_rag import cli
from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.content import load_piece
from wa_knowledge_rag.ingestion.embeddings import Embedder, FakeEmbedder
from wa_knowledge_rag.ingestion.params import load_parameters
from wa_knowledge_rag.store.database import transaction

from . import CONTENT_DIR

type Command = Callable[..., str]


class _ClosableEmbedder:
    """CLI registers provider.close() on the ExitStack; FakeEmbedder has none."""

    def __init__(self, inner: Embedder) -> None:
        self._inner = inner

    def embed_documents(self, texts: Sequence[str]) -> list[list[float]]:
        return self._inner.embed_documents(texts)

    def embed_query(self, text: str) -> list[float]:
        return self._inner.embed_query(text)

    def close(self) -> None:
        closer = getattr(self._inner, "close", None)
        if callable(closer):
            closer()


def publish_minimum_corpus(
    migrated: Settings,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
    *,
    embedder: Embedder | None = None,
) -> tuple[Command, Embedder]:
    """Copy content/, approve every piece and parameter with --test, then publish."""
    with transaction(migrated) as connection:
        connection.execute("TRUNCATE kb_piece, kb_parameter_version, kb_release RESTART IDENTITY CASCADE")
    assert migrated.database_url is not None

    target = tmp_path / "content"
    if target.exists():
        shutil.rmtree(target)
    shutil.copytree(CONTENT_DIR, target)
    policy = Path("config/approval-policy.yaml").resolve()
    monkeypatch.setenv("KB_DATABASE_URL", migrated.database_url.get_secret_value())
    monkeypatch.setenv("KB_APPROVAL_POLICY_PATH", str(policy))
    monkeypatch.setenv("KB_ENVIRONMENT", "test")
    monkeypatch.chdir(tmp_path)

    inner: Embedder = embedder or FakeEmbedder(migrated.embedding_dimensions)
    provider = _ClosableEmbedder(inner)
    monkeypatch.setattr(cli, "GoogleEmbedder", lambda _settings: provider)

    def run(*args: str) -> str:
        monkeypatch.setattr("sys.argv", ["kb", *args])
        cli.main()
        output = capsys.readouterr()
        assert not output.err, output.err
        return output.out.strip()

    run("ingest")
    piece_ids = sorted(load_piece(path).id for path in (tmp_path / "content").rglob("*.md"))
    for piece_id in piece_ids:
        run("submit", piece_id)
        for role in ("area_duena", "compliance"):
            run("approve", piece_id, "--role", role, "--test")

    run("params", "ingest")
    parameter_names = sorted(load_parameters(tmp_path / "content" / "parameters" / "parametros.yaml"))
    for name in parameter_names:
        for role in ("area_duena", "compliance"):
            run("params", "approve", name, "--role", role, "--test")

    release = run("publish", "--all-approved")
    assert release.startswith("kv-")
    return run, inner


def coverage_piece_ids(content_dir: Path = CONTENT_DIR) -> set[str]:
    manifest = yaml.safe_load((content_dir / "coverage.yaml").read_text(encoding="utf-8"))
    return {piece_id for topics in manifest.values() for piece_id in topics.values()}
