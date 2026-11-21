package com.bcra.events.messaging.dlq;

import com.bcra.events.schema.SchemaValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.core.NestedExceptionUtils;

final class DlqFailureClassifier {
    private DlqFailureClassifier() {
    }

    static String classify(Throwable failure) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
        if (root instanceof JsonProcessingException) {
            return "parse";
        }
        if (root instanceof SchemaValidationException) {
            String message = root.getMessage() == null ? "" : root.getMessage();
            if (message.contains("JSON")) {
                return "parse";
            }
            if (message.contains("headers do not match") || message.contains("Missing event header")) {
                return "contract";
            }
            return "schema";
        }
        return "exhausted";
    }
}
