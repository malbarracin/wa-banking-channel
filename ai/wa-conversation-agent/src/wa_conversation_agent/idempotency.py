"""Deduplicación por messageId / X-Idempotency-Key alineada a H0.2 (TTL 7 días)."""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import time
from dataclasses import dataclass
from typing import Any, Literal, Protocol

from redis.asyncio import Redis

from wa_conversation_agent.models import TurnResponse

Outcome = Literal["acquired", "in_progress", "done"]


@dataclass(frozen=True)
class BeginResult:
    outcome: Outcome
    response: TurnResponse | None = None


class IdempotencyStore(Protocol):
    async def begin(self, key: str, fingerprint: str = "") -> BeginResult: ...

    async def complete(self, key: str, response: TurnResponse) -> None: ...


class MemoryIdempotencyStore:
    """Almacén en proceso para tests y entornos sin Redis."""

    def __init__(self, ttl_seconds: int = 7 * 24 * 3600) -> None:
        self.ttl_seconds = ttl_seconds
        self._lock = asyncio.Lock()
        self._entries: dict[str, dict[str, Any]] = {}
        self._expires: dict[str, float] = {}

    async def begin(self, key: str, fingerprint: str = "") -> BeginResult:
        async with self._lock:
            if self._expires.get(key, 0) <= time.monotonic():
                self._entries.pop(key, None)
            entry = self._entries.get(key)
            if entry is None:
                self._entries[key] = {"status": "in_progress", "fingerprint": fingerprint}
                self._expires[key] = time.monotonic() + self.ttl_seconds
                return BeginResult("acquired")
            if entry.get("fingerprint") != fingerprint:
                return BeginResult("in_progress")
            if entry["status"] == "done":
                return BeginResult("done", TurnResponse.model_validate(entry["response"]))
            return BeginResult("in_progress")

    async def complete(self, key: str, response: TurnResponse) -> None:
        async with self._lock:
            self._entries[key] = {
                "status": "done",
                "fingerprint": self._entries[key]["fingerprint"],
                "response": response.model_dump(mode="json"),
            }
            self._expires[key] = time.monotonic() + self.ttl_seconds


class RedisIdempotencyStore:
    """Cache Redis: in_progress → 409; done → mismo TurnResponse; TTL ~7 días."""

    def __init__(self, client: Redis, key_secret: str, ttl_seconds: int = 7 * 24 * 3600) -> None:
        if len(key_secret) < 32:
            raise ValueError("Configurar AGENT_MEMORY_KEY_SECRET con al menos 32 caracteres")
        if ttl_seconds < 1:
            raise ValueError("TTL de idempotencia debe ser positivo")
        self.client = client
        self._secret = key_secret.encode()
        self.ttl_seconds = ttl_seconds

    def _key(self, idempotency_key: str) -> str:
        digest = hmac.new(self._secret, idempotency_key.encode(), hashlib.sha256).hexdigest()
        return f"h9:v1:idem:{digest}"

    async def begin(self, key: str, fingerprint: str = "") -> BeginResult:
        redis_key = self._key(key)
        marker = json.dumps({"status": "in_progress", "fingerprint": fingerprint})
        created = await self.client.set(redis_key, marker, ex=self.ttl_seconds, nx=True)
        if created:
            return BeginResult("acquired")
        raw = await self.client.get(redis_key)
        if raw is None:
            # Expiró entre el SET fallido y el GET: reintentar adquisición.
            created = await self.client.set(redis_key, marker, ex=self.ttl_seconds, nx=True)
            if created:
                return BeginResult("acquired")
            raw = await self.client.get(redis_key)
        if raw is None:
            return BeginResult("in_progress")
        payload = json.loads(raw)
        if payload.get("fingerprint") != fingerprint:
            return BeginResult("in_progress")
        if payload.get("status") == "done" and "response" in payload:
            return BeginResult("done", TurnResponse.model_validate(payload["response"]))
        return BeginResult("in_progress")

    async def complete(self, key: str, response: TurnResponse) -> None:
        raw = await self.client.get(self._key(key))
        if raw is None:
            raise ValueError("La reserva de idempotencia expiro")
        payload = json.dumps(
            {
                "status": "done",
                "response": response.model_dump(mode="json"),
                "fingerprint": json.loads(raw)["fingerprint"],
            }
        )
        await self.client.set(self._key(key), payload, ex=self.ttl_seconds)
