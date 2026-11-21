package com.bcra.events.messaging.dlq;

public final class DlqHeaders {
    public static final String ATTEMPTS = "x-dlq-attempts";
    public static final String FAILED_AT = "x-dlq-failed-at";
    public static final String REPROCESSED_FROM = "x-dlq-reprocessed-from";
    public static final String ORIGINAL_TOPIC = "kafka_dlt-original-topic";

    private DlqHeaders() {
    }
}
