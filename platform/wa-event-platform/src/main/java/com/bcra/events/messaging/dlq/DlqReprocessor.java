package com.bcra.events.messaging.dlq;

import com.bcra.events.config.TopicsProperties;
import com.bcra.events.messaging.consumer.EnvelopeReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DlqReprocessor {
    private static final Set<String> CONTRACT_HEADERS = Set.of("correlationId", "eventType", "eventVersion");

    private final ConsumerFactory<String, String> consumers;
    private final KafkaTemplate<String, String> kafka;
    private final TopicsProperties topics;
    private final EnvelopeReader envelopeReader;

    public DlqReprocessor(ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> kafka,
                          TopicsProperties topics, EnvelopeReader envelopeReader) {
        this.consumers = consumers;
        this.kafka = kafka;
        this.topics = topics;
        this.envelopeReader = envelopeReader;
    }

    public int reprocess(long fromOffsetInclusive, long toOffsetInclusive) {
        if (fromOffsetInclusive < 0 || toOffsetInclusive < fromOffsetInclusive) {
            throw new IllegalArgumentException("Invalid DLQ offset range");
        }

        List<ConsumerRecord<String, String>> batch = collect(fromOffsetInclusive, toOffsetInclusive);
        for (ConsumerRecord<String, String> record : batch) {
            envelopeReader.read(record, event -> { });
        }

        int republished = 0;
        for (ConsumerRecord<String, String> record : batch) {
            String originalTopic = header(record, DlqHeaders.ORIGINAL_TOPIC);
            if (originalTopic == null || originalTopic.isBlank()) {
                throw new IllegalStateException("DLQ record missing original topic header");
            }
            kafka.send(prepare(originalTopic, record)).join();
            republished++;
        }
        return republished;
    }

    private List<ConsumerRecord<String, String>> collect(long fromOffsetInclusive, long toOffsetInclusive) {
        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        try (Consumer<String, String> consumer = consumers.createConsumer("wa-event-platform-dlq-reprocess", null)) {
            consumer.assign(Collections.singleton(partition));
            consumer.seek(partition, fromOffsetInclusive);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            long nextExpected = fromOffsetInclusive;
            while (nextExpected <= toOffsetInclusive && System.nanoTime() < deadline) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                if (polled.isEmpty()) {
                    continue;
                }
                for (ConsumerRecord<String, String> record : polled.records(partition)) {
                    if (record.offset() < fromOffsetInclusive) {
                        continue;
                    }
                    if (record.offset() > toOffsetInclusive) {
                        return collected;
                    }
                    collected.add(record);
                    nextExpected = record.offset() + 1;
                }
            }
        }
        return collected;
    }

    private ProducerRecord<String, String> prepare(String originalTopic, ConsumerRecord<String, String> record) {
        ProducerRecord<String, String> outbound =
            new ProducerRecord<>(originalTopic, record.key(), record.value());
        for (Header header : record.headers()) {
            if (CONTRACT_HEADERS.contains(header.key())) {
                outbound.headers().add(header.key(), header.value());
            }
        }
        outbound.headers().add(DlqHeaders.REPROCESSED_FROM,
            Long.toString(record.offset()).getBytes(StandardCharsets.UTF_8));
        return outbound;
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
