import sys
from datetime import UTC, datetime
from pathlib import Path

import pytest
import yaml

from wa_knowledge_rag.cli import main
from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.content import Family
from wa_knowledge_rag.store.database import transaction
from wa_knowledge_rag.store.repositories import piece_versions, pieces, release_items, releases

pytestmark = pytest.mark.integration


def test_coverage_uses_the_active_release(
    migrated: Settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    content = tmp_path / "content"
    content.mkdir()
    manifest = {family.value: {"tema": f"prueba-{family}"} for family in Family}
    (content / "coverage.yaml").write_text(yaml.safe_dump(manifest), encoding="utf-8")
    monkeypatch.chdir(tmp_path)
    assert migrated.database_url is not None
    monkeypatch.setenv("KB_DATABASE_URL", migrated.database_url.get_secret_value())
    monkeypatch.setattr(sys, "argv", ["kb", "coverage", "--published"])

    with transaction(migrated) as connection:
        release_no = releases.insert(connection, reason="publish", is_active=True)["release_no"]
        for family in Family:
            piece_id = f"prueba-{family}"
            pieces.insert(connection, piece_id=piece_id)
            piece_versions.insert(
                connection,
                piece_id=piece_id,
                version=1,
                title="FAQ sintética",
                type="faq",
                family=family,
                audience="publico",
                owner_area="soporte",
                status="publicada",
                body_template="Contenido de prueba.",
                source_refs=["Fuente de prueba"],
                content_hash="origen",
                valid_from=datetime(2025, 1, 1, tzinfo=UTC),
            )
            release_items.insert(
                connection, release_no=release_no, piece_id=piece_id, version=1, rendered_hash="resuelto"
            )

    main()
    assert "Cobertura completa en publicación activa" in capsys.readouterr().out

    with transaction(migrated) as connection:
        releases.set_active(connection, release_no=release_no, is_active=False)
        releases.insert(connection, reason="retire", parent_release_no=release_no, is_active=True)

    with pytest.raises(SystemExit) as error:
        main()
    assert error.value.code == 1
    assert "canal/tema en la publicación activa" in capsys.readouterr().err
