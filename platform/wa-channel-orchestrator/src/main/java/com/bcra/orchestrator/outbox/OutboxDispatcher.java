package com.bcra.orchestrator.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.bcra.orchestrator.messaging.producer.OutboundEventPublisher;
import com.bcra.orchestrator.outbox.model.OutboxEvent;
import com.bcra.orchestrator.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDispatcher {
    private final OutboxEventRepository repository;
    private final OutboundEventPublisher publisher;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry metrics;
    private final int maxAttempts;
    private final long backoffMs;
    private final long sendTimeoutMs;

    public OutboxDispatcher(OutboxEventRepository repository, OutboundEventPublisher publisher,
            KafkaTemplate<String, String> kafka, MeterRegistry metrics,
            @Value("${outbox.max-attempts:3}") int maxAttempts,
            @Value("${outbox.backoff-ms:1000}") long backoffMs,
            @Value("${outbox.send-timeout-ms:10000}") long sendTimeoutMs) {
        if (maxAttempts < 1 || backoffMs < 1 || sendTimeoutMs < 1) {
            throw new IllegalArgumentException("Invalid outbox delivery configuration");
        }
        this.repository = repository;
        this.publisher = publisher;
        this.kafka = kafka;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    /** Locks the oldest pending row until ACK and commit; no SKIP LOCKED reordering. */
    @Transactional
    public void dispatchNext() {
        repository.findFirstByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscEventIdAsc()
                .ifPresent(this::deliver);
    }

    private void deliver(OutboxEvent row) {
        Instant now = Instant.now();
        if (row.getNextAttemptAt() != null && row.getNextAttemptAt().isAfter(now)) {
            return;
        }
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", row.getCorrelationId().toString());
        try {
            String code = "RETRIES_EXHAUSTED";
            try {
                var event = publisher.validate(row.getPayload());
                if (!OutboundEventPublisher.TOPIC.equals(row.getTopic())
                        || !event.path("eventId").asText().equals(row.getEventId().toString())
                        || !event.path("conversationId").asText().equals(row.getConversationId())
                        || !event.path("correlationId").asText().equals(row.getCorrelationId().toString())
                        || !event.path("messageId").asText().equals(row.getMessageId())
                        || !"MessageReadyToSend".equals(row.getEventType()) || row.getEventVersion() != 1) {
                    throw new IllegalArgumentException("Invalid outbound metadata");
                }
            } catch (IllegalArgumentException exception) {
                code = "SCHEMA_VALIDATION_FAILED";
            }
            if ("SCHEMA_VALIDATION_FAILED".equals(code) || row.getAttempts() >= maxAttempts) {
                try {
                    send(row, "wa.message.dlq.v1", "DeadLetter", publisher.deadLetter(row, code), code);
                    row.markDeadLettered(now);
                    metrics.counter("orchestrator.outbox.dlq").increment();
                } catch (InterruptedException | ExecutionException | TimeoutException | RuntimeException exception) {
                    retry(row, now, exception);
                }
                return;
            }
            try {
                send(row, OutboundEventPublisher.TOPIC, "MessageReadyToSend", row.getPayload(), null);
                row.markPublished(now);
                metrics.counter("orchestrator.outbox.published").increment();
            } catch (InterruptedException | ExecutionException | TimeoutException | RuntimeException exception) {
                retry(row, now, exception);
            }
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }

    private void retry(OutboxEvent row, Instant now, Exception exception) {
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        long delay = Math.min(backoffMs, 60000L) * (1L << Math.min(row.getAttempts(), 6));
        row.recordFailure(now.plusMillis(Math.min(delay, 60000L)));
        metrics.counter("orchestrator.outbox.failures").increment();
    }

    private void send(OutboxEvent row, String topic, String type, String json, String code)
            throws InterruptedException, ExecutionException, TimeoutException {
        var record = new ProducerRecord<String, String>(topic, row.getConversationId(), json);
        record.headers().add("correlationId", bytes(row.getCorrelationId().toString()));
        record.headers().add("eventType", bytes(type));
        record.headers().add("eventVersion", bytes("1"));
        if (code != null) {
            record.headers().add("errorCode", bytes(code));
        }
        kafka.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
