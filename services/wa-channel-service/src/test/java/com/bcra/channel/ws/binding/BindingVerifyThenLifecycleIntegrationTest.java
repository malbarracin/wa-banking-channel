package com.bcra.channel.ws.binding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.binding.repository.VerificationAttemptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/**
 * Recorre en el mismo contexto los endpoints de verificación y de ciclo de vida del controlador de vínculos:
 * el bloqueo o la desvinculación posteriores a una verificación cortan el acceso, y la re-vinculación emite
 * un resultado verificable nuevo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BindingVerifyThenLifecycleIntegrationTest {

    private static final String NUMBER = "+5491155554444";
    private static final UUID CLIENT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String CONTACT_PHONE = "+5491155554444";
    private static final String CLIENT_NAME = "Ana Pérez";

    @RegisterExtension
    static final WireMockExtension usersService = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void usersServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.channel.users-service.base-url", usersService::baseUrl);
        registry.add("bank.channel.users-service.timeout-ms", () -> "3000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BindingRepository bindingRepository;

    @Autowired
    private VerificationAttemptRepository verificationAttemptRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        usersService.resetAll();
        stubActiveUser();
    }

    /** Otros tests comparten este contexto y solo borran vínculos: no dejar intentos que los referencien. */
    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        auditRepository.deleteAll();
        verificationAttemptRepository.deleteAll();
        bindingRepository.deleteAll();
    }

    @Test
    @DisplayName("verify → block CLIENTE → el vínculo queda BLOQUEADO y un nuevo verify se rechaza con 409 sin consultar H1")
    void blockAfterVerificationCutsAccess() throws Exception {
        acceptTerms();
        verify();

        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Solicitud del cliente","confirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADO"))
                .andExpect(jsonPath("$.credentialRevocationRequired").value(true));
        usersService.resetRequests();

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        usersService.verify(0, getRequestedFor(urlPathMatching("/api/v1/users/.*")));
        Binding blocked = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(blocked.getStatus()).isEqualTo(BindingStatus.BLOQUEADO);
    }

    @Test
    @DisplayName("verify → unbind CLIENTE → terms + verify emiten un resultado verificable nuevo")
    void unbindAfterVerificationThenRebindIssuesNewResult() throws Exception {
        acceptTerms();
        JsonNode first = verify();

        mockMvc.perform(post("/api/v1/bindings/{n}/unbind", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Ya no uso el canal","confirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESVINCULADO"))
                .andExpect(jsonPath("$.credentialRevocationRequired").value(true));
        assertThat(bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.DESVINCULADO);

        acceptTerms();
        assertThat(bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
        JsonNode second = verify();

        assertThat(second.path("verifiableResult").path("identityVerified").asBoolean()).isTrue();
        assertThat(second.path("verifiableResult").path("clientId").asText()).isEqualTo(CLIENT_ID.toString());
        assertThat(second.path("verifiableResult").path("verifiedAt").asText()).isNotBlank();
        assertThat(second.path("verifiableResult").toString())
                .isNotEqualTo(first.path("verifiableResult").toString());
        assertThat(bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.ACTIVO);
    }

    private void acceptTerms() throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk());
    }

    private JsonNode verify() throws Exception {
        String body = mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static String verifyBody() {
        return """
                {"clientId":"%s","verificationData":"%s"}
                """.formatted(CLIENT_ID, CONTACT_PHONE);
    }

    private void stubActiveUser() {
        usersService.stubFor(get(urlEqualTo("/api/v1/users/" + CLIENT_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "externalId": "USR-00042",
                                  "displayName": "%s",
                                  "contactEmail": "ana.perez@example.com",
                                  "contactPhone": "%s",
                                  "status": "ACTIVE",
                                  "createdAt": "2026-01-15T10:00:00Z"
                                }
                                """.formatted(CLIENT_ID, CLIENT_NAME, CONTACT_PHONE))));
    }
}
