package com.bcra.gateway.messaging;

import com.bcra.gateway.messaging.producer.InboundEventPublisher;
import com.bcra.gateway.messaging.producer.InboundWebhookPublisher;
import com.bcra.gateway.normalization.MessageNormalizer;
import com.bcra.gateway.observability.CorrelationIdFilter;
import com.bcra.gateway.security.AntiReplayService;
import com.bcra.gateway.security.TwilioSignatureValidator;
import com.bcra.gateway.web.TwilioWebhookController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InboundWebhookPipelineTest {
    @Test
    @SuppressWarnings("unchecked")
    void signedWebhookUsesRealNormalizationAndDeduplicationAndRejectsInvalidPayload() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var mapper = new ObjectMapper();
        var beans = new StaticListableBeanFactory();
        beans.addBean("publisher", new InboundEventPublisher(new MessageNormalizer(mapper), kafka,
                Runnable::run, mapper));
        var mvc = MockMvcBuilders.standaloneSetup(new TwilioWebhookController(
                new TwilioSignatureValidator("test-token", "https://example.test/webhook/twilio"),
                new AntiReplayService(300, 100), beans.getBeanProvider(InboundWebhookPublisher.class)))
                .addFilters(new CorrelationIdFilter()).build();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content(encode(InboundEventPublisherTest.FORM))
                    .header("X-Twilio-Signature", sign(InboundEventPublisherTest.FORM)))
                    .andExpect(status().isOk());
        }
        verify(kafka, times(1)).send(any(ProducerRecord.class));
        var invalid = Map.of("MessageSid", "SM11111111111111111111111111111111", "Body", "hola");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content(encode(invalid)).header("X-Twilio-Signature", sign(invalid)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoMoreInteractions(kafka);
    }

    private String encode(Map<String, String> form) {
        return form.entrySet().stream().map(entry -> entry.getKey() + "="
                + java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private String sign(Map<String, String> form) throws Exception {
        var value = new StringBuilder("https://example.test/webhook/twilio");
        new java.util.TreeMap<>(form).forEach((key, field) -> value.append(key).append(field));
        var mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-token".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
