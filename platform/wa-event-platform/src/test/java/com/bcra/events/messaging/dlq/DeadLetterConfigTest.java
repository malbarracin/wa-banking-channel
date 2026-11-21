package com.bcra.events.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcra.events.config.TopicsProperties;
import com.bcra.events.observability.EventMetrics;
import com.bcra.events.schema.SchemaValidationException;
import com.fasterxml.jackson.core.JsonParseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;

class DeadLetterConfigTest {
    private final TopicsProperties topics = new TopicsProperties(
        "wa.message.received.v1", "wa.message.ready-to-send.v1", "wa.message.status.v1", "wa.message.dlq.v1",
        new TopicsProperties.ConsumerGroups("wa-twilio-gateway", "wa-channel-orchestrator",
            "wa-event-platform-reference"));
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EventMetrics metrics = new EventMetrics(registry);

    @Test
    void classifiesFailureReasons() {
        assertThat(DlqFailureClassifier.classify(new SchemaValidationException("Invalid event JSON")))
            .isEqualTo("parse");
        assertThat(DlqFailureClassifier.classify(new SchemaValidationException("schema mismatch")))
            .isEqualTo("schema");
        assertThat(DlqFailureClassifier.classify(
            new SchemaValidationException("Record key or headers do not match envelope"))).isEqualTo("contract");
        assertThat(DlqFailureClassifier.classify(new JsonParseException(null, "bad"))).isEqualTo("parse");
        assertThat(DlqFailureClassifier.classify(new IllegalStateException("db down"))).isEqualTo("exhausted");
    }

    @Test
    void truncatesAndMasksExceptionHeaders() {
        DlqExceptionHeadersCreator creator = new DlqExceptionHeadersCreator();
        assertThat(DlqExceptionHeadersCreator.truncate("x".repeat(250))).hasSize(200);
        assertThat(DlqExceptionHeadersCreator.sanitize("phone +5491122334455 leaked"))
            .isEqualTo("phone +549*********55 leaked");

        DeadLetterPublishingRecoverer.HeaderNames names = headerNames();
        Headers headers = new RecordHeaders();
        creator.create(headers, new SchemaValidationException("boom +12025550123"), true, names);
        assertThat(new String(headers.lastHeader(names.getExceptionInfo().getExceptionFqcn()).value(),
            StandardCharsets.UTF_8)).isEqualTo(SchemaValidationException.class.getName());
        assertThat(new String(headers.lastHeader(names.getExceptionInfo().getExceptionMessage()).value(),
            StandardCharsets.UTF_8)).contains("+120*******23").doesNotContain("+12025550123");
        assertThat(headers.lastHeader("kafka_dlt-exception-stacktrace")).isNull();
    }

    @Test
    void errorHandlerSendsToDlqWithMetricsAndCustomHeaders() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.getDefaultTopic()).thenReturn(null);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        DefaultErrorHandler handler = new DeadLetterConfig()
            .kafkaErrorHandler(kafka, topics, metrics, new DlqExceptionHeadersCreator());

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("wa.message.received.v1", 0, 7L, "conv-1", "{\"bad\":true}");
        record.headers().add(KafkaHeaders.DELIVERY_ATTEMPT, ByteBuffer.allocate(Integer.BYTES).putInt(1).array());

        handler.handleOne(new SchemaValidationException("Invalid event JSON"), record, mockConsumer(),
            mockContainer());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        ProducerRecord<String, String> dlqRecord = sent.getValue();
        assertThat(dlqRecord.topic()).isEqualTo("wa.message.dlq.v1");
        assertThat(dlqRecord.key()).isEqualTo("conv-1");
        assertThat(new String(dlqRecord.headers().lastHeader(DlqHeaders.ATTEMPTS).value(), StandardCharsets.UTF_8))
            .isEqualTo("1");
        assertThat(dlqRecord.headers().lastHeader(DlqHeaders.FAILED_AT)).isNotNull();
        assertThat(dlqRecord.headers().lastHeader("kafka_dlt-exception-stacktrace")).isNull();
        assertThat(registry.counter("wa_events_dlq_total", "originalTopic", "wa.message.received.v1",
            "reason", "parse").count()).isEqualTo(1);
    }

    @Test
    void retryListenerIncrementsRetriedCounter() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        DefaultErrorHandler handler = new DeadLetterConfig()
            .kafkaErrorHandler(kafka, topics, metrics, new DlqExceptionHeadersCreator());

        var getRetryListeners = org.springframework.kafka.listener.FailedRecordProcessor.class
            .getDeclaredMethod("getRetryListeners");
        getRetryListeners.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<org.springframework.kafka.listener.RetryListener> listeners =
            (java.util.List<org.springframework.kafka.listener.RetryListener>) getRetryListeners.invoke(handler);
        assertThat(listeners).isNotEmpty();
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("wa.message.received.v1", 0, 0L, "k", "v");
        listeners.getFirst().failedDelivery(record, new IllegalStateException("transient"), 1);
        listeners.getFirst().failedDelivery(record, new IllegalStateException("transient"), 2);
        assertThat(registry.counter("wa_events_retried_total", "topic", "wa.message.received.v1").count())
            .isEqualTo(1);
    }

    private static DeadLetterPublishingRecoverer.HeaderNames headerNames() {
        return DeadLetterPublishingRecoverer.HeaderNames.Builder.original()
            .offsetHeader("kafka_dlt-original-offset")
            .timestampHeader("kafka_dlt-original-timestamp")
            .timestampTypeHeader("kafka_dlt-original-timestamp-type")
            .topicHeader("kafka_dlt-original-topic")
            .partitionHeader("kafka_dlt-original-partition")
            .consumerGroupHeader("kafka_dlt-original-consumer-group")
            .exception()
            .keyExceptionFqcn("kafka_dlt-key-exception-fqcn")
            .exceptionFqcn("kafka_dlt-exception-fqcn")
            .exceptionCauseFqcn("kafka_dlt-exception-cause-fqcn")
            .keyExceptionMessage("kafka_dlt-key-exception-message")
            .exceptionMessage("kafka_dlt-exception-message")
            .keyExceptionStacktrace("kafka_dlt-key-exception-stacktrace")
            .exceptionStacktrace("kafka_dlt-exception-stacktrace")
            .build();
    }

    @SuppressWarnings("unchecked")
    private static org.apache.kafka.clients.consumer.Consumer<?, ?> mockConsumer() {
        return mock(org.apache.kafka.clients.consumer.Consumer.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static org.springframework.kafka.listener.MessageListenerContainer mockContainer() {
        org.springframework.kafka.listener.MessageListenerContainer container =
            mock(org.springframework.kafka.listener.MessageListenerContainer.class);
        when(container.getListenerId()).thenReturn("test");
        when(container.getGroupId()).thenReturn("wa-event-platform-reference");
        return container;
    }
}
