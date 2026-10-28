package com.bcra.gateway.outbound;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "TWILIO_AUTH_TOKEN=synthetic-token", "TWILIO_ACCOUNT_SID=AC00000000000000000000000000000000",
        "TWILIO_WHATSAPP_FROM=whatsapp:+15005550006",
        "TWILIO_WEBHOOK_PUBLIC_URL=https://bank.example/inbound",
        "TWILIO_STATUS_CALLBACK_PUBLIC_URL=https://bank.example/status",
        "spring.datasource.url=jdbc:h2:mem:container;DB_CLOSE_DELAY=-1", "logging.level.root=ERROR"})
@DirtiesContext
@Import(OutboundKafkaIntegrationTest.Topics.class)
class OutboundKafkaIntegrationTest extends AbstractOutboundKafkaTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
            .withKraft();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // A fresh broker needs time for initial metadata discovery.
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "5000");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        KafkaAdmin.NewTopics outboundTestTopics() {
            return new KafkaAdmin.NewTopics(
                    TopicBuilder.name(OutboundSchemas.READY).partitions(3).replicas(1).build(),
                    TopicBuilder.name(OutboundSchemas.DLQ).partitions(3).replicas(1).build(),
                    TopicBuilder.name("wa.message.status.v1").partitions(3).replicas(1).build());
        }
    }
}
