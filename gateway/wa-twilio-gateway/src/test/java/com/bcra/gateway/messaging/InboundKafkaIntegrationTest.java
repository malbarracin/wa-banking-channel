package com.bcra.gateway.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"TWILIO_AUTH_TOKEN=test-token",
        "TWILIO_WEBHOOK_PUBLIC_URL=https://example.test/webhook/twilio"})
@AutoConfigureMockMvc
class InboundKafkaIntegrationTest {
    @Container
    // Kafka 3.9.1 fixes KAFKA-18281, which prevents Testcontainers' KRaft listeners from starting in 3.9.0.
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Allow metadata discovery during the initial cold request; production keeps the 500 ms bound.
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "5000");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    org.springframework.kafka.core.KafkaTemplate<String, String> kafka;

    @Test
    void signedWebhookPublishesOnceWithHeadersAndPreservesConversationOrder() throws Exception {
        var properties = new HashMap<String, Object>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        String correlationId = UUID.randomUUID().toString();
        kafka.partitionsFor("wa.message.received.v1");
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(java.util.List.of("wa.message.received.v1"));
            for (int index = 0; index < 2; index++) {
                var form = new HashMap<>(InboundEventPublisherTest.FORM);
                form.put("MessageSid", "SM" + "0".repeat(31) + index);
                form.put("Body", "message-" + index);
                String signature = sign(form);
                send(form, signature, correlationId).andExpect(status().isOk());
                send(form, signature, correlationId).andExpect(status().isOk());
            }
            var received = new java.util.ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(100)).forEach(received::add);
                assertThat(received).hasSizeGreaterThanOrEqualTo(2);
            });
            assertThat(received).hasSize(2);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var unique = new java.util.LinkedHashMap<String, String>();
            for (var record : received) {
                var event = mapper.readTree(record.value());
                assertThat(record.key()).isEqualTo(event.path("conversationId").asText());
                for (String header : new String[] {"correlationId", "eventType", "eventVersion"}) {
                    assertThat(new String(record.headers().lastHeader(header).value(), StandardCharsets.UTF_8))
                            .isEqualTo(event.path(header).asText());
                }
                unique.put(event.path("messageId").asText(), event.path("payload").path("body").asText());
            }
            assertThat(unique.values()).containsExactly("message-0", "message-1");
            assertThat(received.stream().map(record -> record.key()).distinct()).hasSize(1);
            assertThat(consumer.poll(Duration.ofSeconds(1))).isEmpty();
        }
    }

    private org.springframework.test.web.servlet.ResultActions send(Map<String, String> form, String signature,
            String correlationId) throws Exception {
        var request = post("/webhook/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Twilio-Signature", signature).header("correlationId", correlationId);
        String body = form.entrySet().stream().map(entry -> entry.getKey() + "="
                        + java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"));
        return mvc.perform(request.content(body));
    }

    private String sign(Map<String, String> form) throws Exception {
        var value = new StringBuilder("https://example.test/webhook/twilio");
        new java.util.TreeMap<>(form).forEach((key, field) -> value.append(key).append(field));
        var mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-token".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
