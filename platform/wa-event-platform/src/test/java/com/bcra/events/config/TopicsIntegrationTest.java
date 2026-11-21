package com.bcra.events.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class TopicsIntegrationTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).withKraft();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private TopicsProperties topics;

    @Test
    void createsTopicsWithConfiguredPartitionsAndRetention() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()))) {
            assertThat(admin.listTopics().names().get(10, TimeUnit.SECONDS))
                .containsAll(Set.copyOf(topics.names()));
            for (String name : topics.names()) {
                boolean dlq = name.equals(topics.dlq());
                assertThat(admin.describeTopics(Set.of(name)).allTopicNames().get(10, TimeUnit.SECONDS)
                    .get(name).partitions()).hasSize(dlq ? 1 : 3);
                ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
                Config config = admin.describeConfigs(Set.of(resource)).all().get(10, TimeUnit.SECONDS).get(resource);
                assertThat(config.get("retention.ms").value()).isEqualTo(dlq ? "2592000000" : "604800000");
                assertThat(config.get("cleanup.policy").value()).isEqualTo("delete");
            }
        }
    }
}
