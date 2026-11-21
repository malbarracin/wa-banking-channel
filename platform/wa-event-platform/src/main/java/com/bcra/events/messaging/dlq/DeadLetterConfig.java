package com.bcra.events.messaging.dlq;

import com.bcra.events.config.TopicsProperties;
import com.bcra.events.observability.EventMetrics;
import com.bcra.events.schema.SchemaValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.util.backoff.BackOff;

@Configuration(proxyBeanMethods = false)
public class DeadLetterConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DeadLetterConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, TopicsProperties topics,
                                                 EventMetrics metrics, DlqExceptionHeadersCreator headersCreator) {
        DeadLetterPublishingRecoverer deadLetter = deadLetterRecoverer(template, topics, headersCreator);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            metrics.dlq(record.topic(), DlqFailureClassifier.classify(exception));
            deadLetter.accept(record, exception);
        }, backOff());
        handler.addNotRetryableExceptions(SchemaValidationException.class, JsonProcessingException.class,
            MismatchedInputException.class);
        handler.setCommitRecovered(true);
        handler.setRetryListeners((record, ex, deliveryAttempt) -> {
            if (deliveryAttempt > 1) {
                metrics.retried(record.topic());
            }
        });
        return handler;
    }

    public static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> kafka,
                                                                    TopicsProperties topics,
                                                                    DlqExceptionHeadersCreator exceptionHeaders) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
            (record, exception) -> new TopicPartition(topics.dlq(), -1));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.excludeHeader(DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd.EX_STACKTRACE);
        recoverer.setExceptionHeadersCreator(exceptionHeaders);
        recoverer.setHeadersFunction((record, exception) -> {
            Header correlation = record.headers().lastHeader("correlationId");
            if (correlation != null && correlation.value() != null) {
                MDC.put("correlationId", new String(correlation.value(), StandardCharsets.UTF_8));
            }
            try {
                LOG.error("Consumer failed; sending record to DLQ topic={}", topics.dlq(), exception);
            } finally {
                MDC.remove("correlationId");
            }
            RecordHeaders headers = new RecordHeaders();
            headers.add(DlqHeaders.ATTEMPTS, attempts(record).getBytes(StandardCharsets.UTF_8));
            headers.add(DlqHeaders.FAILED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            return headers;
        });
        return recoverer;
    }

    public static BackOff backOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8_000L);
        return backOff;
    }

    private static String attempts(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
        if (header == null || header.value() == null || header.value().length < Integer.BYTES) {
            return "1";
        }
        return Integer.toString(ByteBuffer.wrap(header.value()).getInt());
    }
}
