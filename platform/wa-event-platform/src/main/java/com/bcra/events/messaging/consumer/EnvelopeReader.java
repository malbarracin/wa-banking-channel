package com.bcra.events.messaging.consumer;

import com.bcra.events.domain.EventEnvelope;
import com.bcra.events.observability.CorrelationMdc;
import com.bcra.events.observability.EventMetrics;
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
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Component
public class EnvelopeReader {
    private final ObjectMapper mapper;
    private final SchemaValidator validator;
    private final EventMetrics metrics;

    public EnvelopeReader(ObjectMapper mapper, SchemaValidator validator) {
        this(mapper, validator, new EventMetrics(new SimpleMeterRegistry()));
    }

    @Autowired
    public EnvelopeReader(ObjectMapper mapper, SchemaValidator validator, EventMetrics metrics) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
        this.metrics = metrics;
    }

    public void read(ConsumerRecord<String, String> record, Consumer<EventEnvelope> action) {
        var sample = metrics.start();
        String eventType = "unknown";
        try {
            JsonNode json = mapper.readTree(record.value());
            if (json == null || !json.isObject()) {
                throw new SchemaValidationException("Invalid event JSON");
            }
            EventEnvelope event = mapper.treeToValue(json, EventEnvelope.class);
            validator.validate(json, event.eventType(), event.eventVersion());
            eventType = event.eventType().name();
            if (!event.conversationId().equals(record.key())
                || !header(record, "correlationId").equals(event.correlationId())
                || !header(record, "eventType").equals(event.eventType().name())
                || !header(record, "eventVersion").equals(Integer.toString(event.eventVersion()))) {
                throw new SchemaValidationException("Record key or headers do not match envelope");
            }
            try (CorrelationMdc ignored = new CorrelationMdc(event)) {
                action.accept(event);
            }
            metrics.consumed(record.topic(), eventType, "success");
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            metrics.invalid("consume", "json");
            metrics.consumed(record.topic(), eventType, "invalid");
            throw new SchemaValidationException("Invalid event JSON", e);
        } catch (SchemaValidationException e) {
            metrics.invalid("consume", "schema");
            metrics.consumed(record.topic(), eventType, "invalid");
            throw e;
        } catch (RuntimeException e) {
            metrics.consumed(record.topic(), eventType, "error");
            throw e;
        } finally {
            metrics.stop(sample, record.topic());
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
