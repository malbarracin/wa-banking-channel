package com.bcra.events.schema;

import com.bcra.events.domain.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class SchemaValidator {
    private final SchemaCatalog catalog;

    public SchemaValidator(SchemaCatalog catalog) { this.catalog = catalog; }

    public void validate(JsonNode json, EventType type, int version) {
        if (!catalog.schema(type, version).validate(json).isEmpty()) {
            throw new SchemaValidationException("Event does not match its schema");
        }
    }
}
