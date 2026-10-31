package com.bcra.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
        properties = {"gateway.outbound.enabled=false", "spring.datasource.url=jdbc:h2:mem:bootstrap",
                "TWILIO_AUTH_TOKEN=synthetic-test-token",
                "TWILIO_WEBHOOK_PUBLIC_URL=https://bank.example/webhook/twilio"})
@AutoConfigureObservability
class GatewayApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthIsUpWithoutExternalServices() {
        var response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void infoIdentifiesGateway() {
        var response = restTemplate.getForEntity("/actuator/info", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("wa-twilio-gateway");
    }

    @Test
    void prometheusIsExposed() {
        var response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    @ParameterizedTest
    @ValueSource(strings = {"env", "configprops", "beans", "heapdump", "loggers", "mappings"})
    void sensitiveActuatorEndpointsAreNotExposed(String endpoint) {
        assertThat(restTemplate.getForEntity("/actuator/" + endpoint, String.class).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void errorsDoNotExposeInternalDetailsEvenWhenRequested() {
        var response = restTemplate.getForEntity("/missing?trace=true&message=true", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().has("trace")).isFalse();
        assertThat(response.getBody().has("exception")).isFalse();
        assertThat(response.getBody().has("message")).isFalse();
        assertThat(response.getBody().has("errors")).isFalse();
    }
}
