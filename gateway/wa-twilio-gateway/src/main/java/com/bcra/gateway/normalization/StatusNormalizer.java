package com.bcra.gateway.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StatusNormalizer {
    private final ObjectMapper mapper;

    public StatusNormalizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode normalize(Map<String, String> form, String correlationId) {
        String sid = form.getOrDefault("MessageSid", "");
        String status = form.getOrDefault("MessageStatus", "");
        String from = form.getOrDefault("From", "");
        String to = form.getOrDefault("To", "");
        if (!sid.matches("SM[0-9a-fA-F]{32}")
                || !Set.of("queued", "sent", "delivered", "read", "failed").contains(status)
                || !from.matches("whatsapp:\\+[1-9][0-9]{1,14}")
                || !to.matches("whatsapp:\\+[1-9][0-9]{1,14}")) {
            throw new IllegalArgumentException("Invalid status callback");
        }
        ObjectNode event = mapper.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "MessageStatus");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", correlationId);
        // Outbound addresses are reversed relative to the inbound customer/bank pair.
        try {
            event.put("conversationId", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((to + "\n" + from).getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        event.put("messageId", sid);
        ObjectNode payload = event.putObject("payload");
        payload.put("status", status);
        String error = form.get("ErrorCode");
        if (error != null && !error.isBlank()) {
            if (!error.matches("[A-Z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("Invalid error code");
            }
            payload.put("errorCode", error);
        }
        return event;
    }
}
