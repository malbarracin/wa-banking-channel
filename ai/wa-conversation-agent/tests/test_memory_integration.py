"""Real Redis: persistence, inactivity expiry and linkage deletion."""

import asyncio
import time

import pytest
from redis.asyncio import Redis
from test_memory import KEY_MATERIAL, event
from testcontainers.redis import RedisContainer

from wa_conversation_agent.memory import MemoryScope, RedisLongTermMemoryService, RedisSessionService, RedisStore


@pytest.fixture
def redis_url():
    # Only unavailable Docker skips; image/startup/assertion failures are genuine failures.
    import docker
    from docker.errors import DockerException

    try:
        docker_client = docker.from_env()
        docker_client.ping()
        docker_client.close()
    except DockerException:
        pytest.skip("Docker no disponible; ejecutar uv run pytest tests/test_memory_integration.py -m integration -q")
    with RedisContainer("redis:8.4.0") as container:
        yield f"redis://{container.get_container_host_ip()}:{container.get_exposed_port(6379)}"


@pytest.mark.integration
async def test_real_redis_expiry_and_unlink(redis_url):
    client = Redis.from_url(redis_url)
    store = RedisStore(client, KEY_MATERIAL)
    sessions = RedisSessionService(store, ttl_seconds=2)
    memory = RedisLongTermMemoryService(store)
    try:
        first = await sessions.create_session(app_name="h9", user_id="u", session_id="c", state={"language": "es"})
        await sessions.append_event(first, event("hola", current_intent="ayuda"))
        second = await RedisSessionService(store, ttl_seconds=2).get_session(app_name="h9", user_id="u", session_id="c")
        assert second.state["current_intent"] == "ayuda"
        assert second.events[0].content.parts[0].text == "hola"
        key = sessions._key("h9", "u", "c")
        deadline = time.monotonic() + 6
        while await client.exists(key) and time.monotonic() < deadline:  # noqa: ASYNC110 -- bounded Redis TTL polling.
            await asyncio.sleep(0.05)
        assert await sessions.get_session(app_name="h9", user_id="u", session_id="c") is None
        async with memory.for_turn(MemoryScope("h9", "u", "customer", "active")):
            await memory.add_session_to_memory(first)
            assert (await memory.search_memory(app_name="h9", user_id="u", query="")).memories
        async with memory.for_turn(MemoryScope("h9", "u", "customer", "unlinked")):
            assert not await client.exists(store.key("long", "h9", "customer"))
        async with memory.for_turn(MemoryScope("h9", "u", "customer", "active")):
            assert not (await memory.search_memory(app_name="h9", user_id="u", query="")).memories
    finally:
        await client.aclose()
