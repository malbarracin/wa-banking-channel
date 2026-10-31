package com.bcra.orchestrator.messaging.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
public class InboundEventValidator {
    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public InboundEventValidator(ObjectMapper mapper) {
        this.mapper = mapper;
        try (var input = getClass().getResourceAsStream("/contracts/events/wa.message.received.v1.schema.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing inbound schema");
            }
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(mapper.readTree(input),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load inbound schema", exception);
        }
    }

    public InboundMessage validate(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            throw new InvalidEventException();
        }
        try {
            var event = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(record.value());
            if (event == null || !schema.validate(event).isEmpty()
                    || !event.path("conversationId").asText().equals(record.key())) {
                throw new InvalidEventException();
            }
            requireHeader(record, "correlationId", event.path("correlationId").asText());
            requireHeader(record, "eventType", "MessageReceived");
            requireHeader(record, "eventVersion", "1");
            return new InboundMessage(event.path("messageId").asText(), event.path("conversationId").asText(),
                    UUID.fromString(event.path("correlationId").asText()),
                    event.path("payload").path("customerAddress").asText(),
                    event.path("payload").path("body").asText());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidEventException();
        }
    }

    private void requireHeader(ConsumerRecord<String, String> record, String name, String expected) {
        var header = record.headers().lastHeader(name);
        if (header == null || header.value() == null
                || !expected.equals(new String(header.value(), StandardCharsets.UTF_8))) {
            throw new InvalidEventException();
        }
    }
}
