import json
import logging
import math
import os
import subprocess
import sys

import httpx
import pytest
from pydantic import SecretStr

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embeddings import EmbeddingProviderUnavailable, FakeEmbedder, GoogleEmbedder


@pytest.fixture
def settings():
    return Settings(
        google_api_key=SecretStr("test-key"),
        embedding_model="test-model",
        embedding_dimensions=3,
        embedding_timeout_s=0.25,
    )


def test_google_sends_configured_model_dimension_and_task_types(settings):
    sent = []

    def handle(request):
        sent.append(request)
        requests = json.loads(request.content)["requests"]
        return httpx.Response(200, json={"embeddings": [{"values": [index, 1, 2]} for index in range(len(requests))]})

    with httpx.Client(transport=httpx.MockTransport(handle)) as client:
        embedder = GoogleEmbedder(settings, http_client=client)
        assert embedder.embed_documents([]) == []
        assert embedder.embed_documents(["Título\nPrimer texto", "Título\nSegundo texto"]) == [[0, 1, 2], [1, 1, 2]]
        assert embedder.embed_query("Consulta de prueba") == [0, 1, 2]
        embedder.close()

    for request, task_type, texts in zip(
        sent,
        ["RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY"],
        [["Título\nPrimer texto", "Título\nSegundo texto"], ["Consulta de prueba"]],
        strict=True,
    ):
        assert request.url.path.endswith("/models/test-model:batchEmbedContents")
        assert request.extensions["timeout"]["read"] == 0.25
        entries = json.loads(request.content)["requests"]
        assert [entry["taskType"] for entry in entries] == [task_type] * len(texts)
        assert [entry["outputDimensionality"] for entry in entries] == [3] * len(texts)
        assert [entry["content"]["parts"][0]["text"] for entry in entries] == texts


def test_google_preserves_document_order_across_batches(settings):
    batch_sizes = []

    def handle(request):
        entries = json.loads(request.content)["requests"]
        batch_sizes.append(len(entries))
        vectors = []
        for entry in entries:
            index = int(entry["content"]["parts"][0]["text"].removeprefix("Documento "))
            vectors.append({"values": [index, 1, 2]})
        return httpx.Response(200, json={"embeddings": vectors})

    documents = [f"Documento {index}" for index in range(33)]
    with httpx.Client(transport=httpx.MockTransport(handle)) as client:
        embedder = GoogleEmbedder(settings, http_client=client)
        assert embedder.embed_documents(documents) == [[index, 1, 2] for index in range(33)]
        embedder.close()

    assert batch_sizes == [32, 1]


@pytest.mark.parametrize("recover", [True, False])
def test_google_retries_timeout_once(settings, recover, caplog):
    caplog.set_level(logging.DEBUG)
    attempts = 0

    def handle(request):
        nonlocal attempts
        attempts += 1
        if attempts == 1 or not recover:
            raise httpx.ReadTimeout("test-key Consulta de prueba", request=request)
        return httpx.Response(200, json={"embeddings": [{"values": [1, 2, 3]}]})

    with httpx.Client(transport=httpx.MockTransport(handle)) as client:
        embedder = GoogleEmbedder(settings, http_client=client)
        if recover:
            assert embedder.embed_query("Consulta de prueba") == [1, 2, 3]
        else:
            with pytest.raises(EmbeddingProviderUnavailable, match="Proveedor de embeddings no disponible") as failure:
                embedder.embed_query("Consulta de prueba")
            assert failure.value.__context__ is None
        embedder.close()

    assert attempts == 2
    assert "test-key" not in caplog.text
    assert "Consulta de prueba" not in caplog.text


@pytest.mark.parametrize("status,attempts_expected", [(400, 1), (503, 2)])
def test_google_sanitizes_http_errors(settings, status, attempts_expected, caplog):
    caplog.set_level(logging.DEBUG)
    attempts = 0

    def handle(request):
        nonlocal attempts
        attempts += 1
        return httpx.Response(status, json={"error": {"message": "detalle privado"}})

    with httpx.Client(transport=httpx.MockTransport(handle)) as client:
        embedder = GoogleEmbedder(settings, http_client=client)
        with pytest.raises(EmbeddingProviderUnavailable) as failure:
            embedder.embed_query("Consulta de prueba")
        assert "detalle privado" not in str(failure.value)
        embedder.close()
    assert attempts == attempts_expected
    assert "detalle privado" not in caplog.text
    assert "test-key" not in caplog.text
    assert "Consulta de prueba" not in caplog.text


def test_google_rejects_wrong_dimensions(settings):
    transport = httpx.MockTransport(lambda request: httpx.Response(200, json={"embeddings": [{"values": [1]}]}))
    with httpx.Client(transport=transport) as client:
        embedder = GoogleEmbedder(settings, http_client=client)
        with pytest.raises(EmbeddingProviderUnavailable):
            embedder.embed_query("Consulta de prueba")
        embedder.close()


def test_google_requires_a_key(settings):
    with pytest.raises(EmbeddingProviderUnavailable):
        GoogleEmbedder(settings.model_copy(update={"google_api_key": None}))


@pytest.mark.parametrize("dimensions", [32, 768])
def test_fake_is_normalized_deterministic_and_keeps_similar_texts_close(dimensions):
    embedder = FakeEmbedder(dimensions)
    query = embedder.embed_query("¿Dónde consulto el horario de transferencias?")
    related, unrelated, empty = embedder.embed_documents(["Consulto el horario de transferencias", "Póliza", ""])
    assert query == FakeEmbedder(dimensions).embed_query("¿DÓNDE CONSULTO EL HORARIO DE TRANSFERENCIAS?")
    for vector in (query, related, unrelated, empty):
        assert len(vector) == dimensions
        assert math.hypot(*vector) == pytest.approx(1.0)
    assert math.fsum(a * b for a, b in zip(query, related, strict=True)) > math.fsum(
        a * b for a, b in zip(query, unrelated, strict=True)
    )


def test_fake_is_stable_across_python_processes():
    command = [
        sys.executable,
        "-c",
        "from wa_knowledge_rag.ingestion.embeddings import FakeEmbedder; "
        "import json; print(json.dumps(FakeEmbedder().embed_query('Horario de transferencias')))",
    ]
    # Fixed command using the current Python interpreter and a synthetic query.
    result = subprocess.run(  # noqa: S603
        command,
        env=os.environ | {"PYTHONHASHSEED": "123"},
        capture_output=True,
        check=True,
        timeout=15,
    )
    assert json.loads(result.stdout) == FakeEmbedder().embed_query("Horario de transferencias")
