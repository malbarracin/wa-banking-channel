package com.wa.banking.users.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de OpenAPI/Swagger para documentación de la API REST.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waUsersOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WA Users Service API")
                        .description("Servicio de usuarios del canal bancario WA")
                        .version("v1")
                        .contact(new Contact()
                                .name("licius-it")
                                .email("marceloalejandro.albarracin@gmail.com")));
    }

    @Bean
    public GroupedOpenApi usersV1Api() {
        return GroupedOpenApi.builder()
                .group("users-v1")
                .pathsToMatch("/api/v1/users/**")
                .build();
    }
}
