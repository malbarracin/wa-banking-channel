package com.bcra.orchestrator.agent.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class H9ClientConfiguration {

    @Bean
    H9TurnClient h9TurnClient(@Value("${h9.base-url}") String baseUrl,
            @Value("${h9.service-token}") String token,
            @Value("${h9.connect-timeout-ms}") long connectTimeout,
            @Value("${h9.timeout-ms}") long timeout,
            @Value("${h9.max-retries}") int maxRetries,
            @Value("${h9.backoff-ms}") long backoff) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var webClient = WebClient.builder().baseUrl(baseUrl)
                .clientConnector(new JdkClientHttpConnector(httpClient)).build();
        return new H9TurnClient(webClient, token, Duration.ofMillis(timeout), maxRetries,
                Duration.ofMillis(backoff));
    }
}
