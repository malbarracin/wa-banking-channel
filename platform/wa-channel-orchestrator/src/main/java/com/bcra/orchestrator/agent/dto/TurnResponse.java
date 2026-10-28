package com.bcra.orchestrator.agent.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TurnResponse(UUID correlationId, String conversationId, String messageId,
        Reply reply, Boolean escalate, List<String> toolsUsed) {

    public TurnResponse {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(escalate, "escalate");
        toolsUsed = List.copyOf(toolsUsed);
    }

    @Override
    public String toString() {
        return "TurnResponse[redacted]";
    }

    public record Reply(String body) {
        public Reply {
            Objects.requireNonNull(body, "body");
        }

        @Override
        public String toString() {
            return "Reply[redacted]";
        }
    }
}
