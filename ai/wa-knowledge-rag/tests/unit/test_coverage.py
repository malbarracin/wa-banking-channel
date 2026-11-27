import shutil
import sys
from pathlib import Path

import pytest
import yaml

from wa_knowledge_rag.cli import main
from wa_knowledge_rag.content import Audience, PieceType, load_piece
from wa_knowledge_rag.ingestion.params import load_parameters, parameter_names, render_parameters
from wa_knowledge_rag.ingestion.validate import validate_piece

CONTENT = Path(__file__).resolve().parents[2] / "content"
EXPECTED_TOPICS = {
    "cuentas": {
        "operaciones",
        "horario-transferencias",
        "limites-transferencias",
        "destinatarios",
        "confirmaciones",
        "movimientos",
        "fuera-de-alcance",
    },
    "tarjetas": {
        "perdida-o-robo",
        "bloqueo-temporal",
        "aviso-de-viaje",
        "datos-de-tarjeta",
        "consumos",
        "fuera-de-alcance",
    },
    "seguros": {"polizas-y-coberturas", "siniestro", "estado-del-caso", "documentacion", "fuera-de-alcance"},
    "canal": {"asistente", "vinculacion-e-identidad", "nueva-verificacion", "derivacion", "privacidad"},
}


def test_manifest_covers_every_required_topic() -> None:
    manifest = yaml.safe_load((CONTENT / "coverage.yaml").read_text(encoding="utf-8"))
    assert {family: set(topics) for family, topics in manifest.items()} == EXPECTED_TOPICS
    assert any(
        piece.audience == Audience.INTERNAL and piece.type == PieceType.GUIDE
        for piece in map(load_piece, CONTENT.rglob("*.md"))
    )


@pytest.mark.parametrize("path", sorted(CONTENT.rglob("*.md")), ids=lambda path: str(path.relative_to(CONTENT)))
def test_draft_content(path: Path) -> None:
    candidates = load_parameters(CONTENT / "parameters/parametros.yaml")
    piece = load_piece(path)
    assert piece.source_refs
    assert not validate_piece(piece, candidates, max_chars_whatsapp=1000)
    empty_markers = dict.fromkeys(parameter_names(piece.text), "parámetro")
    assert not any(char.isdigit() for char in piece.title + render_parameters(piece.text, empty_markers))
    rendered = render_parameters(piece.text, {name: candidate.value for name, candidate in candidates.items()})
    assert len(rendered) <= 1000


def test_parameters_are_synthetic_and_used() -> None:
    candidates = load_parameters(CONTENT / "parameters/parametros.yaml")
    assert candidates and all(candidate.is_test for candidate in candidates.values())
    assert set(candidates) == {
        name for path in CONTENT.rglob("*.md") for name in parameter_names(load_piece(path).text)
    }


@pytest.fixture
def draft_cli(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    content = shutil.copytree(CONTENT, tmp_path / "content")
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("KB_DATABASE_URL", "")
    monkeypatch.setattr(sys, "argv", ["kb", "coverage"])
    return content


def test_cli_covers_files_and_fails_when_a_faq_is_removed(draft_cli: Path, capsys: pytest.CaptureFixture[str]) -> None:
    main()
    assert "Cobertura completa" in capsys.readouterr().out

    (draft_cli / "tarjetas/aviso-de-viaje.md").unlink()
    with pytest.raises(SystemExit) as error:
        main()
    assert error.value.code == 1
    assert "tarjetas/aviso-de-viaje en los archivos fuente" in capsys.readouterr().err


@pytest.mark.parametrize(
    ("manifest", "message"),
    [
        ("[]", "manifiesto"),
        ("{}\n", "cuatro familias"),
        (
            "cuentas: {a: repetido}\ntarjetas: {b: repetido}\nseguros: {c: c}\ncanal: {d: d}\n",
            "pieza distinta",
        ),
    ],
)
def test_cli_rejects_invalid_manifests(
    draft_cli: Path, capsys: pytest.CaptureFixture[str], manifest: str, message: str
) -> None:
    (draft_cli / "coverage.yaml").write_text(manifest, encoding="utf-8")
    with pytest.raises(SystemExit) as error:
        main()
    assert error.value.code == 1
    assert message in capsys.readouterr().err
