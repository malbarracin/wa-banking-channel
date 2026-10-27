package com.bcra.events.messaging.producer;

import com.bcra.events.messaging.schema.EventSchemaValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventProducer {
    private final KafkaTemplate<String, String> template;
    private final EventSchemaValidator validator;
    private final ObjectMapper mapper;

    /** The caller must observe the returned future before confirming its own effect. */
    public CompletableFuture<SendResult<String, String>> send(String topic, String value) {
        validator.validate(topic, value);
        try {
            var event = mapper.readTree(value);
            var record = new ProducerRecord<String, String>(topic, event.path("conversationId").asText(), value);
            for (String name : new String[] {"correlationId", "eventType", "eventVersion"}) {
                record.headers().add(name, event.path(name).asText().getBytes(StandardCharsets.UTF_8));
            }
            return template.send(record);
        } catch (JsonProcessingException exception) {
            throw new com.bcra.events.messaging.schema.InvalidEventException();
        }
    }
}
