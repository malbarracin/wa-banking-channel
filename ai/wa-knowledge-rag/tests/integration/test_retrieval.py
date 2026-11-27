from datetime import UTC, datetime
from unittest.mock import Mock

import pytest

from wa_knowledge_rag.retrieval.service import KnowledgeUnavailable, retrieve
from wa_knowledge_rag.store.database import transaction
from wa_knowledge_rag.store.repositories import approvals, chunks, piece_versions, pieces, release_items, releases

pytestmark = pytest.mark.integration

_APPROVED_AT = datetime(2024, 2, 1, tzinfo=UTC)
_VECTOR = [1.0] + [0.0] * 767


def test_hybrid_retrieval(migrated):
    embedder = Mock()
    embedder.embed_query.return_value = _VECTOR
    records = [
        ("seguro", "publicada", "seguros", "publico", "faq", "Cobertura de prueba"),
        ("cuenta", "publicada", "cuentas", "publico", "guia", "Cuenta de prueba"),
        ("interno", "publicada", "seguros", "interno", "guia", "Cobertura interna"),
        ("borrador", "borrador", "seguros", "publico", "faq", "Cobertura en edición"),
        ("retirada", "retirada", "seguros", "publico", "faq", "Cobertura retirada"),
        ("vencida", "publicada", "seguros", "publico", "faq", "Cobertura vencida"),
        ("otro-modelo", "publicada", "seguros", "publico", "faq", "Cobertura de otro modelo"),
        ("vieja", "publicada", "seguros", "publico", "faq", "Cobertura de una versión anterior"),
        ("sin-aprobacion", "publicada", "seguros", "publico", "faq", "Cobertura pendiente"),
    ]
    with pytest.raises(KnowledgeUnavailable):
        retrieve(migrated, embedder, "Cobertura", correlation_id="synthetic")

    with transaction(migrated) as connection:
        old = releases.insert(connection, reason="publish")["release_no"]
        active = releases.insert(connection, reason="publish", is_active=True)["release_no"]
        for piece_id, status, family, audience, kind, title in records:
            pieces.insert(connection, piece_id=piece_id)
            piece_versions.insert(
                connection,
                piece_id=piece_id,
                version=1,
                title=title,
                type=kind,
                family=family,
                audience=audience,
                owner_area="area-de-prueba",
                status=status,
                body_template=title,
                source_refs=[],
                content_hash=piece_id,
                valid_from=datetime(2024, 1, 1, tzinfo=UTC),
                valid_to=_APPROVED_AT if piece_id == "vencida" else None,
            )
            if piece_id != "sin-aprobacion":
                approvals.insert(
                    connection,
                    piece_id=piece_id,
                    version=1,
                    role="area_duena",
                    is_test=piece_id != "interno",
                    approved_at=datetime(2024, 1, 15, tzinfo=UTC),
                )
                approvals.insert(
                    connection,
                    piece_id=piece_id,
                    version=1,
                    role="compliance",
                    is_test=piece_id != "interno",
                    approved_at=_APPROVED_AT,
                )
            release_items.insert(
                connection,
                release_no=old if piece_id == "vieja" else active,
                piece_id=piece_id,
                version=1,
                rendered_hash=piece_id,
            )
            for chunk_no in range(2 if piece_id == "seguro" else 1):
                chunks.insert(
                    connection,
                    piece_id=piece_id,
                    version=1,
                    rendered_hash=piece_id,
                    chunk_no=chunk_no,
                    section=None,
                    title=title,
                    text=title,
                    family=family,
                    audience=audience,
                    type=kind,
                    embedding=([0.6, 0.8] + [0.0] * 766) if piece_id == "seguro" else _VECTOR,
                    embedding_model="otro-modelo" if piece_id == "otro-modelo" else migrated.embedding_model,
                )

    try:
        public = retrieve(migrated, embedder, "Cobertura", correlation_id="synthetic")
        assert public["correlationId"] == "synthetic"
        assert public["knowledgeVersion"] == f"kv-{active}"
        assert [item["citation"]["pieceId"] for item in public["results"]] == ["seguro", "cuenta"]
        assert public["results"][0]["score"] == pytest.approx(0.65)
        assert public["confidence"] == {"level": "alta", "score": 1.0, "suggestion": "responder"}
        assert public["results"][0]["citation"] == {
            "pieceId": "seguro",
            "title": "Cobertura de prueba",
            "type": "faq",
            "family": "seguros",
            "audience": "publico",
            "version": 1,
            "section": None,
            "ownerArea": "area-de-prueba",
            "approvedAt": _APPROVED_AT.isoformat(),
            "approvalKind": "prueba",
        }
        citation_fields = public["results"][0]["citation"].keys()
        assert all(item["citation"].keys() == citation_fields for item in public["results"])
        internal = retrieve(
            migrated,
            embedder,
            "Cobertura",
            correlation_id="synthetic",
            audience="interno",
            filters={"family": ["seguros"]},
        )
        results = {item["citation"]["pieceId"]: item for item in internal["results"]}
        assert results.keys() == {"seguro", "interno"}
        assert all(item["citation"].keys() == citation_fields for item in results.values())
        assert results["seguro"]["publishable"]
        assert not results["interno"]["publishable"]
        assert results["interno"]["score"] == 1.0
        assert results["interno"]["citation"]["approvalKind"] == "real"
        filtered = retrieve(
            migrated,
            embedder,
            "Cobertura",
            correlation_id="synthetic",
            audience="interno",
            filters={"type": ["faq"]},
            top_k=1,
        )
        assert [item["citation"]["pieceId"] for item in filtered["results"]] == ["seguro"]

        embedder.embed_query.return_value = [0.0, 0.0, 1.0] + [0.0] * 765
        unrelated = retrieve(migrated, embedder, "Tema ausente", correlation_id="synthetic")
        assert unrelated["results"] == []
        assert unrelated["confidence"] == {"level": "sin_respuesta", "score": 0.0, "suggestion": "derivar"}
    finally:
        with transaction(migrated) as connection:
            releases.set_active(connection, release_no=active, is_active=False)
