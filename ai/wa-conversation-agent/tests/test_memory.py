"""Privacy and ADK interface tests without network or a model."""

import asyncio
import json

import pytest
from fakeredis.aioredis import FakeRedis
from google.adk.agents import Agent
from google.adk.events import Event, EventActions
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService, Session
from google.adk.sessions.base_session_service import GetSessionConfig
from google.genai import types
from pydantic import SecretStr

from wa_conversation_agent.config import Settings
from wa_conversation_agent.memory import (
    MemoryScope,
    RedisLongTermMemoryService,
    RedisSessionService,
    RedisStore,
    create_memory_services,
)

KEY_MATERIAL = "synthetic-key-for-memory-unit-tests-only"


def event(text="hola", **state):
    return Event(
        author="user", content=types.Content(parts=[types.Part(text=text)]), actions=EventActions(state_delta=state)
    )


@pytest.fixture
async def services():
    client = FakeRedis()
    store = RedisStore(client, KEY_MATERIAL)
    yield RedisSessionService(store), RedisLongTermMemoryService(store)
    await client.aclose()


async def test_in_memory_continuity_between_turns():
    service = InMemorySessionService()
    first = await service.create_session(app_name="h9", user_id="user", session_id="conversation")
    await service.append_event(first, event("hola", current_intent="ayuda"))
    second = await service.get_session(app_name="h9", user_id="user", session_id="conversation")
    assert second is not None
    assert second.state["current_intent"] == "ayuda"
    await service.append_event(second, event("continuar"))
    third = await service.get_session(app_name="h9", user_id="user", session_id="conversation")
    assert third is not None and len(third.events) == 2
    assert third.events[0].content.parts[0].text == "hola"


async def test_redis_continuity_filters_and_ttl(services):
    sessions, _ = services
    first = await sessions.create_session(
        app_name="h9", user_id="u", session_id="c", state={"onboarding_step": "inicio"}
    )
    key = sessions._key("h9", "u", "c")
    await sessions.store.client.expire(key, 4)
    await sessions.append_event(first, event("hola", current_intent="ayuda"))
    second = await sessions.get_session(app_name="h9", user_id="u", session_id="c")
    assert second.state == {"onboarding_step": "inicio", "current_intent": "ayuda"}
    assert second.events[0].content.parts[0].text == "hola"
    assert await sessions.store.client.ttl(key) > 1790
    await sessions.store.client.expire(key, 4)
    await sessions.get_session(app_name="h9", user_id="u", session_id="c")
    assert await sessions.store.client.ttl(key) > 1790
    await sessions.append_event(second, event("continuar"))
    for count in [0, 1]:
        loaded = await sessions.get_session(
            app_name="h9", user_id="u", session_id="c", config=GetSessionConfig(num_recent_events=count)
        )
        assert len(loaded.events) == count
    loaded = await sessions.get_session(
        app_name="h9", user_id="u", session_id="c", config=GetSessionConfig(after_timestamp=10**12)
    )
    assert loaded.events == []
    with pytest.raises(ValueError, match="ya existe"):
        await sessions.create_session(app_name="h9", user_id="u", session_id="c")
    with pytest.raises(NotImplementedError):
        await sessions.list_sessions(app_name="h9", user_id="u")
    await sessions.delete_session(app_name="h9", user_id="u", session_id="c")
    assert await sessions.get_session(app_name="h9", user_id="u", session_id="c") is None
    with pytest.raises(ValueError, match="expiro"):
        await sessions.append_event(first, event())


async def test_no_prohibited_data_in_any_redis_key_or_value(services):
    sessions, memory = services
    prohibited = ["synthetic-token-opaque", "12345678", "saldo=9876.54", "Cliente Sintetico", "+34000111222"]
    state = dict(zip(["credencial", "documento", "saldo", "nombre", "telefono"], prohibited, strict=True))
    state.update(
        {"language": "es", "summary": prohibited[0], "response_style": "breve", "current_intent": prohibited[1]}
    )
    session = await sessions.create_session(app_name="h9", user_id=prohibited[4], session_id=prohibited[3], state=state)
    unsafe = event(" ".join(prohibited), **state)
    unsafe.invocation_id = prohibited[0]
    unsafe.author = prohibited[3]
    unsafe.actions.artifact_delta = {prohibited[0]: 1}
    unsafe.content.parts.append(types.Part(function_response=types.FunctionResponse(name="productos", response=state)))
    await sessions.append_event(session, unsafe)
    async with memory.for_turn(MemoryScope("h9", prohibited[4], prohibited[3], "active")):
        await memory.add_session_to_memory(session)
        result = await memory.search_memory(app_name="h9", user_id=prohibited[4], query=prohibited[0])
        assert json.loads(result.memories[0].content.parts[0].text) == {"language": "es", "response_style": "breve"}
    raw = b""
    async for key in sessions.store.client.scan_iter():
        raw += key + (await sessions.store.client.get(key) or b"")
    for value in prohibited:
        assert value.encode() not in raw
    loaded = await sessions.get_session(app_name="h9", user_id=prohibited[4], session_id=prohibited[3])
    assert loaded.events[0].content.parts[0].text == "[contenido omitido]"
    # A model-controlled field cannot manufacture a linked identity.
    session.state.update({"link_status": "active", "customer_id": "forged"})
    await memory.add_session_to_memory(session)
    assert not (await memory.search_memory(app_name="h9", user_id=prohibited[4], query="")).memories


