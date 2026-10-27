package com.bcra.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
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

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() {
        assertThat(restTemplate.getForEntity("/actuator/env", String.class).getStatusCode().value())
                .isEqualTo(404);
    }
}
