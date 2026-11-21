package com.bcra.events.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(UUID eventId, EventType eventType, int eventVersion, Instant occurredAt,
                            String correlationId, String conversationId, String messageId, JsonNode payload) {
    public EventEnvelope completed() {
        return new EventEnvelope(eventId == null ? UUID.randomUUID() : eventId, eventType, eventVersion,
            occurredAt == null ? Instant.now() : occurredAt, correlationId, conversationId, messageId, payload);
    }
}
