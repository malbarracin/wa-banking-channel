package com.wa.banking.accounts.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de OpenAPI/Swagger para documentación de la API REST de cuentas.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waAccountsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WA Accounts Service API")
                        .description("Servicio de cuentas del ecosistema bancario WA (H4 piloto F1)")
                        .version("v1")
                        .contact(new Contact()
                                .name("licius-it")
                                .email("marceloalejandro.albarracin@gmail.com")))
                .components(new Components()
                        .addSecuritySchemes("sessionCredential", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque")
                                .description("Token opaco emitido por H2. Requiere también el header X-Credential-Id.")))
                .addSecurityItem(new SecurityRequirement().addList("sessionCredential"));
    }

    @Bean
    public GroupedOpenApi accountsV1Api() {
        return GroupedOpenApi.builder()
                .group("accounts-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
