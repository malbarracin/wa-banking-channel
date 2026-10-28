package com.bcra.gateway.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MessageNormalizer {
    private final ObjectMapper mapper;

    public MessageNormalizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode normalize(Map<String, String> parameters) {
        String messageId = required(parameters, "MessageSid");
        String from = required(parameters, "From");
        String to = required(parameters, "To");
        String body = required(parameters, "Body");
        if (!messageId.matches("SM[0-9a-fA-F]{32}")
                || !from.matches("whatsapp:\\+[1-9][0-9]{1,14}")
                || !to.matches("whatsapp:\\+[1-9][0-9]{1,14}")) {
            throw new IllegalArgumentException("Invalid inbound message");
        }
        ObjectNode event = mapper.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "MessageReceived");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        String correlationId = MDC.get("correlationId");
        event.put("correlationId", correlationId == null ? UUID.randomUUID().toString() : correlationId);
        event.put("conversationId", conversationId(from, to));
        event.put("messageId", messageId);
        event.putObject("payload").put("customerAddress", from).put("body", body);
        return event;
    }

    private String required(Map<String, String> parameters, String field) {
        String value = parameters.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid inbound message");
        }
        return value;
    }

    private String conversationId(String from, String to) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((from + "\n" + to).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
