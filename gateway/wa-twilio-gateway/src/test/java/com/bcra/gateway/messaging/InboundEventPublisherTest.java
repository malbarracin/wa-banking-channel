package com.bcra.gateway.messaging;

import com.bcra.gateway.messaging.producer.InboundEventPublisher;
import com.bcra.gateway.normalization.MessageNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InboundEventPublisherTest {
    static final Map<String, String> FORM = Map.of("MessageSid", "SM00000000000000000000000000000000",
            "From", "whatsapp:+15005550006", "To", "whatsapp:+15005550007", "Body", "hola");
    private final ObjectMapper mapper = new ObjectMapper();
    private final MessageNormalizer normalizer = new MessageNormalizer(mapper);

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void normalizesOnlyContractFieldsWithStableConversationAndBusinessMessageId() {
        var form = new HashMap<>(FORM);
        form.put("AccountSid", "never-propagate");
        var first = normalizer.normalize(form);
        var second = normalizer.normalize(FORM);
        assertThat(first.path("messageId").asText()).isEqualTo(FORM.get("MessageSid"));
        assertThat(first.path("conversationId").asText()).matches("[0-9a-f]{64}")
                .isEqualTo(second.path("conversationId").asText());
        assertThat(first.path("eventId")).isNotEqualTo(second.path("eventId"));
        assertThat(UUID.fromString(first.path("correlationId").asText())).isNotNull();
        assertThat(first.path("payload").size()).isEqualTo(2);
        assertThat(first.path("payload").path("customerAddress").asText()).isEqualTo(FORM.get("From"));
        assertThat(first.path("payload").path("body").asText()).isEqualTo("hola");
        assertThat(first.toString()).doesNotContain("AccountSid", "never-propagate");
        form.put("To", "whatsapp:+15005550008");
        assertThat(normalizer.normalize(form).path("conversationId")).isNotEqualTo(first.path("conversationId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatesAndPublishesKeyHeadersAndWaitsForKafkaConfirmation() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var confirmation = new CompletableFuture<SendResult<String, String>>();
        when(kafka.send(any(ProducerRecord.class))).thenReturn(confirmation);
        var publisher = new InboundEventPublisher(normalizer, kafka, Runnable::run, mapper);
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        var completion = publisher.publish(FORM).toCompletableFuture();
        assertThat(completion).isNotDone();
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        var record = captor.getValue();
        var event = mapper.readTree(record.value());
        assertThat(record.topic()).isEqualTo("wa.message.received.v1");
        assertThat(record.key()).isEqualTo(event.path("conversationId").asText());
        assertThat(event.path("correlationId").asText()).isEqualTo(correlationId);
        assertThat(new String(record.headers().lastHeader("correlationId").value(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(correlationId);
        assertThat(new String(record.headers().lastHeader("eventType").value(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("MessageReceived");
        assertThat(new String(record.headers().lastHeader("eventVersion").value(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("1");
        confirmation.complete(null);
        assertThat(completion).isCompletedWithValue(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsInvalidInputAndSchemaViolationsWithoutKafka() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var publisher = new InboundEventPublisher(normalizer, kafka, Runnable::run, mapper);
        for (String field : FORM.keySet()) {
            var invalid = new HashMap<>(FORM);
            invalid.remove(field);
            assertThatThrownBy(() -> publisher.publish(invalid)).isInstanceOf(IllegalArgumentException.class);
            invalid.put(field, " ");
            assertThatThrownBy(() -> publisher.publish(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String field : new String[] {"From", "To", "MessageSid"}) {
            var invalid = new HashMap<>(FORM);
            invalid.put(field, "invalid");
            assertThatThrownBy(() -> publisher.publish(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        var oversized = new HashMap<>(FORM);
        oversized.put("Body", "x".repeat(4097));
        assertThatThrownBy(() -> publisher.publish(oversized)).isInstanceOf(IllegalArgumentException.class);
        MDC.put("correlationId", "invalid");
        assertThatThrownBy(() -> publisher.publish(FORM)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(kafka);
    }

    @Test
    @SuppressWarnings("unchecked")
    void propagatesBrokerFailureAndExecutorSaturation() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("broker unavailable")));
        var publisher = new InboundEventPublisher(normalizer, kafka, Runnable::run, mapper);
        assertThat(publisher.publish(FORM).toCompletableFuture()).isCompletedExceptionally();
        var saturated = new InboundEventPublisher(normalizer, kafka, command -> {
            throw new java.util.concurrent.RejectedExecutionException();
        }, mapper);
        assertThatThrownBy(() -> saturated.publish(FORM))
                .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
    }
}
