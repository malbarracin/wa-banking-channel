package com.bcra.events.messaging.dlq;

import com.bcra.events.config.TopicsProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
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
    private final ConsumerFactory<String, String> consumers;
    private final KafkaTemplate<String, String> kafka;
    private final TopicsProperties topics;

    public DlqReprocessor(ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> kafka,
                          TopicsProperties topics) {
        this.consumers = consumers;
        this.kafka = kafka;
        this.topics = topics;
    }

    public int reprocess(long fromOffsetInclusive, long toOffsetInclusive) {
        if (fromOffsetInclusive < 0 || toOffsetInclusive < fromOffsetInclusive) {
            throw new IllegalArgumentException("Invalid DLQ offset range");
        }

        int republished = 0;
        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        try (Consumer<String, String> consumer = consumers.createConsumer("wa-event-platform-dlq-reprocess", null)) {
            consumer.assign(Collections.singleton(partition));
            consumer.seek(partition, fromOffsetInclusive);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            long nextExpected = fromOffsetInclusive;
            while (nextExpected <= toOffsetInclusive && System.nanoTime() < deadline) {
                ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(500));
                if (batch.isEmpty()) {
                    continue;
                }
                for (ConsumerRecord<String, String> record : batch.records(partition)) {
                    if (record.offset() < fromOffsetInclusive) {
                        continue;
                    }
                    if (record.offset() > toOffsetInclusive) {
                        return republished;
                    }
                    String originalTopic = header(record, DlqHeaders.ORIGINAL_TOPIC);
                    if (originalTopic == null || originalTopic.isBlank()) {
                        throw new IllegalStateException("DLQ record missing original topic header");
                    }
                    ProducerRecord<String, String> outbound =
                        new ProducerRecord<>(originalTopic, record.key(), record.value());
                    record.headers().forEach(outbound.headers()::add);
                    outbound.headers().add(DlqHeaders.REPROCESSED_FROM,
                        Long.toString(record.offset()).getBytes(StandardCharsets.UTF_8));
                    kafka.send(outbound).join();
                    republished++;
                    nextExpected = record.offset() + 1;
                }
            }
        }
        return republished;
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
