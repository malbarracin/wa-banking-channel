package com.bcra.events.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcra.events.domain.EventEnvelope;
import com.bcra.events.domain.EventType;
import com.bcra.events.messaging.consumer.EnvelopeReader;
import com.bcra.events.messaging.consumer.InMemoryIdempotencyStore;
import com.bcra.events.messaging.consumer.IdempotencyGuard;
import com.bcra.events.messaging.consumer.ReferenceMessageReceivedListener;
import com.bcra.events.schema.SchemaCatalog;
import com.bcra.events.schema.SchemaValidationException;
import com.bcra.events.schema.SchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

class ReferenceMessagingTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final SchemaValidator validator = new SchemaValidator(new SchemaCatalog());
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    private EventEnvelope event(UUID id, String messageId) throws Exception {
        return new EventEnvelope(id, EventType.MessageReceived, 1, Instant.now(),
            "3b0f8e14-7c2a-4d55-9e01-11b22c33d44e",
            "conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b", messageId,
            mapper.readTree("{\"channel\":\"whatsapp\",\"from\":\"+12025550123\","
                + "\"to\":\"+12025550124\",\"kind\":\"TEXT\",\"text\":{\"body\":\"Hola\"}}"));
    }

    @Test
    void publisherValidatesBeforeSendingAndUsesKeyAndHeaders() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        EventPublisher publisher = new EventPublisher(kafka, mapper, validator, metrics);
        EventEnvelope input = event(null, "SM123");
        publisher.publicar("wa.message.received.v1", input);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> captured =
            org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captured.capture());
        ProducerRecord<String, String> record = captured.getValue();
        assertThat(record.key()).isEqualTo(input.conversationId());
        assertThat(record.headers().lastHeader("eventType").value())
            .isEqualTo("MessageReceived".getBytes(StandardCharsets.UTF_8));
        assertThat(mapper.readTree(record.value()).get("eventId").asText()).isNotBlank();
        assertThatThrownBy(() -> publisher.publicar("wa.message.received.v2", input))
            .isInstanceOf(SchemaValidationException.class);
        assertThat(metrics.counter("wa_events_invalid_total", "stage", "produce").count()).isEqualTo(1);
    }

    @Test
    void invalidPayloadIsNeverPublished() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        EventPublisher publisher = new EventPublisher(kafka, mapper, validator, metrics);
        EventEnvelope original = event(null, "SM123");
        EventEnvelope invalid = new EventEnvelope(null, original.eventType(), 1, null,
            original.correlationId(), original.conversationId(), original.messageId(), mapper.createObjectNode());
        assertThatThrownBy(() -> publisher.publicar("wa.message.received.v1", invalid))
            .isInstanceOf(SchemaValidationException.class);
        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    @Test
    void readerChecksHeadersAndCleansMdcEvenWhenHandlerFails() throws Exception {
        EventEnvelope event = event(UUID.randomUUID(), "SM123");
        ConsumerRecord<String, String> record = new ConsumerRecord<>("wa.message.received.v1", 0, 0,
            event.conversationId(), mapper.writeValueAsString(event));
        record.headers().add("correlationId", event.correlationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "MessageReceived".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        EnvelopeReader reader = new EnvelopeReader(mapper, validator);
        assertThatThrownBy(() -> reader.read(record, value -> {
            assertThat(MDC.get("messageId")).isEqualTo("SM123");
            throw new IllegalStateException("effect failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("messageId")).isNull();
        record.headers().add("eventVersion", "2".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> reader.read(record, value -> { })).isInstanceOf(SchemaValidationException.class);
    }

    @Test
    void duplicateEventAndDuplicateMessageAreRejected() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        UUID id = UUID.randomUUID();
        assertThat(store.first(event(id, "SM123"))).isTrue();
        assertThat(store.first(event(id, "SM456"))).isFalse();
        assertThat(store.first(event(UUID.randomUUID(), "SM123"))).isFalse();
        assertThat(store.first(event(UUID.randomUUID(), "SM456"))).isTrue();
    }

    @Test
    void listenerUsesIndependentReferenceGroup() throws Exception {
        KafkaListener listener = ReferenceMessageReceivedListener.class
            .getMethod("receive", ConsumerRecord.class, org.springframework.kafka.support.Acknowledgment.class)
            .getAnnotation(KafkaListener.class);
        assertThat(listener.groupId()).isEqualTo("${wa.topics.consumer-groups.reference}");
        assertThat(listener.topics()).containsExactly("${wa.topics.received}");
    }

    @Test
    void listenerAcknowledgesAfterReadingAndGuardDeduplicates() throws Exception {
        EventEnvelope event = event(UUID.randomUUID(), "SM123");
        IdempotencyGuard guard = new IdempotencyGuard(new InMemoryIdempotencyStore());
        assertThat(guard.first(event)).isTrue();
        assertThat(guard.first(event)).isFalse();
        EnvelopeReader reader = mock(EnvelopeReader.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", "value");
        new ReferenceMessageReceivedListener(reader, guard).receive(record, acknowledgment);
        verify(reader).read(org.mockito.ArgumentMatchers.eq(record), any());
        verify(acknowledgment).acknowledge();
    }
}
