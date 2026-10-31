package com.bcra.events.messaging.consumer;

import com.bcra.events.messaging.schema.EventSchemaValidator;
import com.bcra.events.messaging.schema.InvalidEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventConsumer {
    private final EventSchemaValidator validator;
    private final ObjectMapper mapper;

    /** Effect must atomically deduplicate messageId with its persistence before returning. */
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment,
            Consumer<JsonNode> effect) {
        validator.validate(record.topic(), record.value());
        try {
            var event = mapper.readTree(record.value());
            if (!Objects.equals(record.key(), event.path("conversationId").asText())) {
                throw new InvalidEventException();
            }
            var header = record.headers().lastHeader("correlationId");
            String correlationId = event.path("correlationId").asText();
            if (header == null || header.value() == null
                    || !correlationId.equals(new String(header.value(), StandardCharsets.UTF_8))) {
                throw new InvalidEventException();
            }
            String previous = MDC.get("correlationId");
            try {
                MDC.put("correlationId", correlationId);
                effect.accept(event);
                acknowledgment.acknowledge();
            } finally {
                if (previous == null) {
                    MDC.remove("correlationId");
                } else {
                    MDC.put("correlationId", previous);
                }
            }
        } catch (JsonProcessingException exception) {
            throw new InvalidEventException();
        }
    }
}
