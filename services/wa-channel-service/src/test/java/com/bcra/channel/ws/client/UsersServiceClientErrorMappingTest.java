package com.bcra.channel.ws.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/**
 * Verifica que 500 y timeout de H1 terminan en 500 INTERNAL_ERROR sin exponer el cuerpo de H1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsersServiceClientErrorMappingTest.UsersLookupProbeController.class)
class UsersServiceClientErrorMappingTest {

    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String USERS_PATH = "/api/v1/users/" + USER_ID;
    private static final String H1_SECRET_FRAGMENT = "detalle-interno-h1-no-exponer";

    @RegisterExtension
    static final WireMockExtension usersService = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void usersServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.channel.users-service.base-url", usersService::baseUrl);
        registry.add("bank.channel.users-service.timeout-ms", () -> "800");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("500 de H1 → 500 INTERNAL_ERROR sin el cuerpo de H1")
    void mapsUsersService500ToInternalErrorWithoutBodyLeak() throws Exception {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": "INTERNAL_ERROR",
                                  "message": "%s",
                                  "details": [],
                                  "timestamp": "2026-11-27T12:00:00Z"
                                }
                                """.formatted(H1_SECRET_FRAGMENT))));

        mockMvc.perform(get("/probe/users/{id}", USER_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Error interno"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.message", not(containsString(H1_SECRET_FRAGMENT))))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("Timeout de H1 → 500 INTERNAL_ERROR sin filtrar el cuerpo de H1")
    void mapsUsersServiceTimeoutToInternalError() throws Exception {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2_500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "externalId": "USR-00042",
                                  "displayName": "Ana Pérez",
                                  "contactEmail": "ana.perez@example.com",
                                  "contactPhone": "+5491112345678",
                                  "status": "ACTIVE",
                                  "createdAt": "2026-11-27T12:00:00Z"
                                }
                                """.formatted(USER_ID))));

        mockMvc.perform(get("/probe/users/{id}", USER_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Error interno"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @RestController
    static class UsersLookupProbeController {

        private final UsersServiceClient usersServiceClient;

        UsersLookupProbeController(UsersServiceClient usersServiceClient) {
            this.usersServiceClient = usersServiceClient;
        }

        @GetMapping("/probe/users/{id}")
        UserLookupResult lookup(@PathVariable("id") UUID id) {
            return usersServiceClient.findById(id);
        }
    }
}
