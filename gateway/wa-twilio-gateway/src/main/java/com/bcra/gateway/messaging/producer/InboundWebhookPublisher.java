package com.bcra.gateway.messaging.producer;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Gateway-local boundary: completion means the normalized event was accepted by Kafka.
 * Implementations must return immediately, complete asynchronously, and preserve MessageSid as messageId.
 * An uncertain timeout can be delivered again: downstream consumers must deduplicate by messageId.
 */
@FunctionalInterface
public interface InboundWebhookPublisher {
    CompletionStage<Void> publish(Map<String, String> parameters);
}
