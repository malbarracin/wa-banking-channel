package com.bcra.events.messaging.consumer;

import com.bcra.events.domain.EventEnvelope;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Example only: entries are lost on restart and are not transactional with business effects. */
@Component
public class InMemoryIdempotencyStore {
    private final Cache<String, Boolean> events = Caffeine.newBuilder().maximumSize(100_000)
        .expireAfterWrite(Duration.ofHours(24)).build();
    private final Cache<String, Boolean> messages = Caffeine.newBuilder().maximumSize(100_000)
        .expireAfterWrite(Duration.ofHours(24)).build();

    public synchronized boolean first(EventEnvelope event) {
        String eventId = event.eventId().toString();
        if (events.getIfPresent(eventId) != null || messages.getIfPresent(event.messageId()) != null) {
            return false;
        }
        events.put(eventId, true);
        messages.put(event.messageId(), true);
        return true;
    }
}
