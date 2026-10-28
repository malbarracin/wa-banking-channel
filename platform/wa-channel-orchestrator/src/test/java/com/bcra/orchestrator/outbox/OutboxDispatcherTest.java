package com.bcra.orchestrator.outbox;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.bcra.orchestrator.messaging.producer.OutboundEventPublisher;
import com.bcra.orchestrator.outbox.model.OutboxEvent;
import com.bcra.orchestrator.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {
    final ObjectMapper mapper = new ObjectMapper();
    final EntityManager entityManager = mock(EntityManager.class);
    final OutboundEventPublisher publisher = new OutboundEventPublisher(mapper, entityManager);
    final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    final OutboxDispatcher dispatcher = new OutboxDispatcher(repository, publisher, kafka, metrics, 2, 1, 100);
    OutboxEvent row;

    @BeforeEach
    void setup() {
        publisher.publish("conversation", UUID.randomUUID(), "outbound-1", "test-address", "test-body");
        var capture = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(entityManager).persist(capture.capture());
        row = capture.getValue();
        when(repository.findFirstByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscEventIdAsc())
                .thenReturn(Optional.of(row));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void publishesValidatedEnvelopeWithKeyHeadersAndRestoresMdc() throws Exception {
        MDC.put("correlationId", "outer");
        try {
            dispatcher.dispatchNext();
            var sent = sent();
            assertThat(sent.topic()).isEqualTo(OutboundEventPublisher.TOPIC);
            assertThat(sent.key()).isEqualTo("conversation");
            assertThat(sent.headers().lastHeader("correlationId").value())
                    .isEqualTo(row.getCorrelationId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(sent.headers().lastHeader("eventType").value()).isEqualTo("MessageReadyToSend".getBytes());
            assertThat(sent.headers().lastHeader("eventVersion").value()).isEqualTo("1".getBytes());
            assertThat(publisher.validate(sent.value()).path("messageId").asText()).isEqualTo("outbound-1");
            assertThat(row.getPublishedAt()).isNotNull();
            assertThat(metrics.counter("orchestrator.outbox.published").count()).isEqualTo(1);
            assertThat(MDC.get("correlationId")).isEqualTo("outer");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void persistsFailureAndWaitsForBackoffThenSendsSafeDlqAfterExhaustion() throws Exception {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sensitive exception")));
        dispatcher.dispatchNext();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getPublishedAt()).isNull();
        row.recordFailure(Instant.now().plusSeconds(60));
        clearInvocations(kafka);
        dispatcher.dispatchNext();
        verifyNoInteractions(kafka);
        row.recordFailure(Instant.EPOCH);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        dispatcher.dispatchNext();
        var sent = sent();
        assertThat(sent.topic()).isEqualTo("wa.message.dlq.v1");
        assertThat(sent.value()).doesNotContain("test-address", "test-body", "sensitive exception");
        assertThat(mapper.readTree(sent.value()).path("payload").path("errorCode").asText())
                .isEqualTo("RETRIES_EXHAUSTED");
        assertThat(mapper.readTree(sent.value()).path("messageId").asText()).isEqualTo(row.getMessageId());
        assertThat(mapper.readTree(sent.value()).path("conversationId").asText())
                .isEqualTo(row.getConversationId());
        assertThat(mapper.readTree(sent.value()).path("correlationId").asText())
                .isEqualTo(row.getCorrelationId().toString());
        assertThat(row.getDeadLetteredAt()).isNotNull();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void transientFailureThenSuccessPreservesEventIdentity() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));
        dispatcher.dispatchNext();
        String original = sent().value();
        row.recordFailure(Instant.EPOCH);
        var retrying = new OutboxDispatcher(repository, publisher, kafka, metrics, 3, 1, 100);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        retrying.dispatchNext();

        assertThat(sent().value()).isEqualTo(original);
        assertThat(row.getPublishedAt()).isNotNull();
        assertThat(row.getDeadLetteredAt()).isNull();
    }

    @Test
    void invalidStoredEventGoesDirectlyToDlqAndDlqFailureKeepsPending() {
        row = new OutboxEvent(UUID.randomUUID(), "conversation", UUID.randomUUID(), "m",
                "MessageReadyToSend", 1, OutboundEventPublisher.TOPIC, "{}", Instant.now());
        when(repository.findFirstByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscEventIdAsc())
                .thenReturn(Optional.of(row));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));
        dispatcher.dispatchNext();
        assertThat(sent().topic()).isEqualTo("wa.message.dlq.v1");
        assertThat(sent().value()).contains("SCHEMA_VALIDATION_FAILED");
        assertThat(row.getDeadLetteredAt()).isNull();
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void mismatchedStoredMetadataNeverPublishesOutbound() {
        row = new OutboxEvent(UUID.randomUUID(), row.getConversationId(), row.getCorrelationId(), row.getMessageId(),
                "MessageReadyToSend", 1, row.getTopic(), row.getPayload(), Instant.now());
        when(repository.findFirstByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscEventIdAsc())
                .thenReturn(Optional.of(row));
        dispatcher.dispatchNext();
        assertThat(sent().topic()).isEqualTo("wa.message.dlq.v1");
        assertThat(row.getDeadLetteredAt()).isNotNull();
    }

    @Test
    void validatesBeforePersistenceAndDoesNotExposeInputInErrors() {
        clearInvocations(entityManager);
        assertThatThrownBy(() -> publisher.publish("conversation", UUID.randomUUID(), "m", "private", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid outbound event");
        assertThatThrownBy(() -> publisher.publish(null, UUID.randomUUID(), "m", "a", "b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.validate("not-json-private"))
                .hasMessage("Invalid outbound event").hasNoCause();
        assertThatThrownBy(() -> publisher.validate(row.getPayload() + " {}"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(entityManager);
        assertThatThrownBy(() -> new OutboxDispatcher(repository, publisher, kafka, metrics, 0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deadLetterHeadersFollowPublishedH01Contract() {
        row = new OutboxEvent(UUID.randomUUID(), "conversation", UUID.randomUUID(), "m",
                "MessageReadyToSend", 1, OutboundEventPublisher.TOPIC, "{}", Instant.now());
        when(repository.findFirstByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscEventIdAsc())
                .thenReturn(Optional.of(row));

        dispatcher.dispatchNext();

        assertThat(sent().topic()).isEqualTo("wa.message.dlq.v1");
        assertThat(sent().headers()).extracting(org.apache.kafka.common.header.Header::key)
                .containsOnly("correlationId", "eventType", "eventVersion", "errorCode");
    }

    @Test
    void missingAcknowledgmentKeepsPendingAndSchedulesRetry() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
        var boundedDispatcher = new OutboxDispatcher(repository, publisher, kafka, metrics, 3, 1000, 1);
        Instant before = Instant.now();

        boundedDispatcher.dispatchNext();

        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getDeadLetteredAt()).isNull();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfterOrEqualTo(before.plusMillis(1000));
        assertThat(sent().topic()).isEqualTo(OutboundEventPublisher.TOPIC);
    }

    @Test
    void rejectsUnexpectedFieldsAndInvalidFormats() throws Exception {
        for (String field : java.util.List.of("unexpected", "eventId", "occurredAt")) {
            var event = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(row.getPayload());
            event.put(field, "private-invalid-value");
            assertThatThrownBy(() -> publisher.validate(event.toString()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid outbound event");
        }
        var event = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(row.getPayload());
        ((com.fasterxml.jackson.databind.node.ObjectNode) event.path("payload")).put("sessionToken", "private");
        assertThatThrownBy(() -> publisher.validate(event.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid outbound event");
    }

    @Test
    void emptyQueueAndSchedulerDelegate() {
        when(repository.findFirstByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscEventIdAsc())
                .thenReturn(Optional.empty());
        new OutboxScheduler(dispatcher).publishPending();
        verifyNoInteractions(kafka);
    }

    private ProducerRecord<String, String> sent() {
        ArgumentCaptor<ProducerRecord<String, String>> capture = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, atLeastOnce()).send(capture.capture());
        return capture.getValue();
    }
}
