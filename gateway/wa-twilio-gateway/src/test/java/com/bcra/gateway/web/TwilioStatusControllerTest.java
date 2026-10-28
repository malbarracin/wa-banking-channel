package com.bcra.gateway.web;

import com.bcra.gateway.messaging.producer.StatusEventPublisher;
import com.bcra.gateway.normalization.StatusNormalizer;
import com.bcra.gateway.security.AntiReplayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TwilioStatusControllerTest {
    private static final String TOKEN = "synthetic-test-token";
    private static final String URL = "https://bank.example/webhooks/twilio/status";
    private static final String CORRELATION = "11111111-1111-4111-8111-111111111111";
    private final StatusEventPublisher publisher = mock(StatusEventPublisher.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        mvc = MockMvcBuilders.standaloneSetup(new TwilioStatusController(TOKEN, URL,
                new StatusNormalizer(new ObjectMapper()), publisher, new AntiReplayService(300, 100))).build();
    }

    @Test
    void callbackWithoutHeaderUsesFilterCorrelationInEventAndResponse() throws Exception {
        mvc = MockMvcBuilders.standaloneSetup(new TwilioStatusController(TOKEN, URL,
                new StatusNormalizer(new ObjectMapper()), publisher, new AntiReplayService(300, 100)))
                .addFilters(new com.bcra.gateway.observability.CorrelationIdFilter()).build();
        var parameters = form("sent");
        var response = mvc.perform(post("/webhooks/twilio/status")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).content(encode(parameters))
                .header("X-Twilio-Signature", sign(URL, parameters)))
                .andExpect(status().isOk()).andReturn().getResponse();
        var event = ArgumentCaptor.forClass(ObjectNode.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().path("correlationId").asText()).isEqualTo(response.getHeader("correlationId"));
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void signedCallbackReachesKafkaThroughRealNormalizerAndSchemaValidator() throws Exception {
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
                mock(org.springframework.kafka.core.KafkaTemplate.class);
        when(kafka.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        var mapper = new ObjectMapper();
        mvc = MockMvcBuilders.standaloneSetup(new TwilioStatusController(TOKEN, URL,
                new StatusNormalizer(mapper), new StatusEventPublisher(kafka, mapper),
                new AntiReplayService(300, 100))).build();
        var sent = form("sent");
        send(sent, "invalid").andExpect(status().isForbidden());
        verifyNoInteractions(kafka);
        send(sent, sign(URL, sent)).andExpect(status().isOk());
        send(sent, sign(URL, sent)).andExpect(status().isOk());
        var delivered = form("delivered");
        send(delivered, sign(URL, delivered)).andExpect(status().isOk());
        ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord<String, String>> records =
                ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
        verify(kafka, times(2)).send(records.capture());
        var first = records.getAllValues().getFirst();
        var second = records.getAllValues().getLast();
        assertThat(first.topic()).isEqualTo("wa.message.status.v1");
        assertThat(second.key()).isEqualTo(first.key());
        assertThat(mapper.readTree(first.value()).path("payload").path("status").asText()).isEqualTo("sent");
        assertThat(mapper.readTree(second.value()).path("payload").path("status").asText()).isEqualTo("delivered");
        assertThat(mapper.readTree(first.value()).path("correlationId").asText()).isEqualTo(CORRELATION);
        assertThat(new String(first.headers().lastHeader("correlationId").value(), StandardCharsets.UTF_8))
                .isEqualTo(CORRELATION);
        assertThat(first.value()).doesNotContain("whatsapp:", TOKEN);
    }

    @Test
    void publishesSignedCallbackWithCorrelationAndDeduplicatesEachState() throws Exception {
        var form = form("sent");
        send(form, sign(URL, form)).andExpect(status().isOk()).andExpect(header().string("correlationId", CORRELATION));
        send(form, sign(URL, form)).andExpect(status().isOk());
        var delivered = form("delivered");
        send(delivered, sign(URL, delivered)).andExpect(status().isOk());
        var events = ArgumentCaptor.forClass(ObjectNode.class);
        verify(publisher, times(2)).publish(events.capture());
        assertThat(events.getAllValues().getFirst().path("correlationId").asText()).isEqualTo(CORRELATION);
        assertThat(events.getAllValues().getFirst().path("messageId").asText()).isEqualTo(form.get("MessageSid"));
        assertThat(events.getAllValues().getFirst().path("payload").path("status").asText()).isEqualTo("sent");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void rejectsInvalidMissingWrongUrlAndTamperedSignatures() throws Exception {
        var form = form("sent");
        send(form, "invalid").andExpect(status().isForbidden());
        send(form, sign("http://internal/status", form)).andExpect(status().isForbidden());
        send(form("read"), sign(URL, form)).andExpect(status().isForbidden());
        mvc.perform(post("/webhooks/twilio/status").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(encode(form))).andExpect(status().isForbidden());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsMalformedUnsupportedOversizeAndDuplicateFields() throws Exception {
        var unsupported = form("unknown");
        send(unsupported, sign(URL, unsupported)).andExpect(status().isBadRequest());
        mvc.perform(post("/webhooks/twilio/status").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("x=%ZZ")).andExpect(status().isBadRequest());
        mvc.perform(post("/webhooks/twilio/status").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("x=1&x=2")).andExpect(status().isBadRequest());
        mvc.perform(post("/webhooks/twilio/status").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("x=" + "a".repeat(16384))).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(publisher);
    }

    @Test
    void publicationFailureReleasesReservationForRetryAndRestoresMdc() throws Exception {
        when(publisher.publish(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException()),
                CompletableFuture.completedFuture(null));
        MDC.put("correlationId", "previous");
        try {
            send(form("failed"), sign(URL, form("failed"))).andExpect(status().isServiceUnavailable());
            send(form("failed"), sign(URL, form("failed"))).andExpect(status().isOk());
            assertThat(MDC.get("correlationId")).isEqualTo("previous");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void rateLimitsRequestsBeforeProcessing() throws Exception {
        for (int index = 0; index < 120; index++) {
            send(form("sent"), "invalid").andExpect(status().isForbidden());
        }
        send(form("sent"), "invalid").andExpect(status().isTooManyRequests());
        verifyNoInteractions(publisher);
    }

    @Test
    void pendingPublicationReturnsUnavailableAndDoesNotDeduplicate() throws Exception {
        when(publisher.publish(any())).thenReturn(new CompletableFuture<>(),
                CompletableFuture.completedFuture(null));
        send(form("sent"), sign(URL, form("sent"))).andExpect(status().isServiceUnavailable());
        send(form("sent"), sign(URL, form("sent"))).andExpect(status().isOk());
        verify(publisher, times(2)).publish(any());
    }

    @Test
    void generatesCorrelationWhenAbsentAndRejectsInvalidHeader() throws Exception {
        var form = form("queued");
        var response = mvc.perform(post("/webhooks/twilio/status")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).content(encode(form))
                .header("X-Twilio-Signature", sign(URL, form))).andExpect(status().isOk()).andReturn();
        assertThat(UUID.fromString(response.getResponse().getHeader("correlationId"))).isNotNull();
        mvc.perform(post("/webhooks/twilio/status").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(encode(form)).header("X-Twilio-Signature", sign(URL, form))
                .header("correlationId", "invalid")).andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions send(Map<String, String> form, String signature)
            throws Exception {
        return mvc.perform(post("/webhooks/twilio/status").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(encode(form)).header("X-Twilio-Signature", signature).header("correlationId", CORRELATION));
    }

    private static Map<String, String> form(String status) {
        return Map.of("MessageSid", "SM00000000000000000000000000000000", "MessageStatus", status,
                "From", "whatsapp:+15005550006", "To", "whatsapp:+15005550007");
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream().map(entry -> entry.getKey() + "="
                + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }

    private static String sign(String url, Map<String, String> form) throws Exception {
        var value = new StringBuilder(url);
        new TreeMap<>(form).forEach((key, item) -> value.append(key).append(item));
        var mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
