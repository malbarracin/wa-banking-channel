package com.bcra.events.messaging.dlq;

/** Nombres de headers propios y límite del mensaje de falla en la DLQ. */
public final class DlqHeaders {
    public static final String ATTEMPTS = "x-dlq-attempts";
    public static final String FAILED_AT = "x-dlq-failed-at";
    public static final String REPROCESSED_FROM = "x-dlq-reprocessed-from";
    public static final String ORIGINAL_TOPIC = "kafka_dlt-original-topic";
    public static final String ORIGINAL_PARTITION = "kafka_dlt-original-partition";
    public static final String ORIGINAL_OFFSET = "kafka_dlt-original-offset";
    public static final String ORIGINAL_TIMESTAMP = "kafka_dlt-original-timestamp";
    public static final String EXCEPTION_FQCN = "kafka_dlt-exception-fqcn";
    public static final String EXCEPTION_MESSAGE = "kafka_dlt-exception-message";
    public static final String EXCEPTION_STACKTRACE = "kafka_dlt-exception-stacktrace";
    public static final int EXCEPTION_MESSAGE_MAX = 200;

    private DlqHeaders() {
    }
}
