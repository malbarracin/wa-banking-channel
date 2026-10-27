package com.bcra.events.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaDeliveryMetrics implements RetryListener {
    private final Counter errors;
    private final Counter recovered;
    private final Counter recoveryErrors;

    public KafkaDeliveryMetrics(MeterRegistry registry) {
        errors = registry.counter("platform.kafka.delivery.errors");
        recovered = registry.counter("platform.kafka.dlq.published");
        recoveryErrors = registry.counter("platform.kafka.dlq.errors");
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
        errors.increment();
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> record, Exception exception) {
        recovered.increment();
    }

    @Override
    public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
        recoveryErrors.increment();
    }
}
