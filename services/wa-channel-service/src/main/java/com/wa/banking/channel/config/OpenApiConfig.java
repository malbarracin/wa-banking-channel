package com.wa.banking.channel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de OpenAPI/Swagger para documentación de la API REST del canal WhatsApp.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waChannelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WA Channel Service API")
                        .description("Servicio de canal WhatsApp del ecosistema bancario WA")
                        .version("v1")
                        .contact(new Contact()
                                .name("licius-it")
                                .email("marceloalejandro.albarracin@gmail.com")));
    }

    @Bean
    public GroupedOpenApi channelV1Api() {
        return GroupedOpenApi.builder()
                .group("channel-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
