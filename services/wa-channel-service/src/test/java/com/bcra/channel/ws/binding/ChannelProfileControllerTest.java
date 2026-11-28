package com.bcra.channel.ws.binding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

@SpringBootTest
@AutoConfigureMockMvc
class ChannelProfileControllerTest {

    private static final String NUMBER = "+5491112345678";
    private static final UUID CLIENT_ID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-1234567890ab");
    private static final UUID BINDING_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final String USERS_PATH = "/api/v1/users/" + CLIENT_ID;
    private static final String DISPLAY_NAME = "Ana Pérez";

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
    @DisplayName("GET /profile ACTIVO → 200 con displayName de H1, estado y preferencias sin campos legales")
    void getProfileActivoReturnsPublicProfile() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO, CLIENT_ID);
        stubUserActive();

        ResultActions result = mockMvc.perform(get("/api/v1/bindings/{n}/profile", NUMBER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").value(NUMBER))
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.displayName").value(DISPLAY_NAME))
                .andExpect(jsonPath("$.bindingStatus").value("ACTIVO"))
                .andExpect(jsonPath("$.languagePreference").value("es"))
                .andExpect(jsonPath("$.notificationsEnabled").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("contactEmail", "contactPhone", "documento", "numeroDocumento");
    }

    @ParameterizedTest(name = "GET /profile con estado {0} → 404 NOT_FOUND")
    @MethodSource("nonActiveStatuses")
    @DisplayName("GET /profile sin vínculo ACTIVO → 404 NOT_FOUND")
    void getProfileWithoutActivoReturnsNotFound(BindingStatus status) throws Exception {
        if (status != null) {
            saveBinding(NUMBER, status, CLIENT_ID);
        }

        mockMvc.perform(get("/api/v1/bindings/{n}/profile", NUMBER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("PATCH /preferences válido guarda idioma y notificaciones y los devuelve")
    void patchPreferencesValidUpdatesAndReturnsProfile() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO, CLIENT_ID);
        stubUserActive();

        mockMvc.perform(patch("/api/v1/bindings/{n}/preferences", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "languagePreference": "en",
                                  "notificationsEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languagePreference").value("en"))
                .andExpect(jsonPath("$.notificationsEnabled").value(false))
                .andExpect(jsonPath("$.displayName").value(DISPLAY_NAME))
                .andExpect(jsonPath("$.bindingStatus").value("ACTIVO"));

        Binding updated = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(updated.getLanguagePreference()).isEqualTo("en");
        assertThat(updated.isNotificationsEnabled()).isFalse();
    }

    @Test
    @DisplayName("PATCH /preferences inválido → 400 VALIDATION_ERROR con details por campo")
    void patchPreferencesInvalidReturnsValidationError() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO, CLIENT_ID);

        mockMvc.perform(patch("/api/v1/bindings/{n}/preferences", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "languagePreference": "ESPAÑOL",
                                  "notificationsEnabled": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("languagePreference")))
                .andExpect(jsonPath("$.details[*].field", hasItem("notificationsEnabled")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest(name = "PATCH /preferences con estado {0} → 404 NOT_FOUND")
    @MethodSource("nonActiveStatuses")
    @DisplayName("PATCH /preferences sin vínculo ACTIVO → 404 NOT_FOUND")
    void patchPreferencesWithoutActivoReturnsNotFound(BindingStatus status) throws Exception {
        if (status != null) {
            saveBinding(NUMBER, status, CLIENT_ID);
        }

        mockMvc.perform(patch("/api/v1/bindings/{n}/preferences", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "languagePreference": "en",
                                  "notificationsEnabled": true
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /profile cuando H1 no responde → 500 INTERNAL_ERROR sin exponer error de H1")
    void getProfileWhenUsersServiceFailsReturnsInternalError() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO, CLIENT_ID);
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

        String body = mockMvc.perform(get("/api/v1/bindings/{n}/profile", NUMBER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.details").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("detalle-interno-h1-no-exponer");
    }

    static Stream<Arguments> nonActiveStatuses() {
        return Stream.of(
                Arguments.of((BindingStatus) null),
                Arguments.of(BindingStatus.PENDIENTE_VERIFICACION),
                Arguments.of(BindingStatus.BLOQUEADO),
                Arguments.of(BindingStatus.DESVINCULADO));
    }

    private void stubUserActive() {
        usersService.stubFor(get(urlEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "externalId": "USR-00042",
                                  "displayName": "%s",
                                  "contactEmail": "ana.perez@example.com",
                                  "contactPhone": "+5491112345678",
                                  "status": "ACTIVE",
                                  "createdAt": "2026-11-27T12:00:00Z"
                                }
                                """.formatted(CLIENT_ID, DISPLAY_NAME))));
    }

    private Binding saveBinding(String whatsappNumber, BindingStatus status, UUID clientId) {
        Instant now = Instant.parse("2026-11-27T12:00:00Z");
        Binding binding = new Binding();
        binding.setId(BINDING_ID);
        binding.setWhatsappNumber(whatsappNumber);
        binding.setClientId(clientId);
        binding.setStatus(status);
        binding.setTermsAcceptedAt(Instant.parse("2026-11-27T10:00:00Z"));
        binding.setVerifiedAt(status == BindingStatus.ACTIVO || status == BindingStatus.BLOQUEADO
                ? Instant.parse("2026-11-27T11:00:00Z")
                : null);
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setActor("CLIENTE");
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        return bindingRepository.save(binding);
    }
}
