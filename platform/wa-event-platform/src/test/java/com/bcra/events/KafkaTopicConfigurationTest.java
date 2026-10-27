package com.bcra.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.bcra.events.messaging.config.KafkaTopicConfig;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaTopicConfig.class)
            .withPropertyValues("platform.kafka.topics.partitions=3", "platform.kafka.topics.replicas=1",
                    "platform.kafka.topics.received-retention=7d",
                    "platform.kafka.topics.ready-to-send-retention=7d",
                    "platform.kafka.topics.status-retention=30d", "platform.kafka.topics.dlq-retention=90d");

    @Test
    @DisplayName("Aplica particiones, réplicas y las cuatro retenciones configuradas")
    void appliesConfiguredTopicProperties() {
        contextRunner.withPropertyValues("platform.kafka.topics.partitions=6", "platform.kafka.topics.replicas=3",
                "platform.kafka.topics.received-retention=1h", "platform.kafka.topics.ready-to-send-retention=2h",
                "platform.kafka.topics.status-retention=3h", "platform.kafka.topics.dlq-retention=4h")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(NewTopic.class)).hasSize(4);
                    assertThat(context.getBeansOfType(NewTopic.class).values()).allSatisfy(topic -> {
                        assertThat(topic.numPartitions()).isEqualTo(6);
                        assertThat(topic.replicationFactor()).isEqualTo((short) 3);
                    });
                    assertThat(context.getBean("receivedTopic", NewTopic.class).configs())
                            .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "3600000");
                    assertThat(context.getBean("readyToSendTopic", NewTopic.class).configs())
                            .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "7200000");
                    assertThat(context.getBean("statusTopic", NewTopic.class).configs())
                            .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "10800000");
                    assertThat(context.getBean("dlqTopic", NewTopic.class).configs())
                            .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "14400000");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"partitions=2", "replicas=0", "received-retention=0ms",
            "ready-to-send-retention=-1h", "status-retention=0ms", "dlq-retention=invalid"})
    @DisplayName("Rechaza configuraciones de topics inválidas al arrancar")
    void rejectsInvalidTopicProperties(String property) {
        contextRunner.withPropertyValues("platform.kafka.topics." + property)
                .run(context -> assertThat(context).hasFailed());
    }
}
