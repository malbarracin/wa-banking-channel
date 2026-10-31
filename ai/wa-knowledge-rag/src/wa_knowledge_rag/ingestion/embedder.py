"""Embeddings en lotes acotados con proveedor intercambiable para tests."""

import asyncio
import math
from collections.abc import Sequence
from typing import Protocol

from google import genai
from google.genai import errors, types

from wa_knowledge_rag.config import Settings

from .validator import IngestionError


class Provider(Protocol):
    async def embed(self, texts: list[str], model: str, dimensions: int) -> list[list[float]]: ...


class GoogleProvider:
    def __init__(self, settings: Settings) -> None:
        if settings.google_api_key is None:
            raise IngestionError("EMBEDDING_KEY_REQUIRED")
        self.client = genai.Client(api_key=settings.google_api_key.get_secret_value())

    async def embed(self, texts: list[str], model: str, dimensions: int) -> list[list[float]]:
        response = await self.client.aio.models.embed_content(
            model=model,
            contents=texts,
            config=types.EmbedContentConfig(output_dimensionality=dimensions, task_type="RETRIEVAL_DOCUMENT"),
        )
        return [item.values or [] for item in response.embeddings or []]


class Embedder:
    def __init__(self, provider: Provider, settings: Settings, batch_size: int = 32, attempts: int = 3) -> None:
        if batch_size < 1 or attempts < 1:
            raise ValueError("INVALID_BATCH_SETTINGS")
        self.provider = provider
        self.settings = settings
        self.batch_size = batch_size
        self.attempts = attempts

    async def embed(self, texts: Sequence[str]) -> list[list[float]]:
        result: list[list[float]] = []
        for start in range(0, len(texts), self.batch_size):
            batch = list(texts[start : start + self.batch_size])
            for attempt in range(self.attempts):
                try:
                    async with asyncio.timeout(30):
                        vectors = await self.provider.embed(
                            batch, self.settings.embedding_model, self.settings.embedding_dimensions
                        )
                    break
                except (TimeoutError, ConnectionError, errors.ServerError):
                    if attempt + 1 == self.attempts:
                        raise IngestionError("EMBEDDING_UNAVAILABLE") from None
                    await asyncio.sleep(0.1 * 2**attempt)
            if len(vectors) != len(batch) or any(
                len(v) != self.settings.embedding_dimensions or not all(math.isfinite(n) for n in v) or not any(v)
                for v in vectors
            ):
                raise IngestionError("INVALID_EMBEDDING")
            result.extend(vectors)
        return result
