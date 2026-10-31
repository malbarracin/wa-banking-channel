package com.bcra.channel.ws.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bcra.channel.ws.vinculo.client.CredencialesServiceClient;
import com.bcra.channel.ws.vinculo.client.RestCredencialesServiceClient;
import com.bcra.channel.ws.vinculo.client.RestUsersServiceClient;
import com.bcra.channel.ws.vinculo.client.UsersServiceClient;

/** Adaptadores HTTP síncronos con límites de espera compartidos y URLs independientes. */
@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    private final String credentialsServiceToken;

    public RestClientConfig(@Value("${CREDENTIAL_H3_SERVICE_TOKEN:}") String credentialsServiceToken) {
        this.credentialsServiceToken = credentialsServiceToken;
    }

    @Bean
    UsersServiceClient usersServiceClient(RestClient.Builder builder,
            @Value("${clients.wa-users-service.base-url}") String baseUrl,
            @Value("${clients.connect-timeout:1s}") Duration connectTimeout,
            @Value("${clients.read-timeout:2s}") Duration readTimeout) {
        return new RestUsersServiceClient(crearCliente(builder, baseUrl, connectTimeout, readTimeout));
    }

    @Bean
    CredencialesServiceClient credencialesServiceClient(RestClient.Builder builder,
            @Value("${clients.credenciales-service.base-url}") String baseUrl,
            @Value("${clients.connect-timeout:1s}") Duration connectTimeout,
            @Value("${clients.read-timeout:2s}") Duration readTimeout) {
        return new RestCredencialesServiceClient(crearCliente(builder, baseUrl, connectTimeout, readTimeout)
                .mutate().defaultHeader("Authorization", "Bearer " + credentialsServiceToken).build());
    }

    private RestClient crearCliente(RestClient.Builder builder, String baseUrl,
            Duration connectTimeout, Duration readTimeout) {
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Los timeouts deben ser positivos");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.toIntExact(Math.max(1, connectTimeout.toMillis())));
        factory.setReadTimeout(Math.toIntExact(Math.max(1, readTimeout.toMillis())));
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
