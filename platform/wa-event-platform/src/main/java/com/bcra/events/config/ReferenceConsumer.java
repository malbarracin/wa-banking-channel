package com.bcra.events.config;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Local subscription example; it performs no business effect. */
@Component
@Profile({"local", "dev", "test"})
public class ReferenceConsumer {
    @KafkaListener(topics = "${wa.topics.received}", groupId = "${wa.topics.consumer-groups.reference}")
    public void receive(String value, Acknowledgment acknowledgment) {
        acknowledgment.acknowledge();
    }
}
