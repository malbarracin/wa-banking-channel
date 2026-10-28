package com.bcra.gateway.outbound;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

public class OutboundDeadLetterPublisher implements ConsumerRecordRecoverer {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final OutboundSchemas schemas;
    private final MeterRegistry metrics;

    public OutboundDeadLetterPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
            OutboundSchemas schemas, MeterRegistry metrics) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.schemas = schemas;
        this.metrics = metrics;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && !(cause instanceof OutboundException)) {
            cause = cause.getCause();
        }
        String code = "RETRIES_EXHAUSTED";
        String providerCode = "OUTBOUND_FAILURE";
        if (cause instanceof OutboundException failure) {
            code = switch (failure.kind()) {
                case INVALID -> "SCHEMA_VALIDATION_FAILED";
                case PERMANENT, AMBIGUOUS -> "NON_RETRYABLE_ERROR";
                case TRANSIENT -> "RETRIES_EXHAUSTED";
            };
            providerCode = failure.code();
        }
        String identifier = record.topic() + ":" + record.partition() + ":" + record.offset();
        String correlationId = UUID.nameUUIDFromBytes(identifier.getBytes(StandardCharsets.UTF_8)).toString();
        var correlation = record.headers().lastHeader("correlationId");
        if (correlation != null && correlation.value() != null) {
            String candidate = new String(correlation.value(), StandardCharsets.UTF_8);
            if (candidate.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                correlationId = candidate;
            }
        }
        String conversation = record.key() instanceof String key && !key.isBlank() && key.length() <= 256
                ? key : identifier;
        var event = mapper.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "DeadLetter");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", correlationId);
        event.put("conversationId", conversation);
        event.put("messageId", identifier);
        var payload = event.putObject("payload");
        payload.put("originalTopic", record.topic());
        payload.put("originalPartition", record.partition());
        payload.put("originalOffset", record.offset());
        String original = record.value() == null ? "" : record.value().toString();
        payload.put("originalValueBase64", Base64.getEncoder()
                .encodeToString(original.getBytes(StandardCharsets.UTF_8)));
        payload.put("errorCode", code);
        schemas.validate(OutboundSchemas.DLQ, event.toString());
        var output = new ProducerRecord<String, String>(OutboundSchemas.DLQ, conversation, event.toString());
        output.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        output.headers().add("eventType", "DeadLetter".getBytes(StandardCharsets.UTF_8));
        output.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        output.headers().add("errorCode", code.getBytes(StandardCharsets.UTF_8));
        output.headers().add("providerErrorCode", providerCode.getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(output).get(10, TimeUnit.SECONDS);
            metrics.counter("gateway.outbound.dlq", "code", code).increment();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DLQ publication interrupted");
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("DLQ publication failed");
        }
    }
}
