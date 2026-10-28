package com.bcra.gateway.outbound;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
        "TWILIO_AUTH_TOKEN=synthetic-token", "TWILIO_ACCOUNT_SID=AC00000000000000000000000000000000",
        "TWILIO_WHATSAPP_FROM=whatsapp:+15005550006",
        "TWILIO_WEBHOOK_PUBLIC_URL=https://bank.example/inbound",
        "TWILIO_STATUS_CALLBACK_PUBLIC_URL=https://bank.example/status",
        "spring.datasource.url=jdbc:h2:mem:embedded;DB_CLOSE_DELAY=-1", "logging.level.root=ERROR"})
@EmbeddedKafka(partitions = 3, topics = {OutboundSchemas.READY, OutboundSchemas.DLQ, "wa.message.status.v1"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers", kraft = true)
@DirtiesContext
class OutboundEmbeddedKafkaTest extends AbstractOutboundKafkaTest { }
