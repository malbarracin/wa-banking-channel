"""Embedding port and adapters for Google and offline tests."""

import math
import re
import unicodedata
from collections.abc import Sequence
from hashlib import blake2b
from itertools import batched
from typing import Protocol

import httpx
from google import genai
from google.genai import errors, types

from wa_knowledge_rag.config import Settings

_DOCUMENT_BATCH_SIZE = 32


class EmbeddingProviderUnavailable(RuntimeError):
    """The embedding provider cannot return a usable result."""

    def __init__(self) -> None:
        super().__init__("Proveedor de embeddings no disponible")


class Embedder(Protocol):
    def embed_documents(self, texts: Sequence[str]) -> list[list[float]]:
        """Embed approved, rendered documents in input order."""
        ...

    def embed_query(self, text: str) -> list[float]:
        """Embed a query already masked by the retrieval caller."""
        ...


class GoogleEmbedder:
    def __init__(self, settings: Settings, *, http_client: httpx.Client | None = None) -> None:
        key = settings.google_api_key
        if key is None or not key.get_secret_value():
            raise EmbeddingProviderUnavailable()
        self._model = settings.embedding_model
        self._dimensions = settings.embedding_dimensions
        # Keep retries here so provider error bodies never enter SDK retry logs.
        self._client = genai.Client(
            vertexai=False,
            api_key=key.get_secret_value(),
            http_options=types.HttpOptions(
                timeout=math.ceil(settings.embedding_timeout_s * 1000),
                httpx_client=http_client,
            ),
        )

    def close(self) -> None:
        """Release the SDK client. An injected HTTP client stays caller-owned."""
        self._client.close()

    def embed_documents(self, texts: Sequence[str]) -> list[list[float]]:
        vectors = []
        for batch in batched(texts, _DOCUMENT_BATCH_SIZE):
            vectors.extend(self._embed(list(batch), "RETRIEVAL_DOCUMENT"))
        return vectors

    def embed_query(self, text: str) -> list[float]:
        return self._embed([text], "RETRIEVAL_QUERY")[0]

    def _embed(self, texts: list[str], task_type: str) -> list[list[float]]:
        config = types.EmbedContentConfig(task_type=task_type, output_dimensionality=self._dimensions)
        for _ in range(2):
            try:
                response = self._client.models.embed_content(model=self._model, contents=texts, config=config)
                vectors = [embedding.values or [] for embedding in response.embeddings or []]
            except httpx.TransportError:
                continue
            except errors.APIError as error:
                if error.code in (408, 429) or 500 <= error.code < 600:
                    continue
                break
            except ValueError:
                break

            if len(vectors) == len(texts) and all(
                len(vector) == self._dimensions and all(map(math.isfinite, vector)) for vector in vectors
            ):
                return vectors
            break
        raise EmbeddingProviderUnavailable()


class FakeEmbedder:
    """Feature hashing over word tokens gives overlapping texts similar unit vectors."""

    def __init__(self, dimensions: int = 768) -> None:
        if dimensions < 1:
            raise ValueError("Embedding dimensions must be positive")
        self._dimensions = dimensions

    def embed_documents(self, texts: Sequence[str]) -> list[list[float]]:
        return [self.embed_query(text) for text in texts]

    def embed_query(self, text: str) -> list[float]:
        tokens = re.findall(r"[^\W_]+", unicodedata.normalize("NFC", text).casefold())
        vector = [0.0] * self._dimensions
        for token in tokens or [""]:
            digest = blake2b(token.encode(), digest_size=8).digest()
            vector[int.from_bytes(digest) % self._dimensions] += 1.0
        norm = math.hypot(*vector)
        return [value / norm for value in vector]
