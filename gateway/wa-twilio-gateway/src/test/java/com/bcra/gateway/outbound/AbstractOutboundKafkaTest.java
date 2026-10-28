package com.bcra.gateway.outbound;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

abstract class AbstractOutboundKafkaTest {
    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private OutboundStore store;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private OutboundSchemas schemas;
    @MockitoBean
    private TwilioSender sender;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Test
    @DisplayName("Kafka preserva orden y deduplica una reentrega sin repetir efecto")
    void kafkaPreservesOrderAndDeduplicates() throws Exception {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        String conversation = UUID.randomUUID().toString();
        when(sender.send(anyString(), anyString())).thenReturn(
                new TwilioSender.Receipt("SM00000000000000000000000000000000", "queued"));

        publish(first, conversation, "first");
        publish(first, conversation, "first");
        publish(second, conversation, "second");

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            assertThat(store.find(second)).isPresent();
            assertThat(store.find(second).orElseThrow().state()).isEqualTo("SENT");
        });
        var order = Mockito.inOrder(sender);
        order.verify(sender).send("whatsapp:+15005550007", "first");
        order.verify(sender).send("whatsapp:+15005550007", "second");
        verify(sender, times(2)).send(anyString(), anyString());
    }

    @Test
    @DisplayName("Kafka reintenta errores transitorios y continúa tras enviar inválidos y permanentes a DLQ")
    void kafkaRetriesAndRecoversToContractDlq() throws Exception {
        String transientId = UUID.randomUUID().toString();
        String permanentId = UUID.randomUUID().toString();
        String exhaustedId = UUID.randomUUID().toString();
        String invalidId = UUID.randomUUID().toString();
        when(sender.send(anyString(), org.mockito.ArgumentMatchers.eq("transient")))
                .thenThrow(new OutboundException(OutboundException.Kind.TRANSIENT, "TWILIO_20429"))
                .thenReturn(new TwilioSender.Receipt("SM00000000000000000000000000000001", "queued"));
        when(sender.send(anyString(), org.mockito.ArgumentMatchers.eq("permanent")))
                .thenThrow(new OutboundException(OutboundException.Kind.PERMANENT, "TWILIO_21614"));
        when(sender.send(anyString(), org.mockito.ArgumentMatchers.eq("exhausted")))
                .thenThrow(new OutboundException(OutboundException.Kind.TRANSIENT, "TWILIO_20500"));
        var properties = new java.util.HashMap<String, Object>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        List<String> codes = new ArrayList<>();
        try (var dlq = new KafkaConsumer<>(properties, new StringDeserializer(), new StringDeserializer())) {
            dlq.subscribe(List.of(OutboundSchemas.DLQ));
            publish(transientId, "retry-conversation", "transient");
            publish(permanentId, "retry-conversation", "permanent");
            publish(exhaustedId, "retry-conversation", "exhausted");
            kafka.send(OutboundSchemas.READY, "invalid", invalidId).get();

            await().atMost(Duration.ofSeconds(50)).untilAsserted(() -> {
                dlq.poll(Duration.ofMillis(200)).forEach(record -> {
                    var event = schemas.validate(OutboundSchemas.DLQ, record.value());
                    String original = new String(Base64.getDecoder().decode(event.path("payload")
                            .path("originalValueBase64").asText()), StandardCharsets.UTF_8);
                    if (List.of(permanentId, exhaustedId, invalidId).stream().anyMatch(original::contains)) {
                        codes.add(event.path("payload").path("errorCode").asText());
                        assertThat(record.headers().lastHeader("providerErrorCode")).isNotNull();
                        assertThat(record.value()).doesNotContain("stackTrace", "synthetic-token");
                    }
                });
                assertThat(codes).contains("NON_RETRYABLE_ERROR", "SCHEMA_VALIDATION_FAILED", "RETRIES_EXHAUSTED");
                assertThat(store.find(transientId).orElseThrow().state()).isEqualTo("SENT");
            });
        }
        verify(sender, times(2)).send(anyString(), org.mockito.ArgumentMatchers.eq("transient"));
        verify(sender).send(anyString(), org.mockito.ArgumentMatchers.eq("permanent"));
        verify(sender, times(4)).send(anyString(), org.mockito.ArgumentMatchers.eq("exhausted"));
    }

    private void publish(String messageId, String conversationId, String body) throws Exception {
        var event = mapper.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "MessageReadyToSend");
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-01-01T00:00:00Z");
        event.put("correlationId", UUID.randomUUID().toString());
        event.put("conversationId", conversationId);
        event.put("messageId", messageId);
        event.putObject("payload").put("customerAddress", "whatsapp:+15005550007").put("body", body);
        var record = new ProducerRecord<String, String>(OutboundSchemas.READY, conversationId, event.toString());
        for (String header : new String[] {"correlationId", "eventType", "eventVersion"}) {
            record.headers().add(header, event.path(header).asText().getBytes(StandardCharsets.UTF_8));
        }
        kafka.send(record).get();
    }
}
