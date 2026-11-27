package com.bcra.channel.ws.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/**
 * Casos borde del cliente de H1: campos opcionales del contrato, estados no esperados y fallas de red.
 */
@SpringBootTest
class UsersServiceClientEdgeCasesTest {

    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String USERS_PATH = "/api/v1/users/" + USER_ID;
    private static final String H1_SECRET_FRAGMENT = "detalle-interno-h1-no-exponer";

    @RegisterExtension
    static final WireMockExtension usersService = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void usersServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.channel.users-service.base-url", usersService::baseUrl);
        registry.add("bank.channel.users-service.timeout-ms", () -> "800");
    }

    @Autowired
    private UsersServiceClient usersServiceClient;

    @Test
    @DisplayName("200 con uniquenessValue (campo opcional del contrato de H1) se acepta y no se expone")
    void acceptsOptionalUniquenessValueFromContract() {
        stubJson(200, """
                {
                  "id": "%s",
                  "externalId": "USR-00042",
                  "uniquenessValue": "CLI-1001",
                  "displayName": "Ana Pérez",
                  "contactEmail": "ana.perez@example.com",
                  "contactPhone": "+5491112345678",
                  "status": "ACTIVE",
                  "createdAt": "2026-11-27T12:00:00Z"
                }
                """.formatted(USER_ID));

        UserLookupResult result = usersServiceClient.findById(USER_ID);

        assertThat(result.status()).isEqualTo(UserLookupStatus.FOUND_ACTIVE);
        assertThat(result.displayName()).isEqualTo("Ana Pérez");
        assertThat(result.toString())
                .doesNotContain("Ana Pérez")
                .doesNotContain("ana.perez@example.com")
                .doesNotContain("+5491112345678");
    }

    @Test
    @DisplayName("Pide exactamente GET /api/v1/users/{id} a H1")
    void callsUsersByIdPath() {
        stubJson(404, "{\"code\":\"NOT_FOUND\",\"message\":\"x\",\"details\":[],\"timestamp\":\"2026-11-27T12:00:00Z\"}");

        usersServiceClient.findById(USER_ID);

        usersService.verify(1, getRequestedFor(urlEqualTo(USERS_PATH)));
    }

    @Test
    @DisplayName("400 de H1 (4xx distinto de 404) → UsersServiceUnavailableException sin el cuerpo")
    void throwsUnavailableOnUnexpectedClientError() {
        stubJson(400, "{\"code\":\"BAD_REQUEST\",\"message\":\"" + H1_SECRET_FRAGMENT + "\"}");

        assertThatThrownBy(() -> usersServiceClient.findById(USER_ID))
                .isInstanceOf(UsersServiceUnavailableException.class)
                .hasMessageNotContaining(H1_SECRET_FRAGMENT);
    }

    @Test
    @DisplayName("503 de H1 → UsersServiceUnavailableException")
    void throwsUnavailableOnServiceUnavailable() {
        stubJson(503, "{}");

        assertThatThrownBy(() -> usersServiceClient.findById(USER_ID))
                .isInstanceOf(UsersServiceUnavailableException.class);
    }

    @Test
    @DisplayName("200 sin status → UsersServiceUnavailableException")
    void throwsUnavailableWhenStatusMissing() {
        stubJson(200, "{\"id\":\"%s\",\"displayName\":\"Ana Pérez\"}".formatted(USER_ID));

        assertThatThrownBy(() -> usersServiceClient.findById(USER_ID))
                .isInstanceOf(UsersServiceUnavailableException.class);
    }

    @Test
    @DisplayName("200 con status fuera del enum del contrato → UsersServiceUnavailableException")
    void throwsUnavailableWhenStatusUnknown() {
        stubJson(200, "{\"id\":\"%s\",\"status\":\"DELETED\"}".formatted(USER_ID));

        assertThatThrownBy(() -> usersServiceClient.findById(USER_ID))
                .isInstanceOf(UsersServiceUnavailableException.class);
    }

    @Test
    @DisplayName("Conexión cortada por H1 → UsersServiceUnavailableException")
    void throwsUnavailableOnConnectionReset() {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> usersServiceClient.findById(USER_ID))
                .isInstanceOf(UsersServiceUnavailableException.class);
    }

    @Test
    @DisplayName("H1 caído (puerto sin escuchar) → UsersServiceUnavailableException")
    void throwsUnavailableWhenUsersServiceDown() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        UsersServiceClient clientToDownService = new UsersServiceClient(
                RestClient.builder().baseUrl("http://localhost:" + closedPort).build());

        assertThatThrownBy(() -> clientToDownService.findById(USER_ID))
                .isInstanceOf(UsersServiceUnavailableException.class)
                .hasCauseInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }

    @Test
    @DisplayName("userId nulo → NullPointerException sin llamar a H1")
    void rejectsNullUserId() {
        assertThatThrownBy(() -> usersServiceClient.findById(null))
                .isInstanceOf(NullPointerException.class);

        usersService.verify(0, getRequestedFor(urlEqualTo(USERS_PATH)));
    }

    private void stubJson(int httpStatus, String body) {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(httpStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
