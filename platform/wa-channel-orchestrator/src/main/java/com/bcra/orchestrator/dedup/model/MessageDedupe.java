package com.bcra.orchestrator.dedup.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message_dedupe")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageDedupe {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "message_id", length = 256, nullable = false, unique = true)
    private String messageId;

    @Column(name = "conversation_id", length = 256, nullable = false)
    private String conversationId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public MessageDedupe(UUID id, String messageId, String conversationId, UUID correlationId,
            String eventType, Instant processedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
    }
}
