package com.bcra.orchestrator.messaging.config;

import com.bcra.orchestrator.messaging.consumer.InvalidEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

/** Publica diagnóstico sin copiar contenido, claves ni headers no confiables. */
public class SafeDeadLetterRecoverer extends DeadLetterPublishingRecoverer {
    public static final String TOPIC = "wa.message.dlq.v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeDeadLetterRecoverer.class);
    private final ObjectMapper mapper;
    private final MeterRegistry registry;
    private final JsonSchema schema;

    public SafeDeadLetterRecoverer(KafkaTemplate<String, String> template, ObjectMapper mapper,
            MeterRegistry registry) {
        super(template, (record, exception) -> new TopicPartition(TOPIC, -1));
        this.mapper = mapper;
        this.registry = registry;
        try (var input = getClass().getResourceAsStream("/contracts/events/wa.message.dlq.v1.schema.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing DLQ schema");
            }
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(mapper.readTree(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load DLQ schema", exception);
        }
        setFailIfSendResultIsError(true);
        setLogRecoveryRecord(false);
        setExceptionHeadersCreator((headers, exception, isKey, names) -> {
            boolean invalid = false;
            Throwable cause = exception;
            for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
                invalid |= cause instanceof InvalidEventException
                        || cause instanceof jakarta.validation.ValidationException;
            }
            headers.add("errorCode", bytes(invalid ? "SCHEMA_VALIDATION_FAILED" : "RETRIES_EXHAUSTED"));
        });
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
            Exception exception) {
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", correlationId(record));
        try {
            super.accept(record, consumer, exception);
            registry.counter("orchestrator.kafka.dlq.published").increment();
            LOGGER.warn("Kafka record recovered to DLQ partition={} offset={}", record.partition(), record.offset());
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }

    @Override
    protected ProducerRecord<Object, Object> createProducerRecord(ConsumerRecord<?, ?> record,
            TopicPartition destination, Headers headers, byte[] key, byte[] value) {
        String identifier = record.topic() + ":" + record.partition() + ":" + record.offset();
        String correlation = correlationId(record);
        // A transport-derived identifier avoids leaking an unvalidated Kafka key.
        String conversation = UUID.nameUUIDFromBytes(bytes(identifier)).toString();
        String code = new String(headers.lastHeader("errorCode").value(), StandardCharsets.UTF_8);
        var event = mapper.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "DeadLetter");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", correlation);
        event.put("conversationId", conversation);
        event.put("messageId", identifier);
        var payload = event.putObject("payload");
        payload.put("originalTopic", record.topic());
        payload.put("originalPartition", record.partition());
        payload.put("originalOffset", record.offset());
        payload.put("originalValueBase64", "");
        payload.put("errorCode", code);
        if (!schema.validate(event).isEmpty()) {
            throw new IllegalStateException("DLQ contract validation failed");
        }
        var safeHeaders = new RecordHeaders();
        safeHeaders.add("correlationId", bytes(correlation));
        safeHeaders.add("eventType", bytes("DeadLetter"));
        safeHeaders.add("eventVersion", bytes("1"));
        safeHeaders.add("errorCode", bytes(code));
        return new ProducerRecord<>(TOPIC, null, conversation, event.toString(), safeHeaders);
    }

    private static String correlationId(ConsumerRecord<?, ?> record) {
        var header = record.headers().lastHeader("correlationId");
        if (header != null && header.value() != null && header.value().length == 36) {
            String candidate = new String(header.value(), StandardCharsets.UTF_8);
            if (candidate.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                return candidate;
            }
        }
        return UUID.nameUUIDFromBytes(bytes(record.topic() + ":" + record.partition() + ":" + record.offset()))
                .toString();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
