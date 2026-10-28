package com.bcra.gateway.outbound;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
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
class OutboundKafkaIntegrationTest extends AbstractOutboundKafkaTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
            .withKraft();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
