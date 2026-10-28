package com.bcra.gateway.messaging.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StatusEventPublisher {
    public static final String TOPIC = "wa.message.status.v1";
    private final KafkaTemplate<String, String> kafka;
    private final JsonSchema schema;

    public StatusEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) throws IOException {
        this.kafka = kafka;
        try (var input = getClass().getResourceAsStream("/contracts/events/" + TOPIC + ".schema.json")) {
            if (input == null) {
                throw new IOException("Missing status schema");
            }
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(mapper.readTree(input),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
    }

    public CompletionStage<Void> publish(ObjectNode event) {
        if (!schema.validate(event).isEmpty()) {
            throw new IllegalArgumentException("Invalid status event");
        }
        var record = new ProducerRecord<String, String>(TOPIC, event.path("conversationId").asText(),
                event.toString());
        for (String header : new String[] {"correlationId", "eventType", "eventVersion"}) {
            record.headers().add(header, event.path(header).asText().getBytes(StandardCharsets.UTF_8));
        }
        return kafka.send(record).thenApply(result -> null);
    }
}
