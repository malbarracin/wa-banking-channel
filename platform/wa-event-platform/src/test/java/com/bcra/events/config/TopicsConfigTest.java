package com.bcra.events.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

class TopicsConfigTest {
    private final TopicsProperties topics = new TopicsProperties("received", "ready", "status", "dlq",
        new TopicsProperties.ConsumerGroups("gateway", "orchestrator", "reference"));

    @Test
    void declaresTopicsWithRetentionAndPartitions() {
        TopicsAdminConfig config = new TopicsAdminConfig();
        for (NewTopic topic : new NewTopic[]{config.receivedTopic(topics), config.readyToSendTopic(topics),
                config.statusTopic(topics)}) {
            assertThat(topic.numPartitions()).isEqualTo(3);
            assertThat(topic.configs()).containsEntry("retention.ms", "604800000")
                .containsEntry("cleanup.policy", "delete");
        }
        assertThat(config.dlqTopic(topics).numPartitions()).isEqualTo(1);
        assertThat(config.dlqTopic(topics).configs()).containsEntry("retention.ms", "2592000000");
        assertThat(topics.names()).containsExactly("received", "ready", "status", "dlq");
    }

    @Test
    void declaresTopicsOnlyInLocalProfiles() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TopicsAdminConfig.class)
            .withPropertyValues("wa.topics.received=received", "wa.topics.ready-to-send=ready",
                "wa.topics.status=status", "wa.topics.dlq=dlq");
        for (String profile : new String[]{"local", "dev", "test"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context.getBeansOfType(NewTopic.class)).hasSize(4));
        }
        runner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context.getBeansOfType(NewTopic.class)).isEmpty());
    }

    @Test
    void checksSchemasAndSharedKafkaAtStartup() throws Exception {
        StartupChecks checks = new StartupChecks();
        checks.eventSchemasCheck(new DefaultResourceLoader()).run(null);
        ResourceLoader missing = mock(ResourceLoader.class);
        when(missing.getResource(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new org.springframework.core.io.ByteArrayResource(new byte[0]) {
                @Override public boolean exists() { return false; }
            });
        assertThatThrownBy(() -> checks.eventSchemasCheck(missing).run(null)).isInstanceOf(IllegalStateException.class);
        KafkaHealthIndicator health = mock(KafkaHealthIndicator.class);
        when(health.health()).thenReturn(Health.up().build(), Health.down().build());
        checks.sharedKafkaCheck(health).run(null);
        assertThatThrownBy(() -> checks.sharedKafkaCheck(health).run(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void healthChecksClusterAndRequiredTopics() {
        AdminClient client = mock(AdminClient.class);
        DescribeClusterResult cluster = mock(DescribeClusterResult.class);
        ListTopicsResult listing = mock(ListTopicsResult.class);
        when(client.describeCluster()).thenReturn(cluster);
        when(cluster.clusterId()).thenReturn(KafkaFuture.completedFuture("cluster"));
        when(client.listTopics()).thenReturn(listing);
        when(listing.names()).thenReturn(KafkaFuture.completedFuture(Set.of("received", "ready", "status", "dlq")),
            KafkaFuture.completedFuture(Set.of("received")));
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(topics, () -> client);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void healthReportsUnavailableCluster() {
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(topics, () -> {
            throw new IllegalStateException("unavailable");
        });
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
