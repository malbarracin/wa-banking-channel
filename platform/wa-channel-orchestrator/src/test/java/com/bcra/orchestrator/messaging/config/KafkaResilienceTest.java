package com.bcra.orchestrator.messaging.config;

import com.bcra.orchestrator.messaging.consumer.InvalidEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KafkaResilienceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final KafkaResilienceConfiguration configuration = new KafkaResilienceConfiguration();
    private KafkaTemplate<String, String> template;
    private SafeDeadLetterRecoverer recoverer;
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.completedFuture(mock(SendResult.class)));
        recoverer = configuration.deadLetterRecoverer(template, mapper, registry);
        recoverer.setVerifyPartition(false);
    }

    @Test
    void retriesThenPublishesSafeMetadataAndRestoresMdc() throws Exception {
        var handler = configuration.kafkaErrorHandler(recoverer, 2, 1, 2);
        var record = record();
        String correlation = "11111111-1111-1111-1111-111111111111";
        record.headers().add("correlationId", correlation.getBytes(StandardCharsets.UTF_8));
        record.headers().add("Authorization", "secret".getBytes(StandardCharsets.UTF_8));
        var failure = new IllegalStateException("secret phone +15005550006");
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        verify(template, never()).send(any(ProducerRecord.class));
        MDC.put("correlationId", "previous");
        try {
            assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
            assertThat(MDC.get("correlationId")).isEqualTo("previous");
        } finally {
            MDC.clear();
        }
        ProducerRecord<?, ?> sent = sent();
        assertThat(sent.topic()).isEqualTo(SafeDeadLetterRecoverer.TOPIC);
        assertThat(sent.partition()).isNull();
        assertThat(sent.value().toString()).doesNotContain("secret", "+15005550006");
        var event = mapper.readTree(sent.value().toString());
        try (var schema = getClass().getResourceAsStream("/contracts/events/wa.message.dlq.v1.schema.json")) {
            assertThat(schema).isNotNull();
            assertThat(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                    .getSchema(mapper.readTree(schema)).validate(event)).isEmpty();
        }
        assertThat(event.path("correlationId").asText()).isEqualTo(correlation);
        assertThat(event.path("conversationId").asText()).isEqualTo(sent.key());
        assertThat(event.path("payload").path("originalOffset").asLong()).isEqualTo(42);
        assertThat(event.path("payload").path("originalPartition").asInt()).isEqualTo(1);
        assertThat(event.path("payload").path("originalTopic").asText()).isEqualTo(record.topic());
        assertThat(event.path("payload").path("originalValueBase64").asText()).isEmpty();
        assertThat(event.path("payload").path("errorCode").asText()).isEqualTo("RETRIES_EXHAUSTED");
        assertThat(sent.headers().toArray()).hasSize(4);
        assertThat(new String(sent.headers().lastHeader("correlationId").value(), StandardCharsets.UTF_8))
                .isEqualTo(correlation);
        assertThat(registry.counter("orchestrator.kafka.dlq.published").count()).isEqualTo(1);
    }

    @Test
    void wrappedValidationFailureGoesDirectlyToDlq() throws Exception {
        var handler = configuration.kafkaErrorHandler(recoverer, 3, 1, 2);
        assertThat(handler.handleOne(new RuntimeException(new InvalidEventException()), record(), consumer, container))
                .isTrue();
        assertThat(mapper.readTree(sent().value().toString()).path("payload").path("errorCode").asText())
                .isEqualTo("SCHEMA_VALIDATION_FAILED");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void beanValidationIsNotRetriedAndInvalidCorrelationIsReplaced() throws Exception {
        var record = record();
        record.headers().add("correlationId", "invalid\nsecret".getBytes(StandardCharsets.UTF_8));
        var handler = configuration.kafkaErrorHandler(recoverer, 3, 1, 2);
        assertThat(handler.handleOne(new jakarta.validation.ValidationException("secret"), record, consumer, container))
                .isTrue();
        assertThat(mapper.readTree(sent().value().toString()).path("correlationId").asText())
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void failedPublicationIsNotAcknowledgedOrCounted() {
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unavailable")));
        var handler = configuration.kafkaErrorHandler(recoverer, 0, 1, 2);
        assertThat(handler.handleOne(new IllegalStateException(), record(), consumer, container)).isFalse();
        assertThat(registry.counter("orchestrator.kafka.dlq.published").count()).isZero();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void rejectsInvalidBackoff() {
        assertThatThrownBy(() -> configuration.kafkaErrorHandler(recoverer, -1, 1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.kafkaErrorHandler(recoverer, 1, 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.kafkaErrorHandler(recoverer, 1, 3, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recoveryLogCarriesCorrelationWithoutPrivateData() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(SafeDeadLetterRecoverer.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String correlation = "11111111-1111-1111-1111-111111111111";
        var record = record();
        record.headers().add("correlationId", correlation.getBytes(StandardCharsets.UTF_8));
        try {
            recoverer.accept(record, consumer, new InvalidEventException());
            assertThat(appender.list).hasSize(1);
            var log = appender.list.getFirst();
            assertThat(log.getMDCPropertyMap()).containsEntry("correlationId", correlation);
            assertThat(log.getFormattedMessage()).contains("partition=1", "offset=42")
                    .doesNotContain("secret", "phone", "payload");
            assertThat(log.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("wa.message.received.v1", 1, 42, "secret phone", "secret payload");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ProducerRecord<?, ?> sent() {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return captor.getValue();
    }
}
