package com.bcra.events.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.bcra.events.config.TopicsProperties;
import com.bcra.events.domain.EventEnvelope;
import com.bcra.events.domain.EventType;
import com.bcra.events.schema.SchemaValidationException;
import com.bcra.events.schema.SchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.Acknowledgment;
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
    /** Topic used only by this test to drive the real error handler with controlled failures. */
    static final String PROBE_TOPIC = "wa.test.dlq-probe.v1";
    private static final String CONVERSATION = "conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b";
    private static final String PAYLOAD = "{\"channel\":\"whatsapp\",\"from\":\"+12025550123\","
        + "\"to\":\"+12025550124\",\"kind\":\"TEXT\",\"text\":{\"body\":\"Hola\"}}";
    /** 0,5 s + 1 s + 2 s + 4 s of blocking backoff before the fifth delivery gives up. */
    private static final long RETRY_BUDGET_MS = 7_500L;

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).withKraft();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private TopicsProperties topics;

    @Autowired
    private ProbeListener probe;

    @Autowired
    private DlqReprocessor reprocessor;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private SchemaValidator validator;

    @Test
    void invalidPayloadGoesToDlqWithoutStacktraceHeader() throws Exception {
        String correlation = UUID.randomUUID().toString();
        // Unique broken payload so another test's DLQ fixture cannot be mistaken for this one.
        String badJson = "{not-json-" + correlation;
        try (KafkaProducer<String, String> producer = producer();
             KafkaConsumer<String, String> dlq = consumer()) {
            dlq.subscribe(List.of(topics.dlq()));
            ProducerRecord<String, String> bad = new ProducerRecord<>(topics.received(), CONVERSATION, badJson);
            contractHeaders(bad, correlation);
            producer.send(bad).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> dead = awaitDlq(dlq,
                record -> badJson.equals(record.value()), Duration.ofSeconds(30));

            assertThat(dead.topic()).isEqualTo(topics.dlq());
            assertThat(dead.key()).isEqualTo(CONVERSATION);
            assertThat(dead.value()).isEqualTo(badJson);
            assertThat(dead.headers().lastHeader(DlqHeaders.EXCEPTION_STACKTRACE)).isNull();
            assertThat(dead.headers()).allSatisfy(header -> assertThat(header.key()).doesNotContain("stacktrace"));
            assertThat(header(dead, DlqHeaders.ORIGINAL_TOPIC)).isEqualTo(topics.received());
            assertThat(intHeader(dead, DlqHeaders.ORIGINAL_PARTITION)).isNotNegative();
            assertThat(longHeader(dead, DlqHeaders.ORIGINAL_OFFSET)).isNotNegative();
            assertThat(dead.headers().lastHeader(DlqHeaders.ORIGINAL_TIMESTAMP)).isNotNull();
            assertThat(header(dead, DlqHeaders.EXCEPTION_FQCN)).isNotBlank();
            assertThat(header(dead, DlqHeaders.EXCEPTION_MESSAGE)).isNotBlank()
                .hasSizeLessThanOrEqualTo(DlqHeaders.EXCEPTION_MESSAGE_MAX).doesNotContain("\tat ");
            assertThat(header(dead, DlqHeaders.ATTEMPTS)).isEqualTo("1");
            assertThat(header(dead, DlqHeaders.FAILED_AT)).isNotBlank();
            assertThat(header(dead, "correlationId")).isEqualTo(correlation);
            assertThat(header(dead, "eventType")).isEqualTo("MessageReceived");
            assertThat(header(dead, "eventVersion")).isEqualTo("1");
        }
    }

    @Test
    void transientFailureIsRetriedFiveTimesBeforeTheDlq() throws Exception {
        String value = ProbeListener.FAIL_ALWAYS + ":" + UUID.randomUUID();
        try (KafkaProducer<String, String> producer = producer();
             KafkaConsumer<String, String> dlq = consumer()) {
            dlq.subscribe(List.of(topics.dlq()));
            long started = System.nanoTime();
            producer.send(probeRecord(value)).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> dead = awaitDlq(dlq, record -> value.equals(record.value()),
                Duration.ofSeconds(60));
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

            // Five deliveries in total: the original plus the four blocking retries.
            assertThat(probe.deliveries(value)).isEqualTo(5);
            assertThat(header(dead, DlqHeaders.ATTEMPTS)).isEqualTo("5");
            // The retries really waited 0,5 s + 1 s + 2 s + 4 s on the partition thread.
            assertThat(elapsedMs).isGreaterThanOrEqualTo(RETRY_BUDGET_MS);
            assertThat(header(dead, DlqHeaders.EXCEPTION_FQCN))
                .isEqualTo(IllegalStateException.class.getName());
            assertThat(dead.headers().lastHeader(DlqHeaders.EXCEPTION_STACKTRACE)).isNull();
        }
    }

    @Test
    void contractFailureReachesTheDlqOnTheFirstDeliveryWithoutRetrying() throws Exception {
        String value = ProbeListener.FAIL_SCHEMA + ":" + UUID.randomUUID();
        try (KafkaProducer<String, String> producer = producer();
             KafkaConsumer<String, String> dlq = consumer()) {
            dlq.subscribe(List.of(topics.dlq()));
            producer.send(probeRecord(value)).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> dead = awaitDlq(dlq, record -> value.equals(record.value()),
                Duration.ofSeconds(30));

            assertThat(header(dead, DlqHeaders.ATTEMPTS)).isEqualTo("1");
            assertThat(header(dead, DlqHeaders.EXCEPTION_FQCN))
                .isEqualTo(SchemaValidationException.class.getName());
            // The listener was entered exactly once: a contract failure is never retried.
            assertThat(probe.deliveries(value)).isEqualTo(1);
        }
    }

    @Test
    void blockingRetriesDoNotReorderTheConversation() throws Exception {
        String run = UUID.randomUUID().toString();
        String retried = ProbeListener.FAIL_TWICE + ":" + run;
        String following = ProbeListener.OK + ":" + run;
        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(probeRecord(retried)).get(10, TimeUnit.SECONDS);
            producer.send(probeRecord(following)).get(10, TimeUnit.SECONDS);

            await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
                .until(() -> probe.processedOf(run).size() == 2);

            // The record that failed twice is retried in place; the next one waits its turn.
            assertThat(probe.processedOf(run)).containsExactly(retried, following);
            assertThat(probe.deliveries(retried)).isEqualTo(3);
            assertThat(probe.deliveries(following)).isEqualTo(1);
        }
    }

    @Test
    void reprocessorRepublishesOnlyRangesThatAreValidAndKeepsTheDlq() throws Exception {
        String correlation = UUID.randomUUID().toString();
        String messageId = "SM" + UUID.randomUUID().toString().replace("-", "");
        String valid = validEvent(correlation, messageId);
        String invalid = "{not-json-" + correlation;
        long validOffset;
        long invalidOffset;
        int partition;
        try (KafkaProducer<String, String> producer = producer()) {
            // A record that was dead-lettered by a transient failure: its payload still honours the contract.
            RecordMetadata validMeta = producer.send(dlqRecord(valid, correlation)).get(10, TimeUnit.SECONDS);
            // And one that was dead-lettered because it is not a valid event at all.
            RecordMetadata invalidMeta = producer.send(dlqRecord(invalid, correlation)).get(10, TimeUnit.SECONDS);
            validOffset = validMeta.offset();
            invalidOffset = invalidMeta.offset();
            partition = validMeta.partition();
            assertThat(invalidMeta.partition()).isEqualTo(partition);
        }

        try (KafkaConsumer<String, String> business = consumer()) {
            business.subscribe(List.of(topics.received()));
            business.poll(Duration.ofMillis(500));

            // A range holding an event that does not match its schema is refused as a whole.
            assertThatThrownBy(() -> reprocessor.reprocess(partition, validOffset, invalidOffset + 1))
                .isInstanceOf(SchemaValidationException.class);

            // The valid record on its own is republished on its original topic.
            assertThat(reprocessor.reprocess(partition, validOffset, validOffset + 1)).isEqualTo(1);

            // Everything the reprocessor wrote carries x-dlq-reprocessed-from, so it can be told apart.
            List<ConsumerRecord<String, String>> reprocessed = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
                business.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (record.headers().lastHeader(DlqHeaders.REPROCESSED_FROM) != null) {
                        reprocessed.add(record);
                    }
                });
                return !reprocessed.isEmpty();
            });

            // Exactly one record came back: the invalid one was not republished, not even partially.
            assertThat(reprocessed).hasSize(1);
            ConsumerRecord<String, String> republished = reprocessed.get(0);
            assertThat(republished.topic()).isEqualTo(topics.received());
            assertThat(republished.key()).isEqualTo(CONVERSATION);
            assertThat(republished.value()).isEqualTo(valid);
            assertThat(header(republished, DlqHeaders.REPROCESSED_FROM))
                .isEqualTo(topics.dlq() + ":" + partition + ":" + validOffset);
            assertThat(header(republished, "correlationId")).isEqualTo(correlation);
            assertThat(header(republished, "eventType")).isEqualTo("MessageReceived");
            assertThat(header(republished, "eventVersion")).isEqualTo("1");
            assertThat(republished.headers().lastHeader(DlqHeaders.ORIGINAL_TOPIC)).isNull();
        }

        // Reprocessing does not consume the DLQ: both records are still there to be audited.
        try (KafkaConsumer<String, String> dlq = consumer()) {
            dlq.subscribe(List.of(topics.dlq()));
            assertThat(awaitDlq(dlq, record -> valid.equals(record.value()), Duration.ofSeconds(30)).offset())
                .isEqualTo(validOffset);
        }
    }

    /** A MessageReceived event that the catalog accepts; asserted here so the fixture cannot rot. */
    private String validEvent(String correlation, String messageId) throws Exception {
        EventEnvelope event = new EventEnvelope(UUID.randomUUID(), EventType.MessageReceived, 1,
            java.time.Instant.now(), correlation, CONVERSATION, messageId, mapper.readTree(PAYLOAD));
        String json = mapper.writeValueAsString(event);
        validator.validate(mapper.readTree(json), EventType.MessageReceived, 1);
        return json;
    }

    private ProducerRecord<String, String> probeRecord(String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(PROBE_TOPIC, CONVERSATION, value);
        contractHeaders(record, UUID.randomUUID().toString());
        return record;
    }

    /** A record shaped like one the recoverer would have written to the DLQ. */
    private ProducerRecord<String, String> dlqRecord(String value, String correlation) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topics.dlq(), CONVERSATION, value);
        contractHeaders(record, correlation);
        record.headers().add(DlqHeaders.ORIGINAL_TOPIC, topics.received().getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private void contractHeaders(ProducerRecord<String, String> record, String correlation) {
        record.headers().add("correlationId", correlation.getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "MessageReceived".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
    }

    private KafkaProducer<String, String> producer() {
        return new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()));
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "dlq-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));
    }

    /** Polls the DLQ until a record matching {@code match} shows up, keeping everything already seen. */
    private ConsumerRecord<String, String> awaitDlq(KafkaConsumer<String, String> dlq,
                                                    Predicate<ConsumerRecord<String, String>> match,
                                                    Duration timeout) {
        List<ConsumerRecord<String, String>> seen = new ArrayList<>();
        return await().atMost(timeout).pollInterval(Duration.ofMillis(200)).until(() -> {
            dlq.poll(Duration.ofMillis(500)).forEach(seen::add);
            return seen.stream().filter(match).findFirst().orElse(null);
        }, Objects::nonNull);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static int intHeader(ConsumerRecord<String, String> record, String name) {
        return ByteBuffer.wrap(record.headers().lastHeader(name).value()).getInt();
    }

    private static long longHeader(ConsumerRecord<String, String> record, String name) {
        return ByteBuffer.wrap(record.headers().lastHeader(name).value()).getLong();
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        NewTopic dlqProbeTopic() {
            return TopicBuilder.name(PROBE_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        ProbeListener probeListener() {
            return new ProbeListener();
        }
    }

    /**
     * Test-only consumer on {@link #PROBE_TOPIC}. It fails on demand so the production error handler,
     * backoff and recoverer are the ones under test; it never touches production code.
     */
    static class ProbeListener {
        static final String FAIL_ALWAYS = "fail-always";
        static final String FAIL_SCHEMA = "fail-schema";
        static final String FAIL_TWICE = "fail-twice";
        static final String OK = "ok";

        private final Map<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        private final List<String> processed = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = PROBE_TOPIC, groupId = "wa-event-platform-dlq-probe")
        public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
            String value = record.value();
            int delivery = deliveries.computeIfAbsent(value, key -> new AtomicInteger()).incrementAndGet();
            if (value.startsWith(FAIL_ALWAYS)) {
                throw new IllegalStateException("database unavailable");
            }
            if (value.startsWith(FAIL_SCHEMA)) {
                throw new SchemaValidationException("Event does not match its schema");
            }
            if (value.startsWith(FAIL_TWICE) && delivery <= 2) {
                throw new IllegalStateException("network blip " + delivery);
            }
            processed.add(value);
            acknowledgment.acknowledge();
        }

        int deliveries(String value) {
            AtomicInteger counter = deliveries.get(value);
            return counter == null ? 0 : counter.get();
        }

        List<String> processedOf(String run) {
            return processed.stream().filter(value -> value.endsWith(run)).toList();
        }
    }
}
