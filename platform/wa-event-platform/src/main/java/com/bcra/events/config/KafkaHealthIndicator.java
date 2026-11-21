package com.bcra.events.config;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

@Component
public class KafkaHealthIndicator implements HealthIndicator {
    private static final int TIMEOUT_SECONDS = 3;
    private final TopicsProperties topics;
    private final Supplier<AdminClient> clientFactory;

    @Autowired
    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin, TopicsProperties topics) {
        this.topics = topics;
        this.clientFactory = () -> AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    KafkaHealthIndicator(TopicsProperties topics, Supplier<AdminClient> clientFactory) {
        this.topics = topics;
        this.clientFactory = clientFactory;
    }

    @Override
    public Health health() {
        try (AdminClient client = clientFactory.get()) {
            client.describeCluster().clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Set<String> missing = new HashSet<>(topics.names());
            missing.removeAll(client.listTopics().names().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            return missing.isEmpty() ? Health.up().build() : Health.down().withDetail("missingTopics", missing).build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Health.down().build();
        } catch (Exception exception) {
            return Health.down().build();
        }
    }
}
