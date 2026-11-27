package com.bcra.users.ws.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI usersOpenApi() {
        return new OpenAPI().info(new Info().title("Users service").version("v1"));
    }
}
