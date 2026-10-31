package com.bcra.gateway.messaging;

import com.bcra.gateway.messaging.producer.InboundEventPublisher;
import com.bcra.gateway.normalization.MessageNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InboundEventContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void rejectsMalformedNormalizedEventsBeforeSendingToKafka() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var normalizer = mock(MessageNormalizer.class);
        var publisher = new InboundEventPublisher(normalizer, kafka, Runnable::run, mapper);
        var valid = new MessageNormalizer(mapper).normalize(InboundEventPublisherTest.FORM);
        var invalidEvents = new java.util.ArrayList<ObjectNode>();
        valid.fieldNames().forEachRemaining(field -> {
            var missing = valid.deepCopy();
            missing.remove(field);
            invalidEvents.add(missing);
        });
        invalidEvents.add(valid.deepCopy().put("unexpected", "value"));
        invalidEvents.add(valid.deepCopy().put("eventType", "MessageStatus"));
        invalidEvents.add(valid.deepCopy().put("eventVersion", "1"));
        invalidEvents.add(valid.deepCopy().put("eventVersion", 2));
        invalidEvents.add(valid.deepCopy().put("eventId", "invalid"));
        invalidEvents.add(valid.deepCopy().put("correlationId", "invalid"));
        invalidEvents.add(valid.deepCopy().put("occurredAt", "yesterday"));
        invalidEvents.add(valid.deepCopy().put("conversationId", ""));
        invalidEvents.add(valid.deepCopy().put("messageId", "x".repeat(257)));
        for (String field : new String[] {"customerAddress", "body"}) {
            var missing = valid.deepCopy();
            ((ObjectNode) missing.path("payload")).remove(field);
            invalidEvents.add(missing);
        }
        var extraPayload = valid.deepCopy();
        ((ObjectNode) extraPayload.path("payload")).put("AccountSid", "synthetic-account");
        invalidEvents.add(extraPayload);
        for (var invalid : invalidEvents) {
            when(normalizer.normalize(InboundEventPublisherTest.FORM)).thenReturn(invalid);
            assertThatThrownBy(() -> publisher.publish(InboundEventPublisherTest.FORM))
                    .as("Reject malformed contract: %s", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(kafka);
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesUnicodeAtBodyLimitAndEmitsCurrentTimestamp() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var publisher = new InboundEventPublisher(new MessageNormalizer(mapper), kafka, Runnable::run, mapper);
        var form = new HashMap<>(InboundEventPublisherTest.FORM);
        String body = "á".repeat(4095) + "ñ";
        form.put("Body", body);
        Instant before = Instant.now();
        assertThat(publisher.publish(form).toCompletableFuture()).isCompletedWithValue(null);
        Instant after = Instant.now();
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        var event = mapper.readTree(captor.getValue().value());
        assertThat(event.path("payload").path("body").asText()).isEqualTo(body);
        assertThat(Instant.parse(event.path("occurredAt").asText())).isBetween(before, after);
    }
}
