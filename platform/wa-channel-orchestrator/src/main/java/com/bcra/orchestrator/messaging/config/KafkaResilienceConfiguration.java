package com.bcra.orchestrator.messaging.config;

import com.bcra.orchestrator.messaging.consumer.InvalidEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaResilienceConfiguration {
    @Bean
    SafeDeadLetterRecoverer deadLetterRecoverer(KafkaTemplate<String, String> template,
            ObjectMapper mapper, MeterRegistry registry) {
        return new SafeDeadLetterRecoverer(template, mapper, registry);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(SafeDeadLetterRecoverer recoverer,
            @Value("${orchestrator.kafka.retry.max-retries:3}") int maxRetries,
            @Value("${orchestrator.kafka.retry.initial-interval:1000}") long initialInterval,
            @Value("${orchestrator.kafka.retry.max-interval:10000}") long maxInterval) {
        if (maxRetries < 0 || initialInterval <= 0 || maxInterval < initialInterval) {
            throw new IllegalArgumentException("Invalid Kafka retry configuration");
        }
        var backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(maxInterval);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class, jakarta.validation.ValidationException.class);
        handler.setCommitRecovered(true);
        // Framework error logs can include the original key/value or exception message.
        handler.setLogLevel(org.springframework.kafka.KafkaException.Level.DEBUG);
        return handler;
    }
}
