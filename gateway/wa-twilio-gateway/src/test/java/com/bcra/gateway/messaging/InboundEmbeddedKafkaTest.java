package com.bcra.gateway.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {"gateway.outbound.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:inbound-embedded",
        "TWILIO_AUTH_TOKEN=test-token",
        "TWILIO_WEBHOOK_PUBLIC_URL=https://example.test/webhook/twilio",
        "spring.kafka.producer.properties.max.block.ms=5000"})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = "wa.message.received.v1",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers", kraft = true)
@DirtiesContext
class InboundEmbeddedKafkaTest extends AbstractInboundKafkaTest {
    @Value("${spring.kafka.bootstrap-servers}")
    private String brokers;

    @Override
    protected String bootstrapServers() {
        return brokers;
    }
}
