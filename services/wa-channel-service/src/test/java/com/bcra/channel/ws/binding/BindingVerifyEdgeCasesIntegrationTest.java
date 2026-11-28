package com.bcra.channel.ws.binding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.entity.VerificationAttempt;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.binding.repository.VerificationAttemptRepository;
import com.bcra.channel.ws.config.ChannelMessages;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/**
 * Casos borde del endpoint de verificación de identidad: logs sin datos de verificación, bloqueo previo por
 * intentos en la ventana, estados que no admiten verificación, anti-fraude por HTTP y re-vinculación desde
 * BLOQUEADO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class BindingVerifyEdgeCasesIntegrationTest {

    private static final String NUMBER = "+5491155550001";
    private static final UUID CLIENT_ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final String CONTACT_PHONE = "+5491155550001";
    private static final String CONTACT_EMAIL = "lucia.gomez@example.com";
    private static final String CLIENT_NAME = "Lucía Gómez";
    private static final String WRONG_DATA = "dato-secreto-incorrecto-7731";
    private static final int MAX_ATTEMPTS = 3;
    private static final int WINDOW_MINUTES = 60;
    private static final Instant T0 = Instant.parse("2026-11-27T15:00:00Z");

    @RegisterExtension
    static final WireMockExtension usersService = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void usersServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.channel.users-service.base-url", usersService::baseUrl);
        registry.add("bank.channel.users-service.timeout-ms", () -> "3000");
        registry.add("bank.channel.verification.max-attempts", () -> String.valueOf(MAX_ATTEMPTS));
        registry.add("bank.channel.verification.window-minutes", () -> String.valueOf(WINDOW_MINUTES));
    }

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private BindingRepository bindingRepository;

    @Autowired
    private VerificationAttemptRepository verificationAttemptRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private ChannelMessages channelMessages;

    @MockBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        verificationAttemptRepository.deleteAll();
        bindingRepository.deleteAll();
        usersService.resetAll();
        setNow(T0);
    }

    @Test
    @DisplayName("Ni los logs ni la respuesta contienen verificationData, con fallo y con éxito")
    void logsNeverContainVerificationData(CapturedOutput output) throws Exception {
        acceptTerms();
        stubActiveUser();

        MvcResult failed = verify(WRONG_DATA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsRemaining").value(MAX_ATTEMPTS - 1))
                .andReturn();
        MvcResult succeeded = verify(CONTACT_EMAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andReturn();

        assertThat(output.getAll()).doesNotContain(WRONG_DATA).doesNotContain(CONTACT_EMAIL);
        assertThat(failed.getResponse().getContentAsString()).doesNotContain(WRONG_DATA);
        assertThat(succeeded.getResponse().getContentAsString()).doesNotContain(CONTACT_EMAIL);
        assertThat(auditRepository.findAll())
                .extracting(AuditEntry::getDetail)
                .allSatisfy(detail -> assertThat(String.valueOf(detail))
                        .doesNotContain(WRONG_DATA)
                        .doesNotContain(CONTACT_EMAIL));
    }

    @Test
    @DisplayName("Tope ya alcanzado en la ventana → 423, BLOQUEADO y no consulta H1")
    void lockedBeforeLookupWhenAttemptsAlreadyExhausted() throws Exception {
        Binding pending = acceptTerms();
        stubActiveUser();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            saveFailedAttempt(pending.getId(), T0.minus(Duration.ofMinutes(5L + i)));
        }

        verify(CONTACT_PHONE)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"))
                .andExpect(jsonPath("$.message").value(channelMessages.verificationLocked()));

        usersService.verify(0, getRequestedFor(urlPathMatching("/api/v1/users/.*")));
        assertThat(bindingRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.BLOQUEADO);
        assertThat(auditActions()).contains(AuditAction.VERIFICATION_LOCKED);
    }

    @Test
    @DisplayName("Al agotar el tope, el BLOQUEADO y la auditoría persisten pese al 423")
    void lockByFailedAttemptsPersistsStateAndAudit() throws Exception {
        Binding pending = acceptTerms();
        stubActiveUser();

        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            verify(WRONG_DATA).andExpect(status().isOk());
        }
        verify(WRONG_DATA).andExpect(status().isLocked());

        assertThat(bindingRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.BLOQUEADO);
        List<AuditAction> actions = auditActions();
        assertThat(actions).filteredOn(AuditAction.IDENTITY_FAILED::equals).hasSize(MAX_ATTEMPTS);
        assertThat(actions).contains(AuditAction.VERIFICATION_LOCKED).doesNotContain(AuditAction.BINDING_ACTIVATED);
    }

    @Test
    @DisplayName("Vínculo ya ACTIVO → 409 CONFLICT y no consulta H1")
    void activeBindingCannotBeVerifiedAgain() throws Exception {
        acceptTerms();
        stubActiveUser();
        verify(CONTACT_PHONE).andExpect(status().isOk());
        usersService.resetRequests();

        verify(CONTACT_PHONE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        usersService.verify(0, getRequestedFor(urlPathMatching("/api/v1/users/.*")));
    }

    @Test
    @DisplayName("Vínculo BLOQUEADO sin aceptar términos de nuevo → 409 CONFLICT y no consulta H1")
    void blockedBindingRequiresTermsBeforeVerify() throws Exception {
        acceptTerms();
        stubActiveUser();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            verify(WRONG_DATA);
        }
        usersService.resetRequests();

        verify(CONTACT_PHONE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        usersService.verify(0, getRequestedFor(urlPathMatching("/api/v1/users/.*")));
    }

    @Test
    @DisplayName("Número ACTIVO con otro cliente → 409 CONFLICT con el mensaje 8.7 y ANTIFRAUD_CHECK ERROR")
    void antifraudConflictOverHttp() throws Exception {
        Binding pending = acceptTerms();
        stubActiveUser();
        doReturn(true).when(bindingRepository).existsByWhatsappNumberAndStatusAndClientIdNot(
                eq(NUMBER), eq(BindingStatus.ACTIVO), any());

        verify(CONTACT_PHONE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(channelMessages.numberAlreadyLinked()));

        assertThat(bindingRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
        assertThat(auditRepository.findAll())
                .filteredOn(entry -> entry.getAction() == AuditAction.ANTIFRAUD_CHECK)
                .extracting(AuditEntry::getResult)
                .containsExactly("ERROR");
    }

    @Test
    @DisplayName("Re-vinculación desde BLOQUEADO pasada la ventana: terms + verify emiten resultado verificable nuevo")
    void rebindFromBlockedAfterWindowIssuesNewVerifiableResult() throws Exception {
        Binding pending = acceptTerms();
        stubActiveUser();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            verify(WRONG_DATA);
        }
        assertThat(bindingRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(BindingStatus.BLOQUEADO);

        Instant later = T0.plus(Duration.ofMinutes(WINDOW_MINUTES + 1L));
        setNow(later);
        acceptTerms();

        verify(CONTACT_PHONE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andExpect(jsonPath("$.verifiableResult.identityVerified").value(true))
                .andExpect(jsonPath("$.verifiableResult.bindingId").value(pending.getId().toString()))
                .andExpect(jsonPath("$.verifiableResult.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.verifiableResult.verifiedAt").value(later.toString()));
    }

    @Test
    @DisplayName("Dentro de la ventana, re-aceptar términos tras el bloqueo no habilita nuevos intentos")
    void rebindFromBlockedWithinWindowStaysLocked() throws Exception {
        acceptTerms();
        stubActiveUser();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            verify(WRONG_DATA);
        }

        setNow(T0.plus(Duration.ofMinutes(10)));
        acceptTerms();

        verify(CONTACT_PHONE)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
    }

    @Test
    @DisplayName("Body inválido (sin clientId) → 400 VALIDATION_ERROR y no consulta H1")
    void missingClientIdReturnsValidationError() throws Exception {
        acceptTerms();

        mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationData\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        usersService.verify(0, getRequestedFor(urlPathMatching("/api/v1/users/.*")));
    }

    private void setNow(Instant now) {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private Binding acceptTerms() throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk());
        return bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
    }

    private ResultActions verify(String verificationData) throws Exception {
        return mockMvc.perform(post("/api/v1/bindings/{n}/verify", NUMBER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientId":"%s","verificationData":"%s"}
                        """.formatted(CLIENT_ID, verificationData)));
    }

    private void saveFailedAttempt(UUID bindingId, Instant attemptedAt) {
        VerificationAttempt attempt = new VerificationAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setBindingId(bindingId);
        attempt.setAttemptedAt(attemptedAt);
        attempt.setSuccess(false);
        verificationAttemptRepository.save(attempt);
    }

    private List<AuditAction> auditActions() {
        return auditRepository.findAll().stream()
                .filter(entry -> NUMBER.equals(entry.getWhatsappNumber()))
                .map(AuditEntry::getAction)
                .toList();
    }

    private void stubActiveUser() {
        usersService.stubFor(get(urlEqualTo("/api/v1/users/" + CLIENT_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "externalId": "USR-00077",
                                  "displayName": "%s",
                                  "contactEmail": "%s",
                                  "contactPhone": "%s",
                                  "status": "ACTIVE",
                                  "createdAt": "2026-01-15T10:00:00Z"
                                }
                                """.formatted(CLIENT_ID, CLIENT_NAME, CONTACT_EMAIL, CONTACT_PHONE))));
    }
}
