package com.bcra.gateway.web;

import com.bcra.gateway.messaging.producer.InboundWebhookPublisher;
import com.bcra.gateway.security.AntiReplayService;
import com.bcra.gateway.security.TwilioSignatureValidator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TwilioWebhookControllerTest {
    private static final String TOKEN = "synthetic-test-token";
    private static final String PUBLIC_URL = "https://bank.example/webhook/twilio";
    private static final String SID = "SM00000000000000000000000000000000";
    private static final Map<String, String> FORM = Map.of("MessageSid", SID, "Body", "hola");
    private InboundWebhookPublisher publisher;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        publisher = mock(InboundWebhookPublisher.class);
        when(publisher.publish(anyMap())).thenReturn(CompletableFuture.completedFuture(null));
        mvc = controller(publisher);
    }

    private MockMvc controller(InboundWebhookPublisher adapter) {
        var beans = new StaticListableBeanFactory();
        if (adapter != null) {
            beans.addBean("publisher", adapter);
        }
        return MockMvcBuilders.standaloneSetup(new TwilioWebhookController(
                new TwilioSignatureValidator(TOKEN, PUBLIC_URL), new AntiReplayService(300, 100),
                beans.getBeanProvider(InboundWebhookPublisher.class))).build();
    }

    @Test
    void acceptsSignedFormAndAcknowledgesUnderOneSecond() throws Exception {
        // Warm MVC infrastructure before measuring the request path.
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(body()).header("X-Twilio-Signature", "invalid")).andExpect(status().isForbidden());
        assertTimeout(Duration.ofSeconds(1), () -> send(signature(PUBLIC_URL, FORM)).andExpect(status().isOk()));
        verify(publisher).publish(FORM);
    }

    @Test
    void rejectsInvalidMissingAndWrongUrlSignaturesWithoutPublishing() throws Exception {
        send("invalid").andExpect(status().isForbidden());
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(body())).andExpect(status().isForbidden());
        send(signature("http://internal/webhook/twilio", FORM)).andExpect(status().isForbidden());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsTamperedBodyWithoutPublishing() throws Exception {
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("MessageSid=" + SID + "&Body=changed")
                .header("X-Twilio-Signature", signature(PUBLIC_URL, FORM)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(publisher);
    }

    @Test
    void concurrentRetryDoesNotAcknowledgePendingPublication() throws Exception {
        var confirmation = new CompletableFuture<Void>();
        var enteredPublisher = new CompletableFuture<Void>();
        when(publisher.publish(anyMap())).thenAnswer(invocation -> {
            enteredPublisher.complete(null);
            return confirmation;
        });
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> send(signature(PUBLIC_URL, FORM)));
            try {
                enteredPublisher.get(2, java.util.concurrent.TimeUnit.SECONDS);
                send(signature(PUBLIC_URL, FORM)).andExpect(status().isServiceUnavailable());
            } finally {
                confirmation.complete(null);
            }
            first.get(2, java.util.concurrent.TimeUnit.SECONDS).andExpect(status().isOk());
        }
        send(signature(PUBLIC_URL, FORM)).andExpect(status().isOk());
        verify(publisher, times(1)).publish(FORM);
    }

    @Test
    void acknowledgesDuplicateWithoutRepublishing() throws Exception {
        send(signature(PUBLIC_URL, FORM)).andExpect(status().isOk());
        send(signature(PUBLIC_URL, FORM)).andExpect(status().isOk());
        verify(publisher, times(1)).publish(FORM);
    }

    @Test
    void failedPublicationRemainsRetryable() throws Exception {
        when(publisher.publish(anyMap())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException()))
                .thenReturn(CompletableFuture.completedFuture(null));
        send(signature(PUBLIC_URL, FORM)).andExpect(status().isServiceUnavailable());
        send(signature(PUBLIC_URL, FORM)).andExpect(status().isOk());
        verify(publisher, times(2)).publish(FORM);
    }

    @Test
    void unavailablePublisherDoesNotAcknowledgeOrLoseMessage() throws Exception {
        mvc = controller(null);
        send(signature(PUBLIC_URL, FORM)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void stalledPublicationReturnsRetryableResponseWithinBudget() throws Exception {
        when(publisher.publish(anyMap())).thenReturn(new CompletableFuture<>());
        assertTimeout(Duration.ofSeconds(1), () -> send(signature(PUBLIC_URL, FORM))
                .andExpect(status().isServiceUnavailable()));
    }

    @Test
    void rejectsUnsupportedContentTypeAndMalformedForm() throws Exception {
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(body() + "&Body=other")).andExpect(status().isBadRequest());
        var missingSid = Map.of("Body", "hola");
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("Body=hola").header("X-Twilio-Signature", signature(PUBLIC_URL, missingSid)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsOversizedBodyBeforeSignatureValidation() throws Exception {
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("Body=" + "a".repeat(16380)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("Body=" + "a".repeat(16379)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(publisher);
    }

    @Test
    void rateLimitsByRemoteAddressBeforeSignatureValidationAndIgnoresForwardedHeaders() throws Exception {
        for (int index = 0; index < 120; index++) {
            send("invalid").andExpect(status().isForbidden());
        }
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(body()).header("X-Twilio-Signature", signature(PUBLIC_URL, FORM))
                .header("X-Forwarded-For", "192.0.2.2"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(body()).header("X-Twilio-Signature", "invalid")
                .with(request -> { request.setRemoteAddr("192.0.2.3"); return request; }))
                .andExpect(status().isForbidden());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsMalformedEncodingWithoutPublishing() throws Exception {
        mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("Body=%ZZ")).andExpect(status().isBadRequest());
        verifyNoInteractions(publisher);
    }

    @Test
    void configurationRequiresSecretAndPublicUrl() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TwilioSignatureValidator("", PUBLIC_URL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TwilioSignatureValidator(TOKEN, PUBLIC_URL).isValid(FORM, " ")).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions send(String signature) throws Exception {
        return mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(body()).header("X-Twilio-Signature", signature));
    }

    private static String body() {
        return "MessageSid=" + SID + "&Body=hola";
    }

    private static String signature(String url, Map<String, String> parameters) throws Exception {
        var input = new StringBuilder(url);
        new TreeMap<>(parameters).forEach((key, value) -> input.append(key).append(value));
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
