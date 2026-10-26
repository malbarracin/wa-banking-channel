package com.bcra.credentials.ws.config;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientConfigTest {

    private final RestClientConfig config = new RestClientConfig();

    @Test
    @DisplayName("Construye el cliente con URL y timeouts externos")
    void construyeCliente() {
        assertThat(config.usersServiceClient(RestClient.builder(), "http://h1.test",
                Duration.ofSeconds(1), Duration.ofSeconds(2))).isNotNull();
    }

    @Test
    @DisplayName("Impide timeouts infinitos o negativos")
    void rechazaTimeoutInvalido() {
        assertThatThrownBy(() -> config.usersServiceClient(RestClient.builder(), "http://h1.test",
                Duration.ZERO, Duration.ofSeconds(2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.usersServiceClient(RestClient.builder(), "http://h1.test",
                Duration.ofSeconds(1), Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }
}
