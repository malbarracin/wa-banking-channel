"""Redis persistence with a closed vocabulary at the storage boundary.

Free text and tool payloads are never persisted. Authorization is supplied by
trusted turn processing, outside model state, for the complete Runner invocation.
"""

import hashlib
import hmac
import json
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from contextvars import ContextVar
from dataclasses import dataclass
from typing import Any, Literal
from uuid import uuid4

from google.adk.events import Event, EventActions
from google.adk.memory.base_memory_service import BaseMemoryService, SearchMemoryResponse
from google.adk.memory.memory_entry import MemoryEntry
from google.adk.sessions import BaseSessionService, Session
from google.adk.sessions.base_session_service import GetSessionConfig, ListSessionsResponse
from google.genai import types
from redis.asyncio import Redis

from .config import Settings

# Closed sets prevent opaque credentials and unlabelled PII from passing a regex filter.
STATE_VALUES = {
    "onboarding_step": frozenset({"inicio", "verificacion", "vinculado", "finalizado"}),
    "current_intent": frozenset(
        {"saludo", "ayuda", "consulta", "escalamiento", "onboarding", "consulta_informativa", "operacion", "soporte"}
    ),
    "language": frozenset({"es", "en"}),
    "response_style": frozenset({"breve", "detallado"}),
    "summary": frozenset({"solicito_ayuda", "prefiere_respuestas_breves", "solicito_atencion_humana"}),
}
LONG_TERM_FIELDS = frozenset({"language", "response_style", "summary"})
SAFE_TEXT = frozenset({"hola", "necesito ayuda", "continuar", "gracias", "prefiero respuestas breves"})


def safe_state(state: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in state.items()
        if key in STATE_VALUES and isinstance(value, str) and value in STATE_VALUES[key]
    }


def safe_event(event: Event) -> Event:
    texts = []
    for part in (event.content.parts or []) if event.content else []:
        if part.text and part.text.casefold().strip() in SAFE_TEXT:
            texts.append(part.text.casefold().strip())
    return Event(
        author="user" if event.author == "user" else "assistant",
        content=types.Content(
            role="user" if event.author == "user" else "model",
            parts=[types.Part(text="\n".join(texts) if texts else "[contenido omitido]")],
        ),
        actions=EventActions(state_delta=safe_state(event.actions.state_delta)),
    )


class RedisStore:
    def __init__(self, client: Redis, key_secret: str) -> None:
        if len(key_secret) < 32:
            raise ValueError("Configurar AGENT_MEMORY_KEY_SECRET con al menos 32 caracteres")
        self.client = client
        self._secret = key_secret.encode()

    def key(self, kind: str, *identifiers: str) -> str:
        digest = hmac.new(self._secret, json.dumps(identifiers).encode(), hashlib.sha256).hexdigest()
        return f"h9:v1:{kind}:{digest}"


