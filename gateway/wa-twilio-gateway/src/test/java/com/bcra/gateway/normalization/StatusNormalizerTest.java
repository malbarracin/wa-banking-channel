package com.bcra.gateway.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusNormalizerTest {
    private static final String CORRELATION = "11111111-1111-4111-8111-111111111111";
    private final StatusNormalizer normalizer = new StatusNormalizer(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"queued", "sent", "delivered", "read", "failed"})
    void normalizesContractStatesUsingCustomerBankConversationKey(String status) throws Exception {
        var event = normalizer.normalize(form(status), CORRELATION);
        String expectedConversation = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("whatsapp:+15005550007\nwhatsapp:+15005550006".getBytes(StandardCharsets.UTF_8)));
        assertThat(event.path("conversationId").asText()).isEqualTo(expectedConversation);
        assertThat(event.path("messageId").asText()).isEqualTo("SM00000000000000000000000000000000");
        assertThat(event.path("eventType").asText()).isEqualTo("MessageStatus");
        assertThat(event.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.path("correlationId").asText()).isEqualTo(CORRELATION);
        assertThat(event.path("payload").path("status").asText()).isEqualTo(status);
        assertThat(event.path("payload").size()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"MessageSid,invalid", "MessageStatus,unknown", "From,invalid", "To,invalid",
            "ErrorCode,sensitive details"})
    void rejectsInvalidCallbackFields(String field, String value) {
        var invalid = new HashMap<>(form("sent"));
        invalid.put(field, value);
        assertThatThrownBy(() -> normalizer.normalize(invalid, CORRELATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MessageSid", "MessageStatus", "From", "To"})
    void rejectsMissingRequiredFields(String field) {
        var incomplete = new HashMap<>(form("sent"));
        incomplete.remove(field);
        assertThatThrownBy(() -> normalizer.normalize(incomplete, CORRELATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, String> form(String status) {
        return Map.of("MessageSid", "SM00000000000000000000000000000000", "MessageStatus", status,
                "From", "whatsapp:+15005550006", "To", "whatsapp:+15005550007");
    }
}
