package com.bcra.events.messaging.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev", "test"})
public class ReferenceMessageReceivedListener {
    private final EnvelopeReader reader;
    private final IdempotencyGuard guard;

    public ReferenceMessageReceivedListener(EnvelopeReader reader, IdempotencyGuard guard) {
        this.reader = reader;
        this.guard = guard;
    }

    @KafkaListener(topics = "${wa.topics.received}", groupId = "${wa.topics.consumer-groups.reference}")
    public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        reader.read(record, event -> guard.first(event));
        acknowledgment.acknowledge();
    }
}
