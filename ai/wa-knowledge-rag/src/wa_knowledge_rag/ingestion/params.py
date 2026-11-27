"""Load parameter candidates and resolve markers with caller-approved values."""

import re
from collections.abc import Collection, Mapping
from pathlib import Path
from typing import Annotated

import yaml
from pydantic import BaseModel, ConfigDict, StringConstraints, TypeAdapter, ValidationError

_NAME = r"[a-zA-Z_][a-zA-Z0-9_]*"
_MARKER = re.compile(r"(?<!\{)\{\{(" + _NAME + r")\}\}(?!\})")
ParameterName = Annotated[str, StringConstraints(pattern=rf"^{_NAME}$")]


class ParameterSource(BaseModel):
    """Candidate value and environment marker. Approval is tracked by the caller."""

    model_config = ConfigDict(extra="forbid", strict=True, frozen=True, hide_input_in_errors=True)

    value: Annotated[str, StringConstraints(min_length=1)]
    is_test: bool


_PARAMETERS = TypeAdapter(dict[ParameterName, ParameterSource])


def load_parameters(path: Path = Path("content/parameters/parametros.yaml")) -> dict[str, ParameterSource]:
    """Read the YAML map of names to parameter candidates."""
    try:
        return _PARAMETERS.validate_python(yaml.safe_load(path.read_text(encoding="utf-8")))
    except (yaml.YAMLError, ValidationError):
        raise ValueError("YAML de parámetros inválido") from None


def parameter_names(text: str) -> tuple[str, ...]:
    """Return distinct names in order of appearance and reject malformed markers."""
    if "{{" in _MARKER.sub(" ", text):
        raise ValueError("Marcador de parámetro inválido")
    return tuple(dict.fromkeys(_MARKER.findall(text)))


def validate_parameters(text: str, known_parameters: Collection[str]) -> None:
    for name in parameter_names(text):
        if name not in known_parameters:
            raise ValueError(f"Parámetro desconocido: {name}")


def render_parameters(text: str, approved_values: Mapping[str, str]) -> str:
    """Substitute exact approved values supplied by the caller, in one pass."""
    for name in parameter_names(text):
        value = approved_values.get(name)
        if value is None or not value.strip():
            raise ValueError(f"Parámetro sin valor aprobado: {name}")
        if "{{" in value:
            raise ValueError(f"Valor aprobado con marcador de parámetro: {name}")

    rendered = _MARKER.sub(lambda match: approved_values[match[1]], text)
    if "{{" in rendered:
        raise ValueError("El texto renderizado contiene un marcador de parámetro")
    return rendered
