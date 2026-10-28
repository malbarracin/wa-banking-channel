package com.bcra.orchestrator.messaging.consumer;

import com.bcra.orchestrator.dedup.DedupeService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class InboundEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(InboundEventConsumer.class);
    private final InboundEventValidator validator;
    private final DedupeService dedupe;

    public InboundEventConsumer(InboundEventValidator validator, DedupeService dedupe) {
        this.validator = validator;
        this.dedupe = dedupe;
    }

    @KafkaListener(id = "orchestrator-inbound", topics = "wa.message.received.v1",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        var message = validator.validate(record);
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", message.correlationId().toString());
        try {
            if (!dedupe.process(message)) {
                LOGGER.info("Duplicate inbound ignored partition={} offset={}", record.partition(), record.offset());
            }
            acknowledgment.acknowledge();
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }
}
