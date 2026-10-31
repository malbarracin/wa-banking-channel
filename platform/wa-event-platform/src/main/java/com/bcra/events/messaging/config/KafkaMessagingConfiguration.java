package com.bcra.events.messaging.config;

import com.bcra.events.dlq.DeadLetterPublisher;
import com.bcra.events.messaging.schema.InvalidEventException;
import com.bcra.events.observability.KafkaDeliveryMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaMessagingConfiguration {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(DeadLetterPublisher publisher, KafkaDeliveryMetrics metrics,
            @Value("${platform.kafka.retry.max-retries:3}") int maxRetries,
            @Value("${platform.kafka.retry.initial-interval:1000}") long initialInterval,
            @Value("${platform.kafka.retry.max-interval:10000}") long maxInterval) {
        if (maxRetries < 0 || initialInterval <= 0 || maxInterval < initialInterval) {
            throw new IllegalArgumentException("Invalid Kafka retry configuration");
        }
        var backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(maxInterval);
        var handler = new DefaultErrorHandler(publisher, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        handler.setRetryListeners(metrics);
        return handler;
    }
}
