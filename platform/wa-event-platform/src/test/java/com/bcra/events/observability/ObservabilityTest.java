package com.bcra.events.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bcra.events.config.KafkaConsumerConfig;
import com.bcra.events.config.KafkaHealthIndicator;
import com.bcra.events.web.DevEventController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;

class ObservabilityTest {
    @Test
    void masksPhoneAndRejectsInvalidInput() {
        assertThat(PhoneMasker.mask("+5491122334455")).isEqualTo("+549*********55");
        assertThat(PhoneMasker.mask(null)).isEqualTo("***");
        assertThat(PhoneMasker.mask("123")).isEqualTo("***");
    }

    @Test
    void recordsEachMetricWithItsLabels() {
        var registry = new SimpleMeterRegistry();
        var metrics = new EventMetrics(registry);
        metrics.published("topic", "MessageReceived");
        metrics.consumed("topic", "MessageReceived", "success");
        metrics.invalid("consume", "schema");
        metrics.retried("topic");
        metrics.dlq("topic", "schema");
        metrics.stop(metrics.start(), "topic");
        assertThat(registry.counter("wa_events_published_total", "topic", "topic", "eventType", "MessageReceived").count()).isEqualTo(1);
        assertThat(registry.counter("wa_events_consumed_total", "topic", "topic", "eventType", "MessageReceived", "result", "success").count()).isEqualTo(1);
        assertThat(registry.counter("wa_events_invalid_total", "stage", "consume", "reason", "schema").count()).isEqualTo(1);
        assertThat(registry.counter("wa_events_retried_total", "topic", "topic").count()).isEqualTo(1);
        assertThat(registry.counter("wa_events_dlq_total", "originalTopic", "topic", "reason", "schema").count()).isEqualTo(1);
        assertThat(registry.timer("wa_event_processing_seconds", "topic", "topic").count()).isEqualTo(1);
    }

    @Test
    void smokeIsDevOnlyAndRejectsRemoteRequests() {
        assertThat(DevEventController.class.getAnnotation(Profile.class).value()).containsExactly("dev");
        var health = mock(KafkaHealthIndicator.class);
        var request = mock(HttpServletRequest.class);
        var controller = new DevEventController(health);
        when(request.getRemoteAddr()).thenReturn("192.0.2.1", "127.0.0.1", "127.0.0.1", "invalid address");
        when(health.health()).thenReturn(Health.up().build(), Health.down().build());
        assertThat(controller.smoke(request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.smoke(request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.smoke(request).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(controller.smoke(request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void actuatorExposesHealthInfoPrometheusWithOwnKafkaIndicator() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains("include: health,info,prometheus");
        assertThat(Files.readString(Path.of("pom.xml"))).contains("micrometer-registry-prometheus");
        assertThat(org.springframework.boot.actuate.health.HealthIndicator.class
            .isAssignableFrom(KafkaHealthIndicator.class)).isTrue();
    }

    @Test
    void listenerFactoryEnablesObservationForLagMetrics() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains("observation-enabled: true");

        @SuppressWarnings("unchecked")
        ConsumerFactory<Object, Object> consumerFactory = mock(ConsumerFactory.class);
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer =
            mock(ConcurrentKafkaListenerContainerFactoryConfigurer.class);
        CommonErrorHandler errorHandler = mock(CommonErrorHandler.class);
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
            new KafkaConsumerConfig().kafkaListenerContainerFactory(configurer, consumerFactory, errorHandler);
        assertThat(factory.getContainerProperties().isObservationEnabled()).isTrue();
    }
}
