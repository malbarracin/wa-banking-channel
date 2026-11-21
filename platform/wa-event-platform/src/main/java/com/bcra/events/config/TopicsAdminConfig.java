package com.bcra.events.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TopicsProperties.class)
public class TopicsAdminConfig {
    private static final long BUSINESS_RETENTION = Duration.ofDays(7).toMillis();
    private static final long DLQ_RETENTION = Duration.ofDays(30).toMillis();

    @Bean
    @Profile({"local", "dev", "test"})
    NewTopic receivedTopic(TopicsProperties topics) {
        return topic(topics.received(), 3, BUSINESS_RETENTION);
    }

    @Bean
    @Profile({"local", "dev", "test"})
    NewTopic readyToSendTopic(TopicsProperties topics) {
        return topic(topics.readyToSend(), 3, BUSINESS_RETENTION);
    }

    @Bean
    @Profile({"local", "dev", "test"})
    NewTopic statusTopic(TopicsProperties topics) {
        return topic(topics.status(), 3, BUSINESS_RETENTION);
    }

    @Bean
    @Profile({"local", "dev", "test"})
    NewTopic dlqTopic(TopicsProperties topics) {
        return topic(topics.dlq(), 1, DLQ_RETENTION);
    }

    private NewTopic topic(String name, int partitions, long retention) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1)
            .config("cleanup.policy", "delete").config("retention.ms", Long.toString(retention)).build();
    }
}
