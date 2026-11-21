package com.bcra.events.messaging.consumer;

import com.bcra.events.domain.EventEnvelope;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyGuard {
    private final InMemoryIdempotencyStore store;
    public IdempotencyGuard(InMemoryIdempotencyStore store) { this.store = store; }
    public boolean first(EventEnvelope event) { return store.first(event); }
}
