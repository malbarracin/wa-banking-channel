from contextlib import contextmanager
from unittest.mock import Mock

import psycopg
import pytest
from pydantic import SecretStr

from wa_knowledge_rag.config import Settings
from wa_knowledge_rag.ingestion.embeddings import EmbeddingProviderUnavailable
from wa_knowledge_rag.retrieval import service
from wa_knowledge_rag.retrieval.confidence import assess_confidence


@pytest.mark.parametrize(
    ("scores", "level", "suggestion", "best"),
    [
        ([], "sin_respuesta", "derivar", 0.0),
        ([0.4499], "sin_respuesta", "derivar", 0.4499),
        ([0.45], "baja", "derivar", 0.45),
        ([0.70, 0.69], "alta", "responder", 0.70),
        ([0.45, 0.50], "baja", "aclarar", 0.50),
        ([0.4500, 0.5001], "baja", "derivar", 0.5001),
        ([0.4499, 0.45], "baja", "derivar", 0.45),
    ],
)
def test_confidence_boundaries(scores, level, suggestion, best):
    assert assess_confidence(scores, min_relevance=0.45, confidence_threshold=0.70, ambiguity_margin=0.05) == {
        "level": level,
        "suggestion": suggestion,
        "score": best,
    }


def test_threshold_changes_with_environment(monkeypatch):
    levels = []
    for threshold in ("0.70", "0.80"):
        monkeypatch.setenv("KB_CONFIDENCE_THRESHOLD", threshold)
        settings = Settings()
        levels.append(
            assess_confidence(
                [0.75],
                min_relevance=settings.min_relevance,
                confidence_threshold=settings.confidence_threshold,
                ambiguity_margin=settings.ambiguity_margin,
            )["level"]
        )
    assert levels == ["alta", "baja"]


@pytest.fixture
def settings():
    return Settings(database_url=SecretStr("dbname=synthetic"))


def test_masks_query_for_embedding_and_text_search(monkeypatch, settings):
    @contextmanager
    def transaction(settings, *, query):
        assert query
        yield object()

    embedder = Mock()
    embedder.embed_query.return_value = [1.0]
    search = Mock(return_value=[{"release_no": 7, "piece_id": None}])
    monkeypatch.setattr(service, "transaction", transaction)
    monkeypatch.setattr(service, "search", search)

    response = service.retrieve(settings, embedder, "Mi DNI es 99991234", correlation_id="synthetic")
    embedder.embed_query.assert_called_once_with("Mi DNI es ***1234")
    assert search.call_args.kwargs["query"] == "Mi DNI es ***1234"
    assert response["knowledgeVersion"] == "kv-7"
    assert response["results"] == []
    assert response["confidence"] == {"level": "sin_respuesta", "score": 0.0, "suggestion": "derivar"}


@pytest.mark.parametrize("dependency", ["db", "embedder"])
def test_unavailable_dependencies(monkeypatch, settings, dependency):
    embedder = Mock()
    embedder.embed_query.return_value = [1.0]
    if dependency == "embedder":
        embedder.embed_query.side_effect = EmbeddingProviderUnavailable()
    database = Mock(side_effect=psycopg.OperationalError("internal details"))
    monkeypatch.setattr(service, "transaction", database)

    with pytest.raises(service.KnowledgeUnavailable, match="^Base de conocimiento no disponible$") as error:
        service.retrieve(settings, embedder, "Seguros", correlation_id="synthetic")
    assert error.value.code == "KNOWLEDGE_UNAVAILABLE"
    assert database.call_count == (1 if dependency == "db" else 0)
