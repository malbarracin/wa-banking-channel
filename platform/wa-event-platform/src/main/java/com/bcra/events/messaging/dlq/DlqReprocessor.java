package com.bcra.events.messaging.dlq;

import com.bcra.events.EventPlatformApplication;
import com.bcra.events.config.TopicsProperties;
import com.bcra.events.messaging.consumer.EnvelopeReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DlqReprocessor {
    private static final int MAX_RANGE = 1000;
    private final ConsumerFactory<String, String> consumers;
    private final KafkaTemplate<String, String> producer;
    private final EnvelopeReader reader;
    private final TopicsProperties topics;

    public DlqReprocessor(ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> producer,
                          EnvelopeReader reader, TopicsProperties topics) {
        this.consumers = consumers;
        this.producer = producer;
        this.reader = reader;
        this.topics = topics;
    }

    public int reprocess(int partition, long start, long endExclusive) throws Exception {
        if (partition < 0 || start < 0 || endExclusive <= start || endExclusive - start > MAX_RANGE) {
            throw new IllegalArgumentException("Invalid offset range");
        }
        TopicPartition source = new TopicPartition(topics.dlq(), partition);
        List<ProducerRecord<String, String>> pending = new ArrayList<>();
        try (Consumer<String, String> consumer = consumers.createConsumer()) {
            consumer.assign(List.of(source));
            long earliest = consumer.beginningOffsets(List.of(source)).get(source);
            long latest = consumer.endOffsets(List.of(source)).get(source);
            if (start < earliest || endExclusive > latest) {
                throw new IllegalArgumentException("Offset range outside retained records");
            }
            consumer.seek(source, start);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (consumer.position(source) < endExclusive) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Timed out reading DLQ range");
                }
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.offset() < endExclusive) {
                        pending.add(prepare(record));
                    }
                }
            }
        }
        // Validate the entire range before sending anything.
        for (ProducerRecord<String, String> record : pending) {
            producer.send(record).get(30, TimeUnit.SECONDS);
        }
        return pending.size();
    }

    private ProducerRecord<String, String> prepare(ConsumerRecord<String, String> record) {
        Header original = record.headers().lastHeader(DlqHeaders.ORIGINAL_TOPIC);
        if (original == null || original.value() == null) {
            throw new IllegalArgumentException("Missing original topic");
        }
        String topic = new String(original.value(), StandardCharsets.UTF_8);
        if (!Set.of(topics.received(), topics.readyToSend(), topics.status()).contains(topic)) {
            throw new IllegalArgumentException("Original topic is not a business topic");
        }
        ConsumerRecord<String, String> originalRecord = new ConsumerRecord<>(topic, 0, 0, record.key(), record.value());
        for (Header header : record.headers()) {
            if (Set.of("correlationId", "eventType", "eventVersion").contains(header.key())) {
                originalRecord.headers().add(header);
            }
        }
        // Valida el esquema antes de publicar: nunca se reprocesa en masa un evento inválido.
        reader.read(originalRecord, event -> { });
        ProducerRecord<String, String> result = new ProducerRecord<>(topic, record.key(), record.value());
        originalRecord.headers().forEach(result.headers()::add);
        result.headers().add(DlqHeaders.REPROCESSED_FROM,
            (topics.dlq() + ":" + record.partition() + ":" + record.offset()).getBytes(StandardCharsets.UTF_8));
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected partition, start offset and exclusive end offset");
        }
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EventPlatformApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.kafka.listener.auto-startup=false").run()) {
            int count = context.getBean(DlqReprocessor.class).reprocess(
                Integer.parseInt(args[0]), Long.parseLong(args[1]), Long.parseLong(args[2]));
            System.out.println("Republished records: " + count);
        }
    }
}