@pytest.mark.parametrize("status", ["blocked", "unlinked"])
async def test_linkage_isolation_and_deletion(services, status):
    sessions, memory = services
    session = await sessions.create_session(app_name="h9", user_id="u", session_id="c", state={"language": "es"})
    async with memory.for_turn(MemoryScope("h9", "u", "customer", "active")):
        await memory.add_session_to_memory(session)
        assert (await memory.search_memory(app_name="h9", user_id="u", query="")).memories
        assert not (await memory.search_memory(app_name="h9", user_id="other", query="")).memories
        assert not (await memory.search_memory(app_name="other", user_id="u", query="")).memories
    async with memory.for_turn(MemoryScope("h9", "u", "other-customer", "active")):
        assert not (await memory.search_memory(app_name="h9", user_id="u", query="")).memories
    async with memory.for_turn(MemoryScope("h9", "u", "customer", status)):
        await memory.add_session_to_memory(session)
        assert not (await memory.search_memory(app_name="h9", user_id="u", query="")).memories
    async with memory.for_turn(MemoryScope("h9", "u", "customer", "active")):
        assert not (await memory.search_memory(app_name="h9", user_id="u", query="")).memories
    with pytest.raises(ValueError, match="invalido"):
        async with memory.for_turn(MemoryScope("h9", "u", "", "active")):
            pass


async def test_scope_is_task_local_and_resets_on_failure(services):
    sessions, memory = services
    session = Session(app_name="h9", user_id="u", id="c", state={"language": "es"})
    entered = asyncio.Event()

    async def authorized():
        with pytest.raises(RuntimeError):
            async with memory.for_turn(MemoryScope("h9", "u", "customer", "active")):
                await memory.add_session_to_memory(session)
                entered.set()
                await asyncio.sleep(0)
                raise RuntimeError("synthetic failure")
        assert not (await memory.search_memory(app_name="h9", user_id="u", query="")).memories

    async def unauthorized():
        await entered.wait()
        assert not (await memory.search_memory(app_name="h9", user_id="u", query="")).memories

    await asyncio.gather(authorized(), unauthorized())


async def test_partial_events_and_safe_projection(services):
    sessions, memory = services
    session = await sessions.create_session(app_name="h9", user_id="u", session_id="c")
    partial = event("hola")
    partial.partial = True
    assert await sessions.append_event(session, partial) is partial
    assert (await sessions.get_session(app_name="h9", user_id="u", session_id="c")).events == []
    await sessions.append_event(session, Event(author="agent"))
    loaded = await sessions.get_session(app_name="h9", user_id="u", session_id="c")
    assert loaded.events[0].author == "assistant"
    async with memory.for_turn(MemoryScope("h9", "u", "customer", "active")):
        await memory.add_session_to_memory(session)
        assert not (await memory.search_memory(app_name="h9", user_id="u", query="")).memories


async def test_factory_and_runner_compatibility(services):
    sessions, memory = services
    runner = Runner(
        app_name="h9", agent=Agent(name="h9", model="gemini-2.5-flash"), session_service=sessions, memory_service=memory
    )
    assert runner.session_service is sessions and runner.memory_service is memory
    with pytest.raises(ValueError, match="REDIS_URL"):
        create_memory_services(Settings())
    with pytest.raises(ValueError, match="MEMORY_KEY_SECRET"):
        create_memory_services(Settings(redis_url=SecretStr("redis://localhost:6379")))
    with pytest.raises(ValueError, match="TTL"):
        RedisSessionService(sessions.store, 0)
    configured, _ = create_memory_services(
        Settings(
            redis_url=SecretStr("redis://localhost:6379"),
            memory_key_secret=SecretStr(KEY_MATERIAL),
            session_ttl_minutes=30,
        )
    )
    assert configured.ttl_seconds == 1800
    await configured.store.client.aclose()
