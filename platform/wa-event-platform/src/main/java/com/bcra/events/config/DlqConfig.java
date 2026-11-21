package com.bcra.events.config;

import com.bcra.events.messaging.dlq.DlqExceptionHeadersCreator;
import com.bcra.events.messaging.dlq.DlqHeaders;
import com.bcra.events.schema.SchemaValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.util.backoff.BackOff;

@Configuration
public class DlqConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DlqConfig.class);

    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumers,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumers);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        return factory;
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, TopicsProperties topics,
                                          DlqExceptionHeadersCreator exceptionHeaders) {
        DeadLetterPublishingRecoverer recoverer = deadLetterRecoverer(kafka, topics, exceptionHeaders);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, blockingBackoff());
        handler.addNotRetryableExceptions(SchemaValidationException.class, JsonProcessingException.class);
        handler.setCommitRecovered(true);
        return handler;
    }

    static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> kafka,
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
            Header attempt = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
            int count = attempt == null || attempt.value() == null || attempt.value().length < Integer.BYTES
                ? 1
                : ByteBuffer.wrap(attempt.value()).getInt();
            RecordHeaders headers = new RecordHeaders();
            headers.add(DlqHeaders.ATTEMPTS, Integer.toString(count).getBytes(StandardCharsets.UTF_8));
            headers.add(DlqHeaders.FAILED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            return headers;
        });
        return recoverer;
    }

    static BackOff blockingBackoff() {
        ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(4);
        backoff.setInitialInterval(500L);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(8_000L);
        return backoff;
    }
}
