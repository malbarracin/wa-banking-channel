package com.bcra.events.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bcra.events.messaging.dlq.DlqExceptionHeadersCreator;
import com.bcra.events.messaging.dlq.DlqHeaders;
import com.bcra.events.schema.SchemaValidationException;
import com.fasterxml.jackson.core.JsonParseException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ExceptionClassifier;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

class DlqConfigTest {
    @Test
    void blockingBackoffMatchesFiveDeliveries() {
        BackOff backoff = DlqConfig.blockingBackoff();
        assertThat(backoff).isInstanceOf(ExponentialBackOffWithMaxRetries.class);
        assertThat(((ExponentialBackOffWithMaxRetries) backoff).getMaxRetries()).isEqualTo(4);
        BackOffExecution execution = backoff.start();
        assertThat(execution.nextBackOff()).isEqualTo(500L);
        assertThat(execution.nextBackOff()).isEqualTo(1_000L);
        assertThat(execution.nextBackOff()).isEqualTo(2_000L);
        assertThat(execution.nextBackOff()).isEqualTo(4_000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void contractFailuresAreNotRetryableAndTransientAre() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TopicsProperties topics = new TopicsProperties("wa.message.received.v1", "wa.message.ready-to-send.v1",
            "wa.message.status.v1", "wa.message.dlq.v1", null);
        DefaultErrorHandler handler = new DlqConfig().kafkaErrorHandler(kafka, topics, new DlqExceptionHeadersCreator());
        Method classifierMethod = ExceptionClassifier.class.getDeclaredMethod("getClassifier");
        classifierMethod.setAccessible(true);
        BinaryExceptionClassifier classifier = (BinaryExceptionClassifier) classifierMethod.invoke(handler);
        assertThat(classifier.classify(new SchemaValidationException("bad"))).isFalse();
        assertThat(classifier.classify(new JsonParseException(null, "bad json"))).isFalse();
        assertThat(classifier.classify(new IllegalStateException("db down"))).isTrue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recovererPublishesCustomHeadersWithoutStacktrace() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(future);
        TopicsProperties topics = new TopicsProperties("wa.message.received.v1", "wa.message.ready-to-send.v1",
            "wa.message.status.v1", "wa.message.dlq.v1", null);
        DeadLetterPublishingRecoverer recoverer =
            DlqConfig.deadLetterRecoverer(kafka, topics, new DlqExceptionHeadersCreator());
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topics.received(), 1, 42L, "conv-1", "{}");
        record.headers().add("correlationId", "3b0f8e14-7c2a-4d55-9e01-11b22c33d44e".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "MessageReceived".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        recoverer.accept(record, new SchemaValidationException("schema boom\nsecret"));

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafka).send(captor.capture());
        ProducerRecord<?, ?> dlq = captor.getValue();
        assertThat(dlq.topic()).isEqualTo(topics.dlq());
        assertThat(dlq.key()).isEqualTo("conv-1");
        assertThat(dlq.value()).isEqualTo("{}");
        Headers headers = dlq.headers();
        assertThat(headers.lastHeader(DlqHeaders.EXCEPTION_STACKTRACE)).isNull();
        assertThat(header(headers, DlqHeaders.ORIGINAL_TOPIC)).isEqualTo(topics.received());
        assertThat(header(headers, DlqHeaders.EXCEPTION_FQCN)).isEqualTo(SchemaValidationException.class.getName());
        assertThat(header(headers, DlqHeaders.EXCEPTION_MESSAGE)).isEqualTo("schema boom secret");
        assertThat(header(headers, DlqHeaders.ATTEMPTS)).isEqualTo("1");
        assertThat(header(headers, DlqHeaders.FAILED_AT)).isNotBlank();
        assertThat(header(headers, "correlationId")).isEqualTo("3b0f8e14-7c2a-4d55-9e01-11b22c33d44e");
        assertThat(header(headers, "eventType")).isEqualTo("MessageReceived");
        assertThat(header(headers, "eventVersion")).isEqualTo("1");

        // Origin coordinates of the failed record, needed to reprocess the range later on.
        assertThat(intHeader(headers, DlqHeaders.ORIGINAL_PARTITION)).isEqualTo(1);
        assertThat(longHeader(headers, DlqHeaders.ORIGINAL_OFFSET)).isEqualTo(42L);
        assertThat(headers.lastHeader(DlqHeaders.ORIGINAL_TIMESTAMP)).isNotNull();

        // Nothing resembling a stack trace may travel in the DLQ, under any header name.
        assertThat(headers).allSatisfy(header ->
            assertThat(header.key().toLowerCase(java.util.Locale.ROOT)).doesNotContain("stacktrace"));
        assertThat(headers).allSatisfy(header -> assertThat(new String(header.value(), StandardCharsets.UTF_8))
            .doesNotContain("\tat ").doesNotContain(".java:"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void attemptsHeaderReportsTheNumberOfDeliveries() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        TopicsProperties topics = new TopicsProperties("wa.message.received.v1", "wa.message.ready-to-send.v1",
            "wa.message.status.v1", "wa.message.dlq.v1", null);
        DeadLetterPublishingRecoverer recoverer =
            DlqConfig.deadLetterRecoverer(kafka, topics, new DlqExceptionHeadersCreator());
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topics.received(), 0, 1L, "conv-1", "{}");
        record.headers().add(org.springframework.kafka.support.KafkaHeaders.DELIVERY_ATTEMPT,
            java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(5).array());
        recoverer.accept(record, new IllegalStateException("database unavailable"));

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafka).send(captor.capture());
        assertThat(header(captor.getValue().headers(), DlqHeaders.ATTEMPTS)).isEqualTo("5");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everyContractFailureOfTheReaderIsNotRetryable() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TopicsProperties topics = new TopicsProperties("wa.message.received.v1", "wa.message.ready-to-send.v1",
            "wa.message.status.v1", "wa.message.dlq.v1", null);
        DefaultErrorHandler handler = new DlqConfig().kafkaErrorHandler(kafka, topics, new DlqExceptionHeadersCreator());
        Method classifierMethod = ExceptionClassifier.class.getDeclaredMethod("getClassifier");
        classifierMethod.setAccessible(true);
        BinaryExceptionClassifier classifier = (BinaryExceptionClassifier) classifierMethod.invoke(handler);

        // Exactly the failures EnvelopeReaderContractTest produces: all of them reach the DLQ on first delivery.
        assertThat(classifier.classify(new SchemaValidationException("Invalid event JSON"))).isFalse();
        assertThat(classifier.classify(new SchemaValidationException("Event does not match its schema"))).isFalse();
        assertThat(classifier.classify(new SchemaValidationException("Record key or headers do not match envelope")))
            .isFalse();
        assertThat(classifier.classify(new SchemaValidationException("Missing event header"))).isFalse();
        assertThat(classifier.classify(new SchemaValidationException("Unsupported event type or version"))).isFalse();

        // Transient infrastructure failures keep their retries.
        assertThat(classifier.classify(new java.net.SocketTimeoutException("read timed out"))).isTrue();
        assertThat(classifier.classify(new org.springframework.dao.TransientDataAccessResourceException("db down")))
            .isTrue();
        assertThat(classifier.classify(new org.springframework.web.client.HttpServerErrorException(
            org.springframework.http.HttpStatus.BAD_GATEWAY))).isTrue();
    }

    private static String header(Headers headers, String name) {
        return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static int intHeader(Headers headers, String name) {
        return java.nio.ByteBuffer.wrap(headers.lastHeader(name).value()).getInt();
    }

    private static long longHeader(Headers headers, String name) {
        return java.nio.ByteBuffer.wrap(headers.lastHeader(name).value()).getLong();
    }
}
