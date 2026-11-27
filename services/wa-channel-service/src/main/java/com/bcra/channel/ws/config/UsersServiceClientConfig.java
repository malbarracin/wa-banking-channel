package com.bcra.channel.ws.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient hacia wa-users-service con base URL y timeout de {@code bank.channel.users-service.*}.
 */
@Configuration
public class UsersServiceClientConfig {

    /**
     * @param properties configuración del canal (base URL y timeout de H1)
     * @return RestClient listo para inyectar en {@code UsersServiceClient}
     */
    @Bean
    RestClient usersServiceRestClient(BankChannelProperties properties) {
        BankChannelProperties.UsersService usersService = properties.getUsersService();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(usersService.getTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(usersService.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
