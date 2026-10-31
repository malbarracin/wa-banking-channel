package com.bcra.orchestrator.conversation.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.orchestrator.conversation.model.ConversationState;
import com.bcra.orchestrator.conversation.repository.ConversationStateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final String INITIAL_STATE = "INITIAL";
    private static final String CORRELATION_ID = "correlationId";
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_STATE_LENGTH = 64;

    private final ConversationStateRepository conversations;

    /** Loads persisted context without resetting progress when a new turn arrives. */
    @Transactional
    public ConversationState loadOrCreate(String conversationId, UUID correlationId) {
        validateText(conversationId, MAX_IDENTIFIER_LENGTH);
        return withCorrelation(correlationId, () -> conversations.findById(conversationId)
                .orElseGet(() -> conversations.save(new ConversationState(
                        conversationId, correlationId, INITIAL_STATE, Instant.now()))));
    }

    /** Joins the caller's transaction so state, deduplication and outbox can commit together. */
    @Transactional
    public ConversationState updateState(String conversationId, UUID correlationId, String state, String messageId) {
        validateText(conversationId, MAX_IDENTIFIER_LENGTH);
        validateText(state, MAX_STATE_LENGTH);
        validateText(messageId, MAX_IDENTIFIER_LENGTH);
        return withCorrelation(correlationId, () -> {
            ConversationState conversation = conversations.findById(conversationId)
                    .orElseGet(() -> new ConversationState(
                            conversationId, correlationId, INITIAL_STATE, Instant.now()));
            conversation.updateState(state, messageId, correlationId, Instant.now());
            return conversations.save(conversation);
        });
    }

    private <T> T withCorrelation(UUID correlationId, Supplier<T> operation) {
        Objects.requireNonNull(correlationId, "correlationId");
        String previous = MDC.get(CORRELATION_ID);
        MDC.put(CORRELATION_ID, correlationId.toString());
        try {
            return operation.get();
        } finally {
            if (previous == null) {
                MDC.remove(CORRELATION_ID);
            } else {
                MDC.put(CORRELATION_ID, previous);
            }
        }
    }

    private void validateText(String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid technical context field");
        }
    }
}
