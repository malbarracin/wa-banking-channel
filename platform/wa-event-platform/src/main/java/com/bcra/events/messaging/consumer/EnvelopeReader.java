package com.bcra.events.messaging.consumer;

import com.bcra.events.domain.EventEnvelope;
import com.bcra.events.observability.CorrelationMdc;
import com.bcra.events.schema.SchemaValidationException;
import com.bcra.events.schema.SchemaValidator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

@Component
public class EnvelopeReader {
    private final ObjectMapper mapper;
    private final SchemaValidator validator;

    public EnvelopeReader(ObjectMapper mapper, SchemaValidator validator) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    public void read(ConsumerRecord<String, String> record, Consumer<EventEnvelope> action) {
        try {
            JsonNode json = mapper.readTree(record.value());
            if (json == null || !json.isObject()) {
                throw new SchemaValidationException("Invalid event JSON");
            }
            EventEnvelope event = mapper.treeToValue(json, EventEnvelope.class);
            validator.validate(json, event.eventType(), event.eventVersion());
            String expectedTopic = switch (event.eventType()) {
                case MessageReceived -> "wa.message.received.v";
                case MessageReadyToSend -> "wa.message.ready-to-send.v";
                case MessageStatus -> "wa.message.status.v";
            };
            if (!record.topic().equals(expectedTopic + event.eventVersion())
                || !event.conversationId().equals(record.key())
                || !header(record, "correlationId").equals(event.correlationId())
                || !header(record, "eventType").equals(event.eventType().name())
                || !header(record, "eventVersion").equals(Integer.toString(event.eventVersion()))) {
                throw new SchemaValidationException("Record key or headers do not match envelope");
            }
            try (CorrelationMdc ignored = new CorrelationMdc(event)) {
                action.accept(event);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            throw new SchemaValidationException("Invalid event JSON", e);
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            throw new SchemaValidationException("Missing event header");
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
