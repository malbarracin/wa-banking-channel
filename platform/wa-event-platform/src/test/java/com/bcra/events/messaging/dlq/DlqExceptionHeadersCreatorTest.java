package com.bcra.events.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames;

class DlqExceptionHeadersCreatorTest {
    private final DlqExceptionHeadersCreator creator = new DlqExceptionHeadersCreator();
    private final HeaderNames names = HeaderNames.Builder.original()
        .topicHeader(DlqHeaders.ORIGINAL_TOPIC)
        .partitionHeader(DlqHeaders.ORIGINAL_PARTITION)
        .offsetHeader(DlqHeaders.ORIGINAL_OFFSET)
        .timestampHeader(DlqHeaders.ORIGINAL_TIMESTAMP)
        .timestampTypeHeader("kafka_dlt-original-timestamp-type")
        .consumerGroupHeader("kafka_dlt-original-consumer-group")
        .exception()
        .exceptionFqcn(DlqHeaders.EXCEPTION_FQCN)
        .exceptionMessage(DlqHeaders.EXCEPTION_MESSAGE)
        .exceptionStacktrace(DlqHeaders.EXCEPTION_STACKTRACE)
        .keyExceptionFqcn("kafka_dlt-key-exception-fqcn")
        .keyExceptionMessage("kafka_dlt-key-exception-message")
        .keyExceptionStacktrace("kafka_dlt-key-exception-stacktrace")
        .exceptionCauseFqcn("kafka_dlt-exception-cause-fqcn")
        .build();

    @Test
    void truncatesAndSanitizesExceptionMessageWithoutStacktrace() {
        String longMessage = "line1\nline2\t" + "x".repeat(250);
        RecordHeaders headers = new RecordHeaders();
        headers.add(DlqHeaders.EXCEPTION_STACKTRACE, "should-be-removed".getBytes(StandardCharsets.UTF_8));
        creator.create(headers, new IllegalStateException(longMessage), false, names);

        assertThat(headers.lastHeader(DlqHeaders.EXCEPTION_STACKTRACE)).isNull();
        assertThat(new String(headers.lastHeader(DlqHeaders.EXCEPTION_FQCN).value(), StandardCharsets.UTF_8))
            .isEqualTo(IllegalStateException.class.getName());
        String message = new String(headers.lastHeader(DlqHeaders.EXCEPTION_MESSAGE).value(), StandardCharsets.UTF_8);
        assertThat(message).doesNotContain("\n").hasSize(DlqHeaders.EXCEPTION_MESSAGE_MAX);
        assertThat(message).startsWith("line1 line2");
    }

    @Test
    void usesRootCauseAndFallbackMessage() {
        RecordHeaders headers = new RecordHeaders();
        Exception wrapped = new RuntimeException("wrapper", new Schemaish(""));
        creator.create(headers, wrapped, false, names);
        assertThat(new String(headers.lastHeader(DlqHeaders.EXCEPTION_FQCN).value(), StandardCharsets.UTF_8))
            .isEqualTo(Schemaish.class.getName());
        assertThat(new String(headers.lastHeader(DlqHeaders.EXCEPTION_MESSAGE).value(), StandardCharsets.UTF_8))
            .isEqualTo("Consumer processing failed");
    }

    @Test
    void sanitizeHelpers() {
        assertThat(DlqExceptionHeadersCreator.sanitize(null)).isEqualTo("Consumer processing failed");
        assertThat(DlqExceptionHeadersCreator.sanitize("  ok  ")).isEqualTo("ok");
        assertThat(DlqExceptionHeadersCreator.rootCause(new RuntimeException(new IllegalArgumentException("x"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class Schemaish extends RuntimeException {
        private Schemaish(String message) {
            super(message);
        }
    }
}
