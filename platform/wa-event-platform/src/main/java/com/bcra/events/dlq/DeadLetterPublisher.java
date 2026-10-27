package com.bcra.events.dlq;

import com.bcra.events.messaging.schema.EventSchemaValidator;
import com.bcra.events.messaging.schema.InvalidEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterPublisher extends DeadLetterPublishingRecoverer {
    public static final String TOPIC = "wa.message.dlq.v1";
    private final ObjectMapper mapper;
    private final EventSchemaValidator validator;

    public DeadLetterPublisher(KafkaTemplate<String, String> template, ObjectMapper mapper,
            EventSchemaValidator validator) {
        super(template, (record, exception) -> new TopicPartition(TOPIC, -1));
        this.mapper = mapper;
        this.validator = validator;
        setFailIfSendResultIsError(true);
        setLogRecoveryRecord(false);
        setExceptionHeadersCreator((headers, exception, isKey, headerNames) -> {
            Throwable cause = exception;
            while (cause.getCause() != null && !(cause instanceof InvalidEventException)) {
                cause = cause.getCause();
            }
            String code = cause instanceof InvalidEventException ? "SCHEMA_VALIDATION_FAILED" : "RETRIES_EXHAUSTED";
            headers.add("errorCode", code.getBytes(StandardCharsets.UTF_8));
        });
    }

    @Override
    protected ProducerRecord<Object, Object> createProducerRecord(ConsumerRecord<?, ?> record,
            TopicPartition destination, Headers headers, byte[] key, byte[] value) {
        // Invalid JSON cannot supply trusted envelope fields; transport coordinates identify it for replay.
        String identifier = record.topic() + ":" + record.partition() + ":" + record.offset();
        String correlationId = UUID.nameUUIDFromBytes(identifier.getBytes(StandardCharsets.UTF_8)).toString();
        var originalCorrelation = record.headers().lastHeader("correlationId");
        if (originalCorrelation != null && originalCorrelation.value() != null) {
            String candidate = new String(originalCorrelation.value(), StandardCharsets.UTF_8);
            if (candidate.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                correlationId = candidate;
            }
        }
        String conversationId = record.key() instanceof String text && !text.isBlank() && text.length() <= 256
                ? text : identifier;
        String errorCode = new String(headers.lastHeader("errorCode").value(), StandardCharsets.UTF_8);
        var event = mapper.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "DeadLetter");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", correlationId);
        event.put("conversationId", conversationId);
        event.put("messageId", identifier);
        var payload = event.putObject("payload");
        payload.put("originalTopic", record.topic());
        payload.put("originalPartition", record.partition());
        payload.put("originalOffset", record.offset());
        byte[] original = record.value() == null ? new byte[0]
                : record.value().toString().getBytes(StandardCharsets.UTF_8);
        payload.put("originalValueBase64", Base64.getEncoder().encodeToString(original));
        payload.put("errorCode", errorCode);
        String serialized = event.toString();
        validator.validate(TOPIC, serialized);
        var safeHeaders = new RecordHeaders();
        safeHeaders.add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        safeHeaders.add("eventType", "DeadLetter".getBytes(StandardCharsets.UTF_8));
        safeHeaders.add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        safeHeaders.add("errorCode", errorCode.getBytes(StandardCharsets.UTF_8));
        return new ProducerRecord<>(TOPIC, null, conversationId, serialized, safeHeaders);
    }
}
