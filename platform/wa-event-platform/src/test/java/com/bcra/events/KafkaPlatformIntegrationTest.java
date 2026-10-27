package com.bcra.events;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import com.bcra.events.topic.TopicDefinitions;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "platform.kafka.topics.status-retention=12h")
@EmbeddedKafka(kraft = true, partitions = 3,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        brokerProperties = "auto.create.topics.enable=false")
@DirtiesContext
class KafkaPlatformIntegrationTest {

    @Autowired
    private KafkaProperties properties;

    @Autowired
    private KafkaTemplate<String, String> template;

    @Test
    @DisplayName("Crea los cuatro topics con tres particiones y retención configurable")
    void createsTopicsAndConfiguresRetention() throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", properties.getBootstrapServers()))) {
            for (TopicDefinitions definition : TopicDefinitions.values()) {
                var description = admin.describeTopics(List.of(definition.topicName()))
                        .allTopicNames().get(10, TimeUnit.SECONDS).get(definition.topicName());
                assertThat(description.partitions()).hasSize(3);
                assertThat(description.partitions()).allSatisfy(partition ->
                        assertThat(partition.replicas()).hasSize(1));
                var resource = new org.apache.kafka.common.config.ConfigResource(
                        org.apache.kafka.common.config.ConfigResource.Type.TOPIC, definition.topicName());
                var config = admin.describeConfigs(List.of(resource)).all().get(10, TimeUnit.SECONDS).get(resource);
                Duration retention = switch (definition) {
                    case RECEIVED, READY_TO_SEND -> Duration.ofDays(7);
                    case STATUS -> Duration.ofHours(12);
                    case DLQ -> Duration.ofDays(90);
                };
                assertThat(config.get(TopicConfig.RETENTION_MS_CONFIG).value())
                        .isEqualTo(Long.toString(retention.toMillis()));
            }
        }
    }

    @Test
    @DisplayName("La misma conversationId conserva partición y orden")
    void preservesPartitionAndOrderByConversationId() throws Exception {
        String topic = TopicDefinitions.RECEIVED.topicName();
        String conversationId = "synthetic-conversation";
        var first = template.send(topic, conversationId, "first").get(10, TimeUnit.SECONDS);
        var second = template.send(topic, conversationId, "second").get(10, TimeUnit.SECONDS);
        assertThat(second.getRecordMetadata().partition()).isEqualTo(first.getRecordMetadata().partition());

        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "partition-verification",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (var consumer = new KafkaConsumer<>(consumerProperties,
                new StringDeserializer(), new StringDeserializer())) {
            consumer.assign(List.of(new org.apache.kafka.common.TopicPartition(
                    topic, first.getRecordMetadata().partition())));
            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.records(topic)).extracting(record -> record.key())
                    .containsExactly(conversationId, conversationId);
            assertThat(records.records(topic)).extracting(record -> record.value()).containsExactly("first", "second");
        }
    }
}
