package com.bcra.gateway.messaging.producer;

import com.bcra.gateway.normalization.MessageNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InboundEventPublisher implements InboundWebhookPublisher {
    public static final String TOPIC = "wa.message.received.v1";
    private final MessageNormalizer normalizer;
    private final KafkaTemplate<String, String> kafka;
    private final Executor executor;
    private final JsonSchema schema;

    public InboundEventPublisher(MessageNormalizer normalizer, KafkaTemplate<String, String> kafka,
            @Qualifier("inboundPublicationExecutor") Executor executor, ObjectMapper mapper) throws IOException {
        this.normalizer = normalizer;
        this.kafka = kafka;
        this.executor = executor;
        try (var input = getClass().getResourceAsStream("/contracts/events/" + TOPIC + ".schema.json")) {
            if (input == null) {
                throw new IOException("Missing inbound event schema");
            }
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(mapper.readTree(input),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
    }

    @Override
    public CompletionStage<Void> publish(Map<String, String> parameters) {
        var event = normalizer.normalize(parameters);
        if (!schema.validate(event).isEmpty()) {
            throw new IllegalArgumentException("Invalid inbound message");
        }
        var record = new ProducerRecord<String, String>(TOPIC, event.path("conversationId").asText(),
                event.toString());
        for (String header : new String[] {"correlationId", "eventType", "eventVersion"}) {
            record.headers().add(header, event.path(header).asText().getBytes(StandardCharsets.UTF_8));
        }
        // Kafka send can wait for metadata; keep it off the HTTP request thread with a bounded executor.
        return CompletableFuture.supplyAsync(() -> kafka.send(record), executor)
                .thenCompose(future -> future).thenApply(result -> null);
    }
}
