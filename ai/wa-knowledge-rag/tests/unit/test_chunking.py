import pytest

from wa_knowledge_rag.ingestion.chunking import Chunk, chunk_piece


@pytest.mark.parametrize("piece_type", ["faq", "mensaje"])
def test_single_chunk_keeps_complete_rendered_text(piece_type):
    text = "¿Dónde consulto la guía?\nLa guía está en el canal."
    chunks = chunk_piece(piece_type, "Contenido de prueba", text)
    assert chunks == [Chunk("Contenido de prueba", text, None)]
    assert chunks[0].embedding_text == f"Contenido de prueba\n{text}"


@pytest.mark.parametrize("piece_type", ["guia", "politica"])
def test_sections_preserve_titles_and_nested_headings(piece_type):
    text = "## Cuentas\nPrimer texto.\n### Detalles\nMás texto.\n## Tarjetas\nSegundo.\n## Seguros ##\nTercero."
    chunks = chunk_piece(piece_type, "Prueba", text)
    assert [(chunk.section, chunk.text) for chunk in chunks] == [
        ("Cuentas", "Primer texto.\n### Detalles\nMás texto."),
        ("Tarjetas", "Segundo."),
        ("Seguros", "Tercero."),
    ]


def test_long_section_has_exact_overlap_without_an_extra_tail():
    text = ("abcdefghijklmnñopqrstuvwxyz" * 100)[:2250]
    chunks = chunk_piece("guia", "Prueba", f"## Sección larga\n{text}")
    assert [chunk.section for chunk in chunks] == ["Sección larga"] * 2
    assert [len(chunk.text) for chunk in chunks] == [1200, 1200]
    assert chunks[0].text[-150:] == chunks[1].text[:150]
    assert chunks[0].text + chunks[1].text[150:] == text


def test_intro_and_body_without_headings_keep_the_content():
    assert chunk_piece("guia", "Prueba", "Introducción\n## Índice") == [Chunk("Prueba", "Introducción", None)]
