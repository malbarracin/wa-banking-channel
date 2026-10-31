package com.bcra.orchestrator.conversation.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversation_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationState {

    @Id
    @Column(name = "conversation_id", length = 128, nullable = false)
    private String conversationId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "state", nullable = false, length = 64)
    private String state;

    @Column(name = "last_message_id", length = 128)
    private String lastMessageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public ConversationState(String conversationId, UUID correlationId, String state, Instant updatedAt) {
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.state = Objects.requireNonNull(state, "state");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public void recordMessage(String messageId, UUID correlationId, Instant updatedAt) {
        this.lastMessageId = Objects.requireNonNull(messageId, "messageId");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public void updateState(String state, String messageId, UUID correlationId, Instant updatedAt) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        this.state = state;
        recordMessage(messageId, correlationId, updatedAt);
    }
}
