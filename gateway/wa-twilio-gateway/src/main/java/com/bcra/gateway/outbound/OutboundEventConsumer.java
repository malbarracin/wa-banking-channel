package com.bcra.gateway.outbound;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import com.bcra.gateway.messaging.producer.StatusEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.micrometer.core.instrument.MeterRegistry;

public class OutboundEventConsumer {
    private final OutboundSchemas schemas;
    private final OutboundStore store;
    private final TwilioSender sender;
    private final StatusEventPublisher publisher;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public OutboundEventConsumer(OutboundSchemas schemas, OutboundStore store, TwilioSender sender,
            StatusEventPublisher publisher, ObjectMapper mapper, MeterRegistry metrics) {
        this.schemas = schemas;
        this.store = store;
        this.sender = sender;
        this.publisher = publisher;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @KafkaListener(topics = OutboundSchemas.READY, groupId = "wa-twilio-gateway",
            containerFactory = "outboundKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        var event = schemas.validate(OutboundSchemas.READY, record.value());
        if (!event.path("conversationId").asText().equals(record.key())) {
            throw new OutboundException(OutboundException.Kind.INVALID, "INVALID_PARTITION_KEY");
        }
        for (String header : new String[] {"correlationId", "eventType", "eventVersion"}) {
            var supplied = record.headers().lastHeader(header);
            if (supplied == null || supplied.value() == null
                    || !event.path(header).asText().equals(new String(supplied.value(), StandardCharsets.UTF_8))) {
                throw new OutboundException(OutboundException.Kind.INVALID, "INVALID_EVENT_HEADERS");
            }
        }
        String previous = MDC.get("correlationId");
        try {
            MDC.put("correlationId", event.path("correlationId").asText());
            metrics.counter("gateway.outbound.consumed").increment();
            String messageId = event.path("messageId").asText();
            String destination = event.path("payload").path("customerAddress").asText();
            String body = event.path("payload").path("body").asText();
            String fingerprint = hash(destination + "\n" + body);
            var send = store.claim(messageId, fingerprint, record.key(), event.path("correlationId").asText());
            if (!"SENT".equals(send.state())) {
                try {
                    var receipt = sender.send(destination, body);
                    store.sent(messageId, receipt.sid(), receipt.status());
                    metrics.counter("gateway.outbound.sent").increment();
                } catch (OutboundException failure) {
                    store.failed(messageId, failure);
                    metrics.counter("gateway.outbound.errors", "kind", failure.kind().name()).increment();
                    throw failure;
                }
                send = store.find(messageId).orElseThrow();
            }
            publishStatus(send);
            acknowledgment.acknowledge();
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }

    private void publishStatus(OutboundStore.SendRecord send) {
        ObjectNode status = mapper.createObjectNode();
        status.put("eventId", UUID.nameUUIDFromBytes((send.messageId() + ":outbound-result")
                .getBytes(StandardCharsets.UTF_8)).toString());
        status.put("eventType", "MessageStatus");
        status.put("eventVersion", 1);
        status.put("occurredAt", Instant.now().toString());
        status.put("conversationId", send.conversationId());
        status.put("correlationId", send.correlationId());
        status.put("messageId", send.messageId());
        status.putObject("payload").put("status", switch (send.status()) {
            case "sent", "delivered", "read", "failed" -> send.status();
            default -> "queued";
        });
        try {
            publisher.publish(status).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboundException(OutboundException.Kind.TRANSIENT, "STATUS_PUBLICATION_FAILED");
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboundException(OutboundException.Kind.TRANSIENT, "STATUS_PUBLICATION_FAILED");
        }
    }

    private String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
