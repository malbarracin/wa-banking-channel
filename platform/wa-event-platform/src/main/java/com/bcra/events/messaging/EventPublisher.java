package com.bcra.events.messaging;

import com.bcra.events.domain.EventEnvelope;
import com.bcra.events.observability.EventMetrics;
import com.bcra.events.schema.SchemaValidationException;
import com.bcra.events.schema.SchemaValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(EventPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final SchemaValidator validator;
    private final MeterRegistry metrics;
    private final EventMetrics eventMetrics;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                          SchemaValidator validator, MeterRegistry metrics, EventMetrics eventMetrics) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.validator = validator;
        this.metrics = metrics;
        this.eventMetrics = eventMetrics;
    }

    public CompletableFuture<SendResult<String, String>> publicar(String topic, EventEnvelope input) {
        EventEnvelope event;
        String json;
        try {
            if (input == null || topic == null) {
                throw new SchemaValidationException("Missing topic or event");
            }
            event = input.completed();
            if (event.correlationId() == null || event.correlationId().isBlank()
                || event.conversationId() == null || event.conversationId().isBlank()
                || event.messageId() == null || event.messageId().isBlank()
                || !topic.endsWith(".v" + event.eventVersion())) {
                throw new SchemaValidationException("Invalid event identifiers or topic version");
            }
            json = mapper.writeValueAsString(event);
            validator.validate(mapper.readTree(json), event.eventType(), event.eventVersion());
        } catch (JsonProcessingException e) {
            eventMetrics.invalid("produce", "serialization");
            throw new SchemaValidationException("Cannot serialize event", e);
        } catch (SchemaValidationException e) {
            eventMetrics.invalid("produce", "schema");
            throw e;
        }
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.conversationId(), json);
        record.headers().add("correlationId", event.correlationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.eventType().name().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", Integer.toString(event.eventVersion()).getBytes(StandardCharsets.UTF_8));
        try {
            return kafka.send(record).whenComplete((result, error) -> {
                if (error != null) {
                    metrics.counter("wa_events_publish_failed_total").increment();
                    LOG.warn("Event publication failed");
                } else {
                    eventMetrics.published(topic, event.eventType().name());
                }
            });
        } catch (RuntimeException e) {
            metrics.counter("wa_events_publish_failed_total").increment();
            LOG.warn("Event publication failed");
            throw new EventPublicationException("Event publication failed", e);
        }
    }
}
