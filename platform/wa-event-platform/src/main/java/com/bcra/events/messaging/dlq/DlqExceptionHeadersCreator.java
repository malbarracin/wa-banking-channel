package com.bcra.events.messaging.dlq;

import com.bcra.events.observability.PhoneMasker;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;

@Component
public class DlqExceptionHeadersCreator implements DeadLetterPublishingRecoverer.ExceptionHeadersCreator {
    private static final int MAX_MESSAGE_LENGTH = 200;
    private static final Pattern PHONE = Pattern.compile("\\+[0-9]{6,15}");

    @Override
    public void create(Headers headers, Exception exception, boolean overwrite,
                       DeadLetterPublishingRecoverer.HeaderNames names) {
        DeadLetterPublishingRecoverer.HeaderNames.ExceptionInfo info = names.getExceptionInfo();
        add(headers, info.getExceptionFqcn(), exception.getClass().getName(), overwrite);
        add(headers, info.getExceptionMessage(), sanitize(truncate(exception.getMessage())), overwrite);
    }

    static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }

    static String sanitize(String message) {
        Matcher matcher = PHONE.matcher(message);
        StringBuilder sanitized = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(PhoneMasker.mask(matcher.group())));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    private void add(Headers headers, String name, String value, boolean overwrite) {
        if (overwrite) {
            headers.remove(name);
        } else if (headers.lastHeader(name) != null) {
            return;
        }
        headers.add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
    }
}
