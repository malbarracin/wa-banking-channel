package com.bcra.events;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.admin.auto-create=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
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
        assertThat(client.getForEntity("/actuator/info", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var health = client.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
        var metrics = client.getForEntity("/actuator/prometheus", String.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("jvm_");
        assertThat(metrics.getBody()).contains("platform_kafka_delivery_errors_total", "platform_kafka_dlq_published_total");
        assertThat(health.getHeaders().getFirst("correlationId")).isNotBlank();
    }

    @Test
    @DisplayName("HTTP conserva la correlación válida y sustituye valores privados inválidos")
    void preservesHttpCorrelationAndReplacesUnsafeValueAcrossRequests() {
        String correlationId = UUID.randomUUID().toString();
        var headers = new HttpHeaders();
        headers.set("correlationId", correlationId);
        var valid = client.exchange("/actuator/health", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(valid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(valid.getHeaders().getFirst("correlationId")).isEqualTo(correlationId);

        headers.set("correlationId", "synthetic-private-value");
        var unsafe = client.exchange("/actuator/health", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        String replacement = unsafe.getHeaders().getFirst("correlationId");
        assertThat(unsafe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(UUID.fromString(replacement)).isNotNull();
        assertThat(replacement).isNotEqualTo(correlationId);
        assertThat(unsafe.toString()).doesNotContain("synthetic-private-value");
    }
}
