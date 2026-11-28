package com.bcra.channel.ws.binding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

@SpringBootTest
@AutoConfigureMockMvc
class ChannelProfileControllerEdgeCasesTest {

    private static final String NUMBER = "+5491187654321";
    private static final String INVALID_NUMBER = "5491187654321";
    private static final UUID CLIENT_ID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-0987654321fe");
    private static final UUID BINDING_ID = UUID.fromString("e2f3a4b5-c6d7-8901-bcde-0987654321ab");
    private static final String USERS_PATH = "/api/v1/users/" + CLIENT_ID;
    private static final Instant CREATED_AT = Instant.parse("2026-11-27T12:00:00Z");
    private static final String VALID_PREFERENCES = """
            {
              "languagePreference": "en",
              "notificationsEnabled": false
            }
            """;

    @RegisterExtension
    static final WireMockExtension usersService = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void usersServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.channel.users-service.base-url", usersService::baseUrl);
        registry.add("bank.channel.users-service.timeout-ms", () -> "300");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BindingRepository bindingRepository;

    @Autowired
    private AuditRepository auditRepository;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        bindingRepository.deleteAll();
        usersService.resetAll();
    }

    @Test
    @DisplayName("PATCH /preferences con H1 caído → 500 y las preferencias no quedan guardadas")
    void patchPreferencesWhenUsersServiceFailsRollsBack() throws Exception {
        saveActiveBinding();
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse().withStatus(503).withBody("detalle-interno-h1")));

        String body = mockMvc.perform(patchPreferences(NUMBER, VALID_PREFERENCES))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("detalle-interno-h1");
        Binding stored = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(stored.getLanguagePreference()).isEqualTo("es");
        assertThat(stored.isNotificationsEnabled()).isTrue();
    }

    @Test
    @DisplayName("GET /profile cuando H1 no encuentra al cliente del vínculo → 500 INTERNAL_ERROR")
    void getProfileWhenUsersServiceReturnsNotFoundReturnsInternalError() throws Exception {
        saveActiveBinding();
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"NOT_FOUND","message":"Usuario inexistente","details":[],
                                 "timestamp":"2026-11-27T12:00:00Z"}
                                """)));

        String body = mockMvc.perform(getProfile(NUMBER))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("Usuario inexistente");
    }

    @Test
    @DisplayName("GET /profile cuando H1 supera el timeout → 500 INTERNAL_ERROR")
    void getProfileWhenUsersServiceTimesOutReturnsInternalError() throws Exception {
        saveActiveBinding();
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(userBody("Ana Gómez").withFixedDelay(1_500)));

        mockMvc.perform(getProfile(NUMBER))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("GET /profile cuando H1 no entrega displayName → 500 INTERNAL_ERROR")
    void getProfileWhenUsersServiceOmitsDisplayNameReturnsInternalError() throws Exception {
        saveActiveBinding();
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"%s","externalId":"USR-00077","status":"ACTIVE",
                                 "createdAt":"2026-11-27T12:00:00Z"}
                                """.formatted(CLIENT_ID))));

        mockMvc.perform(getProfile(NUMBER))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("GET /profile con número fuera de E.164 → 400 BAD_REQUEST")
    void getProfileWithInvalidNumberReturnsBadRequest() throws Exception {
        mockMvc.perform(getProfile(INVALID_NUMBER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /preferences con número fuera de E.164 → 400 BAD_REQUEST")
    void patchPreferencesWithInvalidNumberReturnsBadRequest() throws Exception {
        mockMvc.perform(patchPreferences(INVALID_NUMBER, VALID_PREFERENCES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /preferences con JSON mal formado → 400 BAD_REQUEST")
    void patchPreferencesWithMalformedJsonReturnsBadRequest() throws Exception {
        saveActiveBinding();

        mockMvc.perform(patchPreferences(NUMBER, "{\"languagePreference\": \"en\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /preferences con idioma vacío y sin notificaciones → 400 VALIDATION_ERROR por campo")
    void patchPreferencesWithBlankLanguageReturnsValidationError() throws Exception {
        saveActiveBinding();

        mockMvc.perform(patchPreferences(NUMBER, "{\"languagePreference\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("languagePreference")))
                .andExpect(jsonPath("$.details[*].field", hasItem("notificationsEnabled")));

        Binding stored = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(stored.getLanguagePreference()).isEqualTo("es");
    }

    @Test
    @DisplayName("PATCH /preferences con idioma y región (es-AR) → 200 y conserva estado, cliente y alta")
    void patchPreferencesWithRegionKeepsBindingIdentity() throws Exception {
        saveActiveBinding();
        usersService.stubFor(get(urlEqualTo(USERS_PATH)).willReturn(userBody("Ana Gómez")));

        mockMvc.perform(patchPreferences(NUMBER, """
                        {"languagePreference": "es-AR", "notificationsEnabled": true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languagePreference").value("es-AR"))
                .andExpect(jsonPath("$.notificationsEnabled").value(true))
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("Ana Gómez"))
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.externalId").doesNotExist());

        Binding stored = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(BindingStatus.ACTIVO);
        assertThat(stored.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(stored.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(stored.getLanguagePreference()).isEqualTo("es-AR");
    }

    private RequestBuilder getProfile(String whatsappNumber) {
        return MockMvcRequestBuilders.get("/api/v1/bindings/{n}/profile", whatsappNumber)
                .accept(MediaType.APPLICATION_JSON);
    }

    private RequestBuilder patchPreferences(String whatsappNumber, String body) {
        return patch("/api/v1/bindings/{n}/preferences", whatsappNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static ResponseDefinitionBuilder userBody(String displayName) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id":"%s","externalId":"USR-00077","displayName":"%s",
                         "contactEmail":"ana.gomez@example.com","contactPhone":"+5491187654321",
                         "status":"ACTIVE","createdAt":"2026-11-27T12:00:00Z"}
                        """.formatted(CLIENT_ID, displayName));
    }

    private void saveActiveBinding() {
        Binding binding = new Binding();
        binding.setId(BINDING_ID);
        binding.setWhatsappNumber(NUMBER);
        binding.setClientId(CLIENT_ID);
        binding.setStatus(BindingStatus.ACTIVO);
        binding.setTermsAcceptedAt(Instant.parse("2026-11-27T10:00:00Z"));
        binding.setVerifiedAt(Instant.parse("2026-11-27T11:00:00Z"));
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setActor("CLIENTE");
        binding.setCreatedAt(CREATED_AT);
        binding.setUpdatedAt(CREATED_AT);
        bindingRepository.save(binding);
    }
}
