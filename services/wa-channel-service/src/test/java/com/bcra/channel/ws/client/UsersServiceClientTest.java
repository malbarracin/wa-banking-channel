package com.bcra.channel.ws.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

@SpringBootTest
class UsersServiceClientTest {

    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String USERS_PATH = "/api/v1/users/" + USER_ID;

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
    private UsersServiceClient usersServiceClient;

    @Test
    @DisplayName("200 ACTIVE → FOUND_ACTIVE con displayName y datos públicos")
    void returnsFoundActiveWhenUserIsActive() {
        stubUserResponse(200, "ACTIVE", "Ana Pérez");

        UserLookupResult result = usersServiceClient.findById(USER_ID);

        assertThat(result.status()).isEqualTo(UserLookupStatus.FOUND_ACTIVE);
        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.displayName()).isEqualTo("Ana Pérez");
        assertThat(result.externalId()).isEqualTo("USR-00042");
        assertThat(result.contactEmail()).isEqualTo("ana.perez@example.com");
        assertThat(result.contactPhone()).isEqualTo("+5491112345678");
    }

    @Test
    @DisplayName("200 SUSPENDED → FOUND_SUSPENDED")
    void returnsFoundSuspendedWhenUserIsSuspended() {
        stubUserResponse(200, "SUSPENDED", "Ana Pérez");

        UserLookupResult result = usersServiceClient.findById(USER_ID);

        assertThat(result.status()).isEqualTo(UserLookupStatus.FOUND_SUSPENDED);
        assertThat(result.displayName()).isEqualTo("Ana Pérez");
    }

    @Test
    @DisplayName("200 INACTIVE → FOUND_INACTIVE")
    void returnsFoundInactiveWhenUserIsInactive() {
        stubUserResponse(200, "INACTIVE", "Ana Pérez");

        UserLookupResult result = usersServiceClient.findById(USER_ID);

        assertThat(result.status()).isEqualTo(UserLookupStatus.FOUND_INACTIVE);
        assertThat(result.displayName()).isEqualTo("Ana Pérez");
    }

    @Test
    @DisplayName("404 → NOT_FOUND")
    void returnsNotFoundWhenUserMissing() {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": "NOT_FOUND",
                                  "message": "Usuario no encontrado",
                                  "details": [],
                                  "timestamp": "2026-11-27T12:00:00Z"
                                }
                                """)));

        UserLookupResult result = usersServiceClient.findById(USER_ID);

        assertThat(result.status()).isEqualTo(UserLookupStatus.NOT_FOUND);
        assertThat(result.id()).isNull();
        assertThat(result.displayName()).isNull();
    }

    @Test
    @DisplayName("500 de H1 → UsersServiceUnavailableException sin filtrar el cuerpo")
    void throwsUnavailableWhenUsersServiceReturns500() {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": "INTERNAL_ERROR",
                                  "message": "detalle-interno-h1-no-exponer",
                                  "details": [],
                                  "timestamp": "2026-11-27T12:00:00Z"
                                }
                                """)));

        assertThatThrownBy(() -> usersServiceClient.findById(USER_ID))
                .isInstanceOf(UsersServiceUnavailableException.class)
                .hasMessageNotContaining("detalle-interno-h1-no-exponer");
    }

    @Test
    @DisplayName("Timeout de H1 → UsersServiceUnavailableException")
    void throwsUnavailableWhenUsersServiceTimesOut() {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2_500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userResponseBody("ACTIVE", "Ana Pérez"))));

        assertThatThrownBy(() -> usersServiceClient.findById(USER_ID))
                .isInstanceOf(UsersServiceUnavailableException.class);
    }

    private void stubUserResponse(int httpStatus, String status, String displayName) {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(httpStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userResponseBody(status, displayName))));
    }

    private static String userResponseBody(String status, String displayName) {
        return """
                {
                  "id": "%s",
                  "externalId": "USR-00042",
                  "displayName": "%s",
                  "contactEmail": "ana.perez@example.com",
                  "contactPhone": "+5491112345678",
                  "status": "%s",
                  "createdAt": "2026-11-27T12:00:00Z"
                }
                """.formatted(USER_ID, displayName, status);
    }
}
