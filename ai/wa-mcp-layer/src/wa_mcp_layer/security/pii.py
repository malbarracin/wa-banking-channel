"""Enmascarado irreversible para eventos de auditoría."""


def mask_document(value: str) -> str:
    return "***" + value[-4:] if len(value) > 4 else "***"


def mask_phone(value: str) -> str:
    return mask_document(value)


def mask_name(value: str) -> str:
    return "***"
