package com.bcra.events.messaging.config;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.bcra.events.topic.TopicDefinitions;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class KafkaTopicConfig {

    @Bean
    NewTopic receivedTopic(KafkaTopicProperties properties) {
        return topic(TopicDefinitions.RECEIVED, properties.receivedRetention(), properties);
    }

    @Bean
    NewTopic readyToSendTopic(KafkaTopicProperties properties) {
        return topic(TopicDefinitions.READY_TO_SEND, properties.readyToSendRetention(), properties);
    }

    @Bean
    NewTopic statusTopic(KafkaTopicProperties properties) {
        return topic(TopicDefinitions.STATUS, properties.statusRetention(), properties);
    }

    @Bean
    NewTopic dlqTopic(KafkaTopicProperties properties) {
        return topic(TopicDefinitions.DLQ, properties.dlqRetention(), properties);
    }

    private NewTopic topic(TopicDefinitions definition, Duration retention, KafkaTopicProperties properties) {
        return TopicBuilder.name(definition.topicName())
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retention.toMillis()))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }
}
