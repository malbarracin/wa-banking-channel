package com.bcra.channel.ws.binding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.entity.VerificationAttempt;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.binding.repository.VerificationAttemptRepository;
import com.bcra.channel.ws.config.ChannelMessages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

@SpringBootTest
@AutoConfigureMockMvc
class BindingVerifyIntegrationTest {

    private static final String NUMBER = "+5491112345678";
    private static final String REVINC_NUMBER = "+5491198765432";
    private static final UUID CLIENT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID OTHER_CLIENT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String CONTACT_PHONE = "+5491112345678";
    private static final String CONTACT_EMAIL = "ana.perez@example.com";
    private static final String CLIENT_NAME = "Ana Pérez";
    private static final Instant T0 = Instant.parse("2026-11-27T15:00:00Z");

    @RegisterExtension
    static final WireMockExtension usersService = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void usersServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.channel.users-service.base-url", usersService::baseUrl);
        registry.add("bank.channel.users-service.timeout-ms", () -> "3000");
        registry.add("bank.channel.verification.max-attempts", () -> "3");
        registry.add("bank.channel.verification.window-minutes", () -> "60");
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
    private ChannelMessages channelMessages;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        verificationAttemptRepository.deleteAll();
        bindingRepository.deleteAll();
        usersService.resetAll();
        when(clock.instant()).thenReturn(T0);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Verificación exitosa (H1 ACTIVE) deja ACTIVO y responde verifiableResult")
    void successfulVerificationActivatesBinding() throws Exception {
        Binding pending = acceptTerms(NUMBER);
        stubActiveUser(CLIENT_ID, CLIENT_NAME, CONTACT_PHONE);

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, CONTACT_PHONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andExpect(jsonPath("$.bindingId").value(pending.getId().toString()))
                .andExpect(jsonPath("$.verifiableResult.identityVerified").value(true))
                .andExpect(jsonPath("$.verifiableResult.bindingId").value(pending.getId().toString()))
                .andExpect(jsonPath("$.verifiableResult.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.verifiableResult.verifiedAt").exists())
                .andExpect(jsonPath("$.message").value(channelMessages.welcome(CLIENT_NAME)));

        Binding saved = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BindingStatus.ACTIVO);
        assertThat(saved.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(saved.getVerifiedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("Sin términos → 409 CONFLICT y no consulta H1")
    void withoutTermsReturnsConflictAndDoesNotCallH1() throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, CONTACT_PHONE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        usersService.verify(0, getRequestedFor(urlPathMatching("/api/v1/users/.*")));
    }

    @Test
    @DisplayName("H1 404 → 404 con mensaje de alta; vínculo no queda ACTIVO")
    void h1NotFoundReturns404() throws Exception {
        acceptTerms(NUMBER);
        usersService.stubFor(get(urlEqualTo("/api/v1/users/" + CLIENT_ID))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"NOT_FOUND","message":"Usuario no encontrado","details":[],"timestamp":"2026-11-27T12:00:00Z"}
                                """)));

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, CONTACT_PHONE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(channelMessages.clientNotFound()));

        assertThat(bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
    }

    @Test
    @DisplayName("H1 SUSPENDED → 409 CONFLICT; vínculo no queda ACTIVO")
    void h1SuspendedReturnsConflict() throws Exception {
        acceptTerms(NUMBER);
        stubUser(CLIENT_ID, "SUSPENDED", CLIENT_NAME, CONTACT_PHONE);

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, CONTACT_PHONE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(channelMessages.clientSuspended()));

        assertThat(bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
    }

    @Test
    @DisplayName("H1 INACTIVE → 409 CONFLICT; vínculo no queda ACTIVO")
    void h1InactiveReturnsConflict() throws Exception {
        acceptTerms(NUMBER);
        stubUser(CLIENT_ID, "INACTIVE", CLIENT_NAME, CONTACT_PHONE);

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, CONTACT_PHONE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(channelMessages.clientSuspended()));

        assertThat(bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
    }

    @Test
    @DisplayName("Fallo con intentos restantes → attemptsRemaining; al agotar → BLOQUEADO y 423")
    void failedAttemptsThenLock() throws Exception {
        Binding pending = acceptTerms(NUMBER);
        stubActiveUser(CLIENT_ID, CLIENT_NAME, CONTACT_PHONE);

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, "dato-incorrecto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.attemptsRemaining").value(2))
                .andExpect(jsonPath("$.message").value(channelMessages.verificationFailed(2)))
                .andExpect(jsonPath("$.verifiableResult").doesNotExist());

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, "dato-incorrecto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsRemaining").value(1));

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, "dato-incorrecto")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"))
                .andExpect(jsonPath("$.message").value(channelMessages.verificationLocked()));

        Binding locked = bindingRepository.findById(pending.getId()).orElseThrow();
        assertThat(locked.getStatus()).isEqualTo(BindingStatus.BLOQUEADO);
        assertThat(verificationAttemptRepository.findAll())
                .filteredOn(attempt -> !attempt.isSuccess())
                .hasSize(3);
    }

    @Test
    @DisplayName("Intentos fuera de la ventana no cuentan (reloj inyectado)")
    void attemptsOutsideWindowDoNotCount() throws Exception {
        Binding pending = acceptTerms(NUMBER);
        stubActiveUser(CLIENT_ID, CLIENT_NAME, CONTACT_PHONE);

        VerificationAttempt oldAttempt = new VerificationAttempt();
        oldAttempt.setId(UUID.randomUUID());
        oldAttempt.setBindingId(pending.getId());
        oldAttempt.setAttemptedAt(T0.minusSeconds(3600 * 2));
        oldAttempt.setSuccess(false);
        verificationAttemptRepository.save(oldAttempt);

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, "dato-incorrecto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsRemaining").value(2));
    }

    @Test
    @DisplayName("Re-vinculación desde DESVINCULADO: terms + verify emiten resultado verificable nuevo")
    void rebindFromDesvinculadoIssuesNewVerifiableResult() throws Exception {
        Instant earlier = Instant.parse("2026-11-20T10:00:00Z");
        Binding previous = new Binding();
        previous.setId(UUID.randomUUID());
        previous.setWhatsappNumber(REVINC_NUMBER);
        previous.setClientId(OTHER_CLIENT_ID);
        previous.setStatus(BindingStatus.DESVINCULADO);
        previous.setTermsAcceptedAt(earlier);
        previous.setVerifiedAt(earlier.plusSeconds(60));
        previous.setLanguagePreference("es");
        previous.setNotificationsEnabled(true);
        previous.setActor("CLIENTE");
        previous.setCreatedAt(earlier);
        previous.setUpdatedAt(earlier);
        bindingRepository.save(previous);

        mockMvc.perform(post("/api/v1/bindings/{n}/terms", REVINC_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE_VERIFICACION"));

        stubActiveUser(CLIENT_ID, CLIENT_NAME, CONTACT_PHONE);

        MvcResult result = mockMvc.perform(post("/api/v1/bindings/{n}/verify", REVINC_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, CONTACT_PHONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andExpect(jsonPath("$.verifiableResult.identityVerified").value(true))
                .andExpect(jsonPath("$.verifiableResult.clientId").value(CLIENT_ID.toString()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("verifiableResult").get("verifiedAt").asText()).isEqualTo(T0.toString());
        assertThat(body.get("bindingId").asText()).isEqualTo(previous.getId().toString());

        Binding rebound = bindingRepository.findByWhatsappNumber(REVINC_NUMBER).orElseThrow();
        assertThat(rebound.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(rebound.getVerifiedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("Auditoría de onboarding completo sin verificationData en entradas")
    void onboardingAuditContainsRequiredActionsWithoutVerificationData() throws Exception {
        acceptTerms(NUMBER);
        stubActiveUser(CLIENT_ID, CLIENT_NAME, CONTACT_PHONE);

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, CONTACT_EMAIL)))
                .andExpect(status().isOk());

        List<AuditAction> actions = auditRepository.findAll().stream()
                .filter(entry -> NUMBER.equals(entry.getWhatsappNumber()))
                .map(AuditEntry::getAction)
                .toList();
        assertThat(actions).contains(
                AuditAction.TERMS_ACCEPTED,
                AuditAction.IDENTITY_VERIFIED,
                AuditAction.ANTIFRAUD_CHECK,
                AuditAction.BINDING_ACTIVATED,
                AuditAction.VERIFIABLE_RESULT_ISSUED);

        for (AuditEntry entry : auditRepository.findAll()) {
            String detail = entry.getDetail() == null ? "" : entry.getDetail();
            assertThat(detail).doesNotContain(CONTACT_EMAIL);
            assertThat(entry.toString()).doesNotContain(CONTACT_EMAIL);
        }
        for (VerificationAttempt attempt : verificationAttemptRepository.findAll()) {
            assertThat(attempt.toString()).doesNotContain(CONTACT_EMAIL);
        }
    }

    @Test
    @DisplayName("Mensaje de bienvenida incluye nombre y no incluye bindingId, clientId ni token")
    void welcomeMessageHasNameWithoutSecrets() throws Exception {
        Binding pending = acceptTerms(NUMBER);
        stubActiveUser(CLIENT_ID, CLIENT_NAME, CONTACT_PHONE);

        MvcResult result = mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(CLIENT_ID, CONTACT_PHONE)))
                .andExpect(status().isOk())
                .andReturn();

        String message = objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asText();
        assertThat(message).contains(CLIENT_NAME);
        assertThat(message).doesNotContain(pending.getId().toString());
        assertThat(message).doesNotContain(CLIENT_ID.toString());
        assertThat(message.toLowerCase()).doesNotContain("token");
    }

    private Binding acceptTerms(String number) throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/terms", number)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk());
        return bindingRepository.findByWhatsappNumber(number).orElseThrow();
    }

    private static String verifyBody(UUID clientId, String verificationData) {
        return """
                {"clientId":"%s","verificationData":"%s"}
                """.formatted(clientId, verificationData);
    }

    private void stubActiveUser(UUID userId, String displayName, String contactPhone) {
        stubUser(userId, "ACTIVE", displayName, contactPhone);
    }

    private void stubUser(UUID userId, String status, String displayName, String contactPhone) {
        usersService.stubFor(get(urlEqualTo("/api/v1/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "externalId": "USR-00042",
                                  "displayName": "%s",
                                  "contactEmail": "%s",
                                  "contactPhone": "%s",
                                  "status": "%s",
                                  "createdAt": "2026-01-15T10:00:00Z"
                                }
                                """.formatted(userId, displayName, CONTACT_EMAIL, contactPhone, status))));
    }
}
