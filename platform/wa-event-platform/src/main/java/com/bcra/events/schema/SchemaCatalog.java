package com.bcra.events.schema;

import com.bcra.events.domain.EventType;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class SchemaCatalog {
    private static final String BASE = "https://contracts.bcra.local/wa/events/";
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
        builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(BASE, "classpath:contracts/events/")));

    public JsonSchema schema(EventType type, int version) {
        if (type == null || version != 1) {
            throw new SchemaValidationException("Unsupported event type or version");
        }
        String name = switch (type) {
            case MessageReceived -> "message-received";
            case MessageReadyToSend -> "message-ready-to-send";
            case MessageStatus -> "message-status";
        };
        return factory.getSchema(URI.create(BASE + name + ".v" + version + ".schema.json"));
    }
}
