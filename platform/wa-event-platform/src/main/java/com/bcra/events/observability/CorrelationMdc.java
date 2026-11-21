package com.bcra.events.observability;

import com.bcra.events.domain.EventEnvelope;
import org.slf4j.MDC;

public final class CorrelationMdc implements AutoCloseable {
    public CorrelationMdc(EventEnvelope event) {
        MDC.put("correlationId", event.correlationId());
        MDC.put("conversationId", event.conversationId());
        MDC.put("messageId", event.messageId());
        MDC.put("eventType", event.eventType().name());
    }

    @Override
    public void close() {
        MDC.remove("correlationId");
        MDC.remove("conversationId");
        MDC.remove("messageId");
        MDC.remove("eventType");
    }
}
