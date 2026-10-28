package com.bcra.orchestrator.messaging.consumer;

import java.util.UUID;

/** Turno validado, entregado a handlers síncronos dentro de la transacción de deduplicación. */
public record InboundMessage(String messageId, String conversationId, UUID correlationId,
        String customerAddress, String body) {
    @Override
    public String toString() {
        return "InboundMessage[redacted]";
    }
}
