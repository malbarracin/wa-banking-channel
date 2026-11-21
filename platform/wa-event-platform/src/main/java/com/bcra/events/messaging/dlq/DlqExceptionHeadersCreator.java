package com.bcra.events.messaging.dlq;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.ExceptionHeadersCreator;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames;
import org.springframework.stereotype.Component;

/**
 * Headers de excepción en DLQ: FQCN y mensaje truncado/saneado; nunca stack trace.
 */
@Component
public class DlqExceptionHeadersCreator implements ExceptionHeadersCreator {
    @Override
    public void create(Headers headers, Exception exception, boolean isKey, HeaderNames names) {
        Throwable root = rootCause(exception);
        HeaderNames.ExceptionInfo info = names.getExceptionInfo();
        String fqcn = isKey ? info.getKeyExceptionFqcn() : info.getExceptionFqcn();
        String message = isKey ? info.getKeyExceptionMessage() : info.getExceptionMessage();
        String stacktrace = isKey ? info.getKeyExceptionStacktrace() : info.getExceptionStacktrace();
        headers.remove(fqcn);
        headers.remove(message);
        headers.remove(stacktrace);
        headers.add(fqcn, bytes(root.getClass().getName()));
        headers.add(message, bytes(sanitize(root.getMessage())));
    }

    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Consumer processing failed";
        }
        String cleaned = raw.replaceAll("[\\p{Cntrl}&&[^\t]]", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return "Consumer processing failed";
        }
        if (cleaned.length() > DlqHeaders.EXCEPTION_MESSAGE_MAX) {
            return cleaned.substring(0, DlqHeaders.EXCEPTION_MESSAGE_MAX);
        }
        return cleaned;
    }

    static Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
