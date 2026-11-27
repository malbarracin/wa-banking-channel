"""Detect personal data and instruction patterns in knowledge content."""

import re
import unicodedata
from collections.abc import Collection, Iterator, Mapping
from dataclasses import dataclass

from structlog.typing import EventDict, WrappedLogger

_MASKED = re.compile(r"\*{3}\S{4}(?!\w)")
_PATTERNS = (
    ("correo", re.compile(r"(?P<value>[\w.!#$%&'*+/=?^`{|}~-]+@[\w-]+(?:\.[\w-]+)+)")),
    ("cbu_cvu", re.compile(r"(?<!\w)(?P<value>[0-9]{22})(?!\w)")),
    ("pan", re.compile(r"(?<!\w)(?P<value>[0-9]{16}|[0-9]{4}(?:[ -][0-9]{4}){3})(?!\w)")),
    ("cuit", re.compile(r"(?<!\w)(?P<value>(?:20|23|24|27|30|33|34)-?[0-9]{8}-?[0-9])(?!\w)")),
    (
        "telefono",
        re.compile(
            r"(?<![\w+])(?P<value>0[0-9]{10}|(?:\+54[ .-]?9?[ .-]?)?(?:\([0-9]{2,4}\)[ .-]?)?"
            r"(?:[0-9][ .-]?){9}[0-9]|(?:\+54[ .-]?9?[ .-]?)?\([0-9]{2,4}\)[ .-]?"
            r"(?:[0-9][ .-]?){5,7}[0-9])(?!\w)"
        ),
    ),
    ("dni", re.compile(r"(?<!\w)(?P<value>[0-9]{7,8}|[0-9]{1,2}\.[0-9]{3}\.[0-9]{3})(?!\w)")),
    ("alias", re.compile(r"\balias\s*[:=]\s*(?P<value>[a-z0-9.-]{5,19}[a-z0-9])(?![\w-]|\.\w)", re.IGNORECASE)),
    (
        "alias",
        re.compile(
            r"(?<![\w@.-])(?P<value>(?=[a-z0-9.-]{5,19}[a-z0-9](?![\w-]|\.\w))"
            r"[a-z0-9]+(?:\.[a-z0-9]+){2,})(?![\w-]|\.\w)",
            re.IGNORECASE,
        ),
    ),
)
_INSTRUCTIONS = re.compile(r"\b(?:ignora\s+las\s+instrucciones|system\s*:|actua\s+como\b)")


@dataclass(frozen=True)
class PiiMatch:
    """Location and category of a match, without copying the personal data."""

    kind: str
    start: int
    end: int


def _passes_luhn(value: str) -> bool:
    digits = [int(char) for char in value if char.isdigit()]
    doubled = [digit * 2 for digit in digits[-2::-2]]
    checksum = sum(digits[::-2]) + sum(digit - 9 if digit > 9 else digit for digit in doubled)
    return checksum > 0 and checksum % 10 == 0


def _overlaps(start: int, end: int, findings: Collection[PiiMatch]) -> bool:
    return any(start < finding.end and finding.start < end for finding in findings)


def _matches(text: str) -> Iterator[PiiMatch]:
    occupied = [PiiMatch("masked", *match.span()) for match in _MASKED.finditer(text)]
    for kind, pattern in _PATTERNS:
        for match in pattern.finditer(text):
            start, end = match.span("value")
            if _overlaps(start, end, occupied):
                continue
            finding = PiiMatch(kind, start, end)
            occupied.append(finding)
            if kind != "pan" or _passes_luhn(text[start:end]):
                yield finding


def detect_pii(text: str, approved_values: Collection[str] = ()) -> tuple[PiiMatch, ...]:
    """Report matches except exact values supplied from approved bank parameters."""
    return tuple(
        sorted(
            (match for match in _matches(text) if text[match.start : match.end] not in approved_values),
            key=lambda match: match.start,
        )
    )


def is_phone(value: str) -> bool:
    """Check whether an entire value is a single phone number."""
    findings = detect_pii(value)
    return (
        len(findings) == 1
        and findings[0].kind == "telefono"
        and (findings[0].start, findings[0].end) == (0, len(value))
    )


def mask_pii(text: str) -> str:
    """Keep only the last four characters of each detected value."""
    parts: list[str] = []
    cursor = 0
    for match in detect_pii(text):
        parts.extend((text[cursor : match.start], "***", text[match.end - 4 : match.end]))
        cursor = match.end
    parts.append(text[cursor:])
    return "".join(parts)


def contains_instruction(text: str) -> bool:
    """Recognize the ingestion instruction patterns regardless of case and accents."""
    normalized = unicodedata.normalize("NFKD", text.casefold())
    plain = "".join(char for char in normalized if not unicodedata.combining(char))
    return _INSTRUCTIONS.search(plain) is not None


def _mask_log_value(value: object) -> object:
    if isinstance(value, str):
        return mask_pii(value)
    if isinstance(value, Mapping):
        return {mask_pii(str(key)): _mask_log_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_mask_log_value(item) for item in value]
    if type(value) in (int, float, bool) or value is None:
        masked = mask_pii(str(value))
        return value if masked == str(value) else masked
    return mask_pii(repr(value))


def mask_log_pii(_logger: WrappedLogger, _method: str, event_dict: EventDict) -> EventDict:
    """Redact fields and message text before structlog serializes the event."""
    return {mask_pii(key): _mask_log_value(value) for key, value in event_dict.items()}
