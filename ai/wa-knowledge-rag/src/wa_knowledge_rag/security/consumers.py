"""Authenticate service consumers against configured token digests."""

from hashlib import sha256
from hmac import compare_digest
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, SecretStr, TypeAdapter, ValidationError


class Consumer(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True, frozen=True)

    id: Annotated[str, Field(min_length=1)]
    token_sha256: Annotated[str, Field(pattern=r"^[0-9a-fA-F]{64}$", repr=False)]
    allow_internal: bool


_consumer_config = TypeAdapter(list[Consumer], config=ConfigDict(strict=True))


def load_consumers(config: SecretStr | None) -> tuple[Consumer, ...]:
    if config is None:
        return ()
    try:
        consumers = _consumer_config.validate_json(config.get_secret_value())
    except ValidationError:
        raise ValueError("KB_CONSUMERS tiene un formato inválido.") from None
    digests = {consumer.token_sha256.lower() for consumer in consumers}
    if len(digests) != len(consumers) or len({consumer.id for consumer in consumers}) != len(consumers):
        raise ValueError("KB_CONSUMERS tiene consumidores o credenciales duplicados.")
    return tuple(consumers)


def authenticate(authorization: str, consumers: tuple[Consumer, ...]) -> Consumer | None:
    scheme, _, token = authorization.partition(" ")
    token = token.strip(" ")
    if scheme.lower() != "bearer" or not token or any(char.isspace() for char in token):
        return None
    digest = sha256(token.encode()).hexdigest()
    match = None
    for consumer in consumers:
        if compare_digest(digest, consumer.token_sha256.lower()):
            match = consumer
    return match
