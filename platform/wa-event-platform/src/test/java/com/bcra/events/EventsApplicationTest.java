package com.bcra.events;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@AutoConfigureObservability
class EventsApplicationTest {

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private KafkaProperties kafka;

    @Test
    void configuresSafeKafkaDefaultsWithoutRequiringBroker() {
        assertThat(kafka.getProducer().getAcks()).isEqualTo("all");
        assertThat(kafka.getProducer().getProperties()).containsEntry("enable.idempotence", "true");
        assertThat(kafka.getConsumer().getEnableAutoCommit()).isFalse();
        assertThat(kafka.getAdmin().isAutoCreate()).isFalse();
        assertThat(kafka.getProducer().getValueSerializer().getName()).isEqualTo(
                "org.apache.kafka.common.serialization.StringSerializer");
    }

    @Test
    void exposesHealthAndPrometheus() {
        var health = client.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
        var metrics = client.getForEntity("/actuator/prometheus", String.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("jvm_");
    }

}