class RedisSessionService(BaseSessionService):
    """One session per conversation; all reads/writes renew inactivity TTL."""

    def __init__(self, store: RedisStore, ttl_seconds: int = 1800) -> None:
        if ttl_seconds < 1:
            raise ValueError("TTL debe ser positivo")
        self.store = store
        self.ttl_seconds = ttl_seconds

    def _key(self, app_name: str, user_id: str, session_id: str) -> str:
        return self.store.key("session", app_name, user_id, session_id)

    def _payload(self, session: Session) -> str:
        return json.dumps(
            {
                "state": safe_state(session.state),
                "events": [event.model_dump(mode="json") for event in session.events[-100:]],
                "last_update_time": time.time(),
            }
        )

    async def create_session(
        self, *, app_name: str, user_id: str, state: dict[str, Any] | None = None, session_id: str | None = None
    ) -> Session:
        session = Session(
            app_name=app_name, user_id=user_id, id=session_id or str(uuid4()), state=safe_state(state or {})
        )
        created = await self.store.client.set(
            self._key(app_name, user_id, session.id), self._payload(session), ex=self.ttl_seconds, nx=True
        )
        if not created:
            raise ValueError("La conversacion ya existe")
        return session

    async def get_session(
        self, *, app_name: str, user_id: str, session_id: str, config: GetSessionConfig | None = None
    ) -> Session | None:
        raw = await self.store.client.getex(self._key(app_name, user_id, session_id), ex=self.ttl_seconds)
        if raw is None:
            return None
        session = Session(app_name=app_name, user_id=user_id, id=session_id, **json.loads(raw))
        if config:
            if config.after_timestamp is not None:
                session.events = [e for e in session.events if e.timestamp >= config.after_timestamp]
            if config.num_recent_events is not None:
                count = max(0, config.num_recent_events)
                session.events = session.events[-count:] if count else []
        return session

    async def list_sessions(self, *, app_name: str, user_id: str | None = None) -> ListSessionsResponse:
        # No reversible identifiers or global enumeration index is persisted.
        raise NotImplementedError("Acceder por conversationId; no se permite enumerar conversaciones")

    async def delete_session(self, *, app_name: str, user_id: str, session_id: str) -> None:
        await self.store.client.delete(self._key(app_name, user_id, session_id))

    async def append_event(self, session: Session, event: Event) -> Event:
        if event.partial:
            return event
        key = self._key(session.app_name, session.user_id, session.id)
        # Serialize concurrent writers; read full history even if Runner loaded a filtered view.
        async with self.store.client.lock(f"{key}:lock", timeout=10, blocking_timeout=5):
            current = await self.get_session(app_name=session.app_name, user_id=session.user_id, session_id=session.id)
            if current is None:
                raise ValueError("La conversacion expiro")
            cleaned = safe_event(event)
            await super().append_event(current, cleaned)
            await self.store.client.set(key, self._payload(current), ex=self.ttl_seconds)
        # ADK needs the original event in this invocation; only the safe projection crosses Redis.
        return await super().append_event(session, event)


@dataclass(frozen=True)
class MemoryScope:
    app_name: str
    user_id: str
    customer_id: str
    link_status: Literal["active", "blocked", "unlinked"]


class RedisLongTermMemoryService(BaseMemoryService):
    """Bounded preferences, gated by trusted linkage per asynchronous invocation."""

    def __init__(self, store: RedisStore) -> None:
        self.store = store
        self._scope: ContextVar[MemoryScope | None] = ContextVar("h9_memory_scope", default=None)

    @asynccontextmanager
    async def for_turn(self, scope: MemoryScope) -> AsyncIterator[None]:
        if scope.link_status not in {"active", "blocked", "unlinked"} or not scope.customer_id:
            raise ValueError("Contexto de vinculo invalido")
        token = self._scope.set(scope)
        try:
            key = self.store.key("long", scope.app_name, scope.customer_id)
            async with self.store.client.lock(f"{key}:lock", timeout=120, blocking_timeout=5):
                if scope.link_status in {"blocked", "unlinked"}:
                    await self.store.client.delete(key)
                yield
        finally:
            self._scope.reset(token)

    def _key(self, app_name: str, user_id: str) -> str | None:
        scope = self._scope.get()
        if scope is None or scope.link_status != "active" or (scope.app_name, scope.user_id) != (app_name, user_id):
            return None
        return self.store.key("long", app_name, scope.customer_id)

    async def add_session_to_memory(self, session: Session) -> None:
        key = self._key(session.app_name, session.user_id)
        if key is None:
            return
        preferences = {k: v for k, v in safe_state(session.state).items() if k in LONG_TERM_FIELDS}
        if preferences:
            await self.store.client.set(key, json.dumps(preferences))

    async def search_memory(self, *, app_name: str, user_id: str, query: str) -> SearchMemoryResponse:
        key = self._key(app_name, user_id)
        raw = await self.store.client.get(key) if key else None
        if raw is None:
            return SearchMemoryResponse()
        # Query is neither logged nor persisted; this small profile is returned as structured data.
        return SearchMemoryResponse(
            memories=[MemoryEntry(content=types.Content(parts=[types.Part(text=raw.decode())]))]
        )


def create_memory_services(settings: Settings) -> tuple[RedisSessionService, RedisLongTermMemoryService]:
    if not settings.redis_url.get_secret_value():
        raise ValueError("Configurar AGENT_REDIS_URL")
    client = Redis.from_url(settings.redis_url.get_secret_value(), socket_timeout=5, socket_connect_timeout=5)
    store = RedisStore(client, settings.memory_key_secret.get_secret_value())
    return RedisSessionService(store, settings.session_ttl_minutes * 60), RedisLongTermMemoryService(store)
