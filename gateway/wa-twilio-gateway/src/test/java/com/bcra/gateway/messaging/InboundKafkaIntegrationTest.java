package com.bcra.gateway.messaging;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"gateway.outbound.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:inbound-container",
        "TWILIO_AUTH_TOKEN=test-token",
        "TWILIO_WEBHOOK_PUBLIC_URL=https://example.test/webhook/twilio"})
@AutoConfigureMockMvc
class InboundKafkaIntegrationTest extends AbstractInboundKafkaTest {
    @Container
    // Kafka 3.9.1 fixes KAFKA-18281 in Testcontainers' KRaft listeners.
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "5000");
    }

    @Override
    protected String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
