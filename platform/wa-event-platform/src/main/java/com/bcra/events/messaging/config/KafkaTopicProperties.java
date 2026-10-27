package com.bcra.events.messaging.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("platform.kafka.topics")
public record KafkaTopicProperties(
        @Min(3) int partitions,
        @Min(1) short replicas,
        @NotNull Duration receivedRetention,
        @NotNull Duration readyToSendRetention,
        @NotNull Duration statusRetention,
        @NotNull Duration dlqRetention) {

    public KafkaTopicProperties {
        requirePositive(receivedRetention);
        requirePositive(readyToSendRetention);
        requirePositive(statusRetention);
        requirePositive(dlqRetention);
    }

    private static void requirePositive(Duration retention) {
        if (retention == null || retention.toMillis() < 1) {
            throw new IllegalArgumentException("Topic retention must be at least one millisecond");
        }
    }
}
