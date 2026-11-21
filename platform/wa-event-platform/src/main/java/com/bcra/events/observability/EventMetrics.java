package com.bcra.events.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class EventMetrics {
    private final MeterRegistry registry;

    public EventMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void published(String topic, String eventType) {
        registry.counter("wa_events_published_total", "topic", topic, "eventType", eventType).increment();
    }

    public void consumed(String topic, String eventType, String result) {
        registry.counter("wa_events_consumed_total", "topic", topic, "eventType", eventType, "result", result)
            .increment();
    }

    public void invalid(String stage, String reason) {
        registry.counter("wa_events_invalid_total", "stage", stage, "reason", reason).increment();
    }

    public void retried(String topic) {
        registry.counter("wa_events_retried_total", "topic", topic).increment();
    }

    public void dlq(String originalTopic, String reason) {
        registry.counter("wa_events_dlq_total", "originalTopic", originalTopic, "reason", reason).increment();
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void stop(Timer.Sample sample, String topic) {
        sample.stop(registry.timer("wa_event_processing_seconds", "topic", topic));
    }
}
