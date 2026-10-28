package com.bcra.orchestrator.outbox.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "conversation_id", length = 128, nullable = false)
    private String conversationId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "message_id", length = 128, nullable = false)
    private String messageId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(nullable = false, length = 255)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent(UUID eventId, String conversationId, UUID correlationId, String messageId,
            String eventType, int eventVersion, String topic, String payload, Instant createdAt) {
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.eventVersion = eventVersion;
        this.topic = Objects.requireNonNull(topic, "topic");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
    }
}
