package com.bcra.cards.ws.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.client.RestCredencialesServiceClient;

/** Configura el transporte del cliente de validación de credenciales de H2. */
@Configuration
public class RestClientConfig {

    /** Expone el puerto de validación de H2 para los servicios de tarjetas. */
    @Bean
    public CredencialesServiceClient credencialesServiceClient(RestClient credencialesRestClient) {
        return new RestCredencialesServiceClient(credencialesRestClient);
    }

    /** Crea el cliente de H2 con URL y tiempos de espera parametrizables. */
    @Bean
    public RestClient credencialesRestClient(RestClient.Builder builder,
            @Value("${clients.wa-credentials-service.base-url}") String baseUrl,
            @Value("${clients.wa-credentials-service.connect-timeout}") Duration connectTimeout,
            @Value("${clients.wa-credentials-service.read-timeout}") Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
