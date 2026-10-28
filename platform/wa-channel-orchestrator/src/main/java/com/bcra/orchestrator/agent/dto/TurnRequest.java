package com.bcra.orchestrator.agent.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TurnRequest(UUID correlationId, String conversationId, String messageId,
        String customerAddress, String body, Context context) {

    public TurnRequest {
        Objects.requireNonNull(correlationId, "correlationId");
        requireText(conversationId);
        requireText(messageId);
        requireText(customerAddress);
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(context, "context");
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("Invalid turn identifier");
        }
    }

    @Override
    public String toString() {
        return "TurnRequest[redacted]";
    }

    public record Context(Map<String, Object> technicalContext, List<Map<String, Object>> history) {
        public Context {
            technicalContext = Map.copyOf(technicalContext);
            history = history.stream().map(Map::copyOf).toList();
        }

        @Override
        public String toString() {
            return "Context[redacted]";
        }
    }
}
