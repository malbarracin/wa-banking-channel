package com.bcra.events.config;

import com.bcra.events.messaging.dlq.DeadLetterConfig;
import com.bcra.events.messaging.dlq.DlqExceptionHeadersCreator;
import com.bcra.events.observability.EventMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;

/**
 * Helpers y fachada de prueba para la política de reintentos/DLQ.
 * El bean Spring de producción vive en {@link DeadLetterConfig}.
 */
public class DlqConfig {
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, TopicsProperties topics,
                                                 DlqExceptionHeadersCreator exceptionHeaders) {
        return new DeadLetterConfig().kafkaErrorHandler(kafka, topics,
            new EventMetrics(new SimpleMeterRegistry()), exceptionHeaders);
    }

    public static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> kafka,
                                                                    TopicsProperties topics,
                                                                    DlqExceptionHeadersCreator exceptionHeaders) {
        return DeadLetterConfig.deadLetterRecoverer(kafka, topics, exceptionHeaders);
    }

    public static BackOff blockingBackoff() {
        return DeadLetterConfig.backOff();
    }
}
