package com.bcra.orchestrator.messaging.producer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.bcra.orchestrator.outbox.model.OutboxEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Enqueues only; the caller owns the deduplicated state/turn transaction. */
@Service
public class OutboundEventPublisher {
    public static final String TOPIC = "wa.message.ready-to-send.v1";
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    private final JsonSchema schema;
    private final JsonSchema deadLetterSchema;

    public OutboundEventPublisher(ObjectMapper mapper, EntityManager entityManager) {
        this.mapper = mapper;
        this.entityManager = entityManager;
        schema = load("wa.message.ready-to-send.v1.schema.json");
        deadLetterSchema = load("wa.message.dlq.v1.schema.json");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(String conversationId, UUID correlationId, String messageId,
            String customerAddress, String body) {
        if (conversationId == null || messageId == null || conversationId.length() > 128
                || messageId.length() > 128 || correlationId == null) {
            throw new IllegalArgumentException("Invalid outbound identifiers");
        }
        UUID eventId = UUID.nameUUIDFromBytes(("MessageReadyToSend:" + messageId)
                .getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        var event = mapper.createObjectNode();
        event.put("eventId", eventId.toString());
        event.put("eventType", "MessageReadyToSend");
        event.put("eventVersion", 1);
        event.put("occurredAt", now.toString());
        event.put("correlationId", correlationId.toString());
        event.put("conversationId", conversationId);
        event.put("messageId", messageId);
        var payload = event.putObject("payload");
        payload.put("customerAddress", customerAddress);
        payload.put("body", body);
        validate(event, false);
        entityManager.persist(new OutboxEvent(eventId, conversationId, correlationId, messageId,
                "MessageReadyToSend", 1, TOPIC, event.toString(), now));
        return eventId;
    }

    public JsonNode validate(String json) {
        try {
            JsonNode event = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature
                    .FAIL_ON_TRAILING_TOKENS).readTree(json);
            validate(event, false);
            return event;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid outbound event");
        }
    }

    public String deadLetter(OutboxEvent row, String code) {
        var event = mapper.createObjectNode();
        event.put("eventId", UUID.nameUUIDFromBytes(("DeadLetter:" + row.getEventId())
                .getBytes(StandardCharsets.UTF_8)).toString());
        event.put("eventType", "DeadLetter");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", row.getCorrelationId().toString());
        event.put("conversationId", row.getConversationId());
        event.put("messageId", row.getMessageId());
        var payload = event.putObject("payload");
        payload.put("originalTopic", TOPIC);
        // No Kafka coordinates exist before publication. See OUTBOUND.md for the outbox pointer.
        payload.put("originalPartition", 0);
        payload.put("originalOffset", 0);
        payload.put("originalValueBase64", "");
        payload.put("errorCode", code);
        validate(event, true);
        return event.toString();
    }

    private void validate(JsonNode event, boolean deadLetter) {
        if (event == null || !(deadLetter ? deadLetterSchema : schema).validate(event).isEmpty()) {
            throw new IllegalArgumentException("Invalid outbound event");
        }
    }

    private JsonSchema load(String name) {
        try (var input = getClass().getResourceAsStream("/contracts/events/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing outbound contract");
            }
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(mapper.readTree(input),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load outbound contract");
        }
    }
}
