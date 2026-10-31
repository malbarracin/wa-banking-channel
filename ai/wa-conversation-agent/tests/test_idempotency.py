"""Reserva atómica, replay, TTL y aislamiento del almacén compartido."""

import asyncio
from unittest.mock import patch

import pytest
from fakeredis.aioredis import FakeRedis

from wa_conversation_agent.idempotency import MemoryIdempotencyStore, RedisIdempotencyStore
from wa_conversation_agent.models import TurnResponse


def response() -> TurnResponse:
    return TurnResponse.model_validate(
        {
            "correlationId": "7aa50f54-a81c-45d7-912f-aabdf69a07a2",
            "conversationId": "synthetic-conversation",
            "messageId": "synthetic-message",
            "reply": {"body": "Hola"},
            "escalate": False,
            "toolsUsed": [],
        }
    )


@pytest.mark.asyncio
async def test_redis_atomic_reservation_and_replay_across_workers() -> None:
    client = FakeRedis()
    first = RedisIdempotencyStore(client, "synthetic-key-secret-for-tests-0001")
    second = RedisIdempotencyStore(client, "synthetic-key-secret-for-tests-0001")
    try:
        results = await asyncio.gather(first.begin("message", "fingerprint"), second.begin("message", "fingerprint"))
        assert sorted(result.outcome for result in results) == ["acquired", "in_progress"]
        await first.complete("message", response())
        replay = await second.begin("message", "fingerprint")
        assert replay.response == response()
        assert (await second.begin("message", "changed")).outcome == "in_progress"
        keys = await client.keys("*")
        assert len(keys) == 1
        assert b"message" not in keys[0]
        assert 604790 <= await client.ttl(keys[0]) <= 604800
        await client.delete(keys[0])
        assert (await second.begin("message", "fingerprint")).outcome == "acquired"
    finally:
        await client.aclose()


@pytest.mark.asyncio
async def test_memory_ttl_and_fingerprint() -> None:
    store = MemoryIdempotencyStore(ttl_seconds=10)
    with patch("wa_conversation_agent.idempotency.time.monotonic", return_value=10):
        assert (await store.begin("message", "first")).outcome == "acquired"
        await store.complete("message", response())
        assert (await store.begin("message", "changed")).outcome == "in_progress"
    with patch("wa_conversation_agent.idempotency.time.monotonic", return_value=21):
        assert (await store.begin("message", "first")).outcome == "acquired"


@pytest.mark.parametrize("secret,ttl", [("short", 10), ("synthetic-key-secret-for-tests-0001", 0)])
def test_invalid_redis_configuration(secret: str, ttl: int) -> None:
    with pytest.raises(ValueError):
        RedisIdempotencyStore(FakeRedis(), secret, ttl)
