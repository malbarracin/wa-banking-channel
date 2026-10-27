package com.bcra.events.observability;

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaDeliveryMetricsTest {
    @Test
    void countsFailedRecoveryWithoutSensitiveLabels() {
        var registry = new SimpleMeterRegistry();
        var metrics = new KafkaDeliveryMetrics(registry);
        var record = new org.apache.kafka.clients.consumer.ConsumerRecord<>("synthetic", 0, 0, "key", "value");
        metrics.recoveryFailed(record, new IllegalStateException("private"), new IllegalStateException("secret"));
        assertThat(registry.get("platform.kafka.dlq.errors").counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }

    @Test
    void exportsNativeConsumerLagToMicrometer() {
        var registry = new SimpleMeterRegistry();
        var consumer = mock(Consumer.class);
        var name = new MetricName("records-lag-max", "consumer-fetch-manager-metrics", "lag",
                Map.of("client-id", "synthetic-consumer"));
        var metric = mock(Metric.class);
        when(metric.metricName()).thenReturn(name);
        when(metric.metricValue()).thenReturn(7.0);
        doReturn(Map.of(name, metric)).when(consumer).metrics();
        try (var binder = new KafkaClientMetrics(consumer)) {
            binder.bindTo(registry);
            assertThat(registry.get("kafka.consumer.fetch.manager.records.lag.max").gauge().value()).isEqualTo(7);
        }
    }
}
