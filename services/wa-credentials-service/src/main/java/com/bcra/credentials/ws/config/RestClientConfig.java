package com.bcra.credentials.ws.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bcra.credentials.ws.credencial.client.RestUsersServiceClient;
import com.bcra.credentials.ws.credencial.client.UsersServiceClient;

/** Configuración externa de H1 con límites de espera finitos. */
@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    @Bean
    UsersServiceClient usersServiceClient(RestClient.Builder builder,
            @Value("${clients.wa-users-service.base-url}") String baseUrl,
            @Value("${clients.wa-users-service.connect-timeout:1s}") Duration connectTimeout,
            @Value("${clients.wa-users-service.read-timeout:2s}") Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis(connectTimeout));
        factory.setReadTimeout(timeoutMillis(readTimeout));
        return new RestUsersServiceClient(builder.clone().baseUrl(baseUrl).requestFactory(factory).build());
    }

    private int timeoutMillis(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Los timeouts deben ser positivos");
        }
        return Math.toIntExact(Math.max(1, timeout.toMillis()));
    }
}
