package com.bcra.events.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bcra.events.config.TopicsProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
class DlqIntegrationTest {
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
    void invalidPayloadGoesToDlqWithoutStacktraceHeader() throws Exception {
        String correlation = UUID.randomUUID().toString();
        String key = "conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b";
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()));
             KafkaConsumer<String, String> dlq = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlq-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            dlq.subscribe(List.of(topics.dlq()));
            ProducerRecord<String, String> bad = new ProducerRecord<>(topics.received(), key, "{not-json");
            bad.headers().add("correlationId", correlation.getBytes(StandardCharsets.UTF_8));
            bad.headers().add("eventType", "MessageReceived".getBytes(StandardCharsets.UTF_8));
            bad.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
            producer.send(bad).get(10, TimeUnit.SECONDS);
            producer.flush();

            ConsumerRecord<String, String> dead = await().atMost(Duration.ofSeconds(20)).until(() -> {
                var records = dlq.poll(Duration.ofMillis(500));
                return records.isEmpty() ? null : records.iterator().next();
            }, record -> record != null);

            assertThat(dead.topic()).isEqualTo(topics.dlq());
            assertThat(dead.key()).isEqualTo(key);
            assertThat(dead.value()).isEqualTo("{not-json");
            assertThat(dead.headers().lastHeader(DlqHeaders.EXCEPTION_STACKTRACE)).isNull();
            assertThat(header(dead, DlqHeaders.ORIGINAL_TOPIC)).isEqualTo(topics.received());
            assertThat(header(dead, DlqHeaders.EXCEPTION_FQCN)).isNotBlank();
            assertThat(header(dead, DlqHeaders.EXCEPTION_MESSAGE)).isNotBlank()
                .hasSizeLessThanOrEqualTo(DlqHeaders.EXCEPTION_MESSAGE_MAX);
            assertThat(header(dead, DlqHeaders.ATTEMPTS)).isEqualTo("1");
            assertThat(header(dead, DlqHeaders.FAILED_AT)).isNotBlank();
            assertThat(header(dead, "correlationId")).isEqualTo(correlation);
            assertThat(header(dead, "eventType")).isEqualTo("MessageReceived");
            assertThat(header(dead, "eventVersion")).isEqualTo("1");
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
