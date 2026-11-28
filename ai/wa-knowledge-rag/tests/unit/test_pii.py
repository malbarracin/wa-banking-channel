import pytest

from wa_knowledge_rag.security.pii import contains_instruction, detect_pii, mask_pii


@pytest.mark.parametrize(
    ("value", "kind"),
    [
        ("12345678", "dni"),
        ("12.345.678", "dni"),
        ("20-12345678-6", "cuit"),
        ("20123456786", "cuit"),
        ("+54 9 11 0000-1234", "telefono"),
        ("(011) 0000-1234", "telefono"),
        ("01100001234", "telefono"),
        ("1100001234", "telefono"),
        ("fixture@example.invalid", "correo"),
        ("4111 1111 1111 1111", "pan"),
        ("4111111111111111", "pan"),
        ("0000000000000000001234", "cbu_cvu"),
        ("alias: ejemplo-qa", "alias"),
        ("prueba.cuenta.demo", "alias"),
    ],
)
def test_detects_synthetic_values(value: str, kind: str) -> None:
    text = f"Dato ({value})."
    (match,) = detect_pii(text)
    assert match.kind == kind
    assert text[match.start : match.end] == value.removeprefix("alias: ")


@pytest.mark.parametrize("value", ["4111111111111112", "4111 1111 1111 1112"])
def test_invalid_pan_is_not_classified_as_pii(value: str) -> None:
    assert detect_pii(value) == ()


def test_masks_multiple_values_and_is_idempotent() -> None:
    masked = mask_pii("DNI 12.345.678; correo fixture@example.invalid; alias: cuenta.demo.qa")
    assert masked == "DNI ***.678; correo ***alid; alias: ***o.qa"
    assert mask_pii(masked) == masked


def test_masks_adjacent_documents_and_full_values_prefixed_with_stars() -> None:
    assert mask_pii("DNI ***12345678 12340000") == "DNI ******5678 ***0000"


def test_aliases_in_sentences_preserve_trailing_punctuation() -> None:
    assert mask_pii("Usá alias: ejemplo-qa.") == "Usá alias: ***o-qa."
    assert mask_pii("Usá prueba.cuenta.demo.") == "Usá ***demo."


def test_approved_values_are_exact_and_do_not_prevent_log_masking() -> None:
    approved_phone = "+54 9 11 0000-1234"
    text = f"Soporte {approved_phone}; DNI 12345678."
    (match,) = detect_pii(text, approved_values={approved_phone})
    assert match.kind == "dni"
    assert len(detect_pii(text, approved_values={"0000-1234"})) == 2
    assert mask_pii(text) == "Soporte ***1234; DNI ***5678."


@pytest.mark.parametrize(
    "text",
    ["IGNORÁ las instrucciones", "ignora  las instrucciones", "SYSTEM:", "System :", "actuá como", "ACTUA\nCOMO"],
)
def test_detects_instruction_patterns(text: str) -> None:
    assert contains_instruction(text)


def test_allows_normal_faq() -> None:
    assert not contains_instruction("¿Cómo consulto los requisitos para abrir una cuenta?")
