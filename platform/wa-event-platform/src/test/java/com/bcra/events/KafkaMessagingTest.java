package com.bcra.events;

import com.bcra.events.dlq.DeadLetterPublisher;
import com.bcra.events.messaging.consumer.KafkaEventConsumer;
import com.bcra.events.messaging.producer.KafkaEventProducer;
import com.bcra.events.messaging.schema.EventSchemaValidator;
import com.bcra.events.messaging.schema.InvalidEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KafkaMessagingTest {
    private static final String TOPIC = "wa.message.received.v1";
    private static final String CORRELATION = "11111111-1111-4111-8111-111111111111";
    private static final String EVENT = """
            {"eventId":"11111111-1111-4111-8111-111111111111","eventType":"MessageReceived",
             "eventVersion":1,"occurredAt":"2026-01-01T12:00:00Z",
             "correlationId":"11111111-1111-4111-8111-111111111111",
             "conversationId":"synthetic-conversation","messageId":"synthetic-message",
             "payload":{"customerAddress":"synthetic-address","body":"Hola"}}
            """;
    private final ObjectMapper mapper = new ObjectMapper();
    private final EventSchemaValidator validator = new EventSchemaValidator(mapper);

    KafkaMessagingTest() throws Exception {
    }

    @Test
    @DisplayName("Valida antes de enviar y propaga clave, headers y resultado asíncrono")
    @SuppressWarnings("unchecked")
    void validatesAndPublishesWithConversationKeyAndHeaders() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        var future = new CompletableFuture<SendResult<String, String>>();
        when(template.send(any(ProducerRecord.class))).thenReturn(future);
        var producer = new KafkaEventProducer(template, validator, mapper);
        assertThatThrownBy(() -> producer.send(TOPIC, "{}")).isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(template);

        assertThat(producer.send(TOPIC, EVENT)).isSameAs(future);
        ArgumentCaptor<ProducerRecord<String, String>> captured = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captured.capture());
        assertThat(captured.getValue().key()).isEqualTo("synthetic-conversation");
        assertThat(captured.getValue().value()).isEqualTo(EVENT);
        assertThat(new String(captured.getValue().headers().lastHeader("correlationId").value(),
                StandardCharsets.UTF_8)).isEqualTo(CORRELATION);
        assertThat(new String(captured.getValue().headers().lastHeader("eventType").value(),
                StandardCharsets.UTF_8)).isEqualTo("MessageReceived");
        future.completeExceptionally(new IllegalStateException("synthetic failure"));
        assertThat(future).isCompletedExceptionally();
    }

    @Test
    @DisplayName("Confirma después del efecto y restaura el MDC previo")
    void acknowledgesAfterEffectAndRestoresMdc() {
        var consumer = new KafkaEventConsumer(validator, mapper);
        var acknowledgment = mock(Acknowledgment.class);
        MDC.put("correlationId", "previous");
        try {
            consumer.consume(record(EVENT), acknowledgment, event -> {
                assertThat(MDC.get("correlationId")).isEqualTo(CORRELATION);
                verifyNoInteractions(acknowledgment);
            });
            verify(acknowledgment).acknowledge();
            assertThat(MDC.get("correlationId")).isEqualTo("previous");
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("No confirma validación inválida ni efecto fallido y limpia el MDC")
    void rejectsInvalidRecordsAndDoesNotAcknowledgeFailedEffects() {
        var consumer = new KafkaEventConsumer(validator, mapper);
        var acknowledgment = mock(Acknowledgment.class);
        assertThatThrownBy(() -> consumer.consume(record("{}"), acknowledgment, event -> {
            throw new AssertionError("Effect must not run");
        })).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> consumer.consume(record(EVENT), acknowledgment, event -> {
            throw new IllegalStateException("synthetic failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("correlationId")).isNull();
        var mismatched = new ConsumerRecord<String, String>(TOPIC, 0, 1, "wrong-key", EVENT);
        assertThatThrownBy(() -> consumer.consume(mismatched, acknowledgment, event -> { }))
                .isInstanceOf(InvalidEventException.class);
        var missingHeader = record(EVENT);
        missingHeader.headers().remove("correlationId");
        assertThatThrownBy(() -> consumer.consume(missingHeader, acknowledgment, event -> { }))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(acknowledgment);
    }

    @Test
    @DisplayName("DLQ valida su contrato y elimina headers arbitrarios y detalles de excepción")
    void deadLetterPreservesOriginalWithSafeErrorHeaders() throws Exception {
        var publisher = new CapturingPublisher(mapper, validator);
        var original = record("malformed synthetic payload");
        original.headers().add("authorization", "synthetic-secret".getBytes(StandardCharsets.UTF_8));
        publisher.accept(original, null, new IllegalStateException("synthetic-secret", new InvalidEventException()));
        var published = publisher.published;
        validator.validate(DeadLetterPublisher.TOPIC, published.value().toString());
        var event = mapper.readTree(published.value().toString());
        assertThat(event.path("payload").path("errorCode").asText()).isEqualTo("SCHEMA_VALIDATION_FAILED");
        assertThat(new String(Base64.getDecoder().decode(event.path("payload").path("originalValueBase64")
                .asText()), StandardCharsets.UTF_8)).isEqualTo(original.value());
        assertThat(published.key()).isEqualTo(original.key());
        assertThat(published.headers()).extracting(header -> header.key())
                .containsExactly("correlationId", "eventType", "eventVersion", "errorCode");
        assertThat(published.value().toString()).doesNotContain("synthetic-secret");

        var tombstone = new ConsumerRecord<String, String>(TOPIC, 0, 2, null, null);
        publisher.accept(tombstone, null, new IllegalStateException("private diagnostic"));
        validator.validate(DeadLetterPublisher.TOPIC, publisher.published.value().toString());
        assertThat(publisher.published.value().toString()).contains("RETRIES_EXHAUSTED")
                .doesNotContain("private diagnostic");
    }

    private ConsumerRecord<String, String> record(String value) {
        var record = new ConsumerRecord<>(TOPIC, 0, 1, "synthetic-conversation", value);
        record.headers().add("correlationId", CORRELATION.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private static class CapturingPublisher extends DeadLetterPublisher {
        private ProducerRecord<Object, Object> published;

        @SuppressWarnings("unchecked")
        CapturingPublisher(ObjectMapper mapper, EventSchemaValidator validator) {
            super(mock(KafkaTemplate.class), mapper, validator);
        }

        @Override
        protected void publish(ProducerRecord<Object, Object> record, KafkaOperations<Object, Object> operations,
                ConsumerRecord<?, ?> original) {
            published = record;
        }
    }
}
