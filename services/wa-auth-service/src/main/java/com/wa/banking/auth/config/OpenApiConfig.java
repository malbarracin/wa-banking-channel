package com.wa.banking.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de OpenAPI/Swagger para documentación de la API REST de acceso y sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waAuthOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WA Auth Service API")
                        .description("Servicio de acceso y sesión del ecosistema bancario WA")
                        .version("v1")
                        .contact(new Contact()
                                .name("licius-it")
                                .email("marceloalejandro.albarracin@gmail.com")));
    }

    @Bean
    public GroupedOpenApi authV1Api() {
        return GroupedOpenApi.builder()
                .group("auth-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
