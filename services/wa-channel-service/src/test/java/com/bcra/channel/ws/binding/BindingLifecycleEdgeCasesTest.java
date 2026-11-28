package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.config.BankChannelProperties;
import com.bcra.channel.ws.config.BankInternalAuthFilter;

@SpringBootTest
@AutoConfigureMockMvc
class BindingLifecycleEdgeCasesTest {

    private static final String NUMBER = "+5491112345678";
    private static final String UNKNOWN_NUMBER = "+5491199999999";
    private static final UUID CLIENT_ID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-1234567890ab");
    private static final UUID BINDING_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final String CLIENT_CONFIRMED = """
            {"actor":"CLIENTE","reason":"Pedido del cliente","confirmed":true}
            """;
    private static final String BANK_REQUEST = """
            {"actor":"BANCO","reason":"Politica de riesgo"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BindingRepository bindingRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private BankChannelProperties bankChannelProperties;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        bindingRepository.deleteAll();
    }

    @Test
    @DisplayName("block desde PENDIENTE_VERIFICACION → 409 sin cambiar el estado ni auditar")
    void blockFromPendingIsConflict() throws Exception {
        saveBinding(BindingStatus.PENDIENTE_VERIFICACION);

        postAction("block", CLIENT_CONFIRMED, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertStatusUnchanged(BindingStatus.PENDIENTE_VERIFICACION);
        assertNoLifecycleAudit();
    }

    @Test
    @DisplayName("unbind desde PENDIENTE_VERIFICACION → 409 sin cambiar el estado ni auditar")
    void unbindFromPendingIsConflict() throws Exception {
        saveBinding(BindingStatus.PENDIENTE_VERIFICACION);

        postAction("unbind", CLIENT_CONFIRMED, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertStatusUnchanged(BindingStatus.PENDIENTE_VERIFICACION);
        assertNoLifecycleAudit();
    }

    @Test
    @DisplayName("block sobre DESVINCULADO → 409")
    void blockFromUnboundIsConflict() throws Exception {
        saveBinding(BindingStatus.DESVINCULADO);

        postAction("block", CLIENT_CONFIRMED, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertStatusUnchanged(BindingStatus.DESVINCULADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"block", "unbind"})
    @DisplayName("Número inexistente → 404 NOT_FOUND")
    void unknownNumberIsNotFound(String action) throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/" + action, UNKNOWN_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENT_CONFIRMED))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"block", "unbind"})
    @DisplayName("Número con formato inválido → 400")
    void invalidNumberIsBadRequest(String action) throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/" + action, "1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENT_CONFIRMED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"block", "unbind"})
    @DisplayName("CLIENTE sin campo confirmed → 400 sin cortar el acceso")
    void clientWithoutConfirmationIsBadRequest(String action) throws Exception {
        saveBinding(BindingStatus.ACTIVO);

        postAction(action, """
                {"actor":"CLIENTE","reason":"Sin confirmar"}
                """, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertStatusUnchanged(BindingStatus.ACTIVO);
        assertNoLifecycleAudit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"block", "unbind"})
    @DisplayName("Sin actor o sin reason → 400 VALIDATION_ERROR")
    void missingActorOrReasonIsValidationError(String action) throws Exception {
        saveBinding(BindingStatus.ACTIVO);

        postAction(action, """
                {"reason":"Sin actor","confirmed":true}
                """, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        postAction(action, """
                {"actor":"CLIENTE","reason":"  ","confirmed":true}
                """, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertStatusUnchanged(BindingStatus.ACTIVO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"block", "unbind"})
    @DisplayName("BANCO con el rol de soporte o con header vacío → 403 sin cortar el acceso ni auditar")
    void bankWithWrongRoleIsForbidden(String action) throws Exception {
        saveBinding(BindingStatus.ACTIVO);
        String supportRole = bankChannelProperties.getInternalRoles().getSupport();

        postAction(action, BANK_REQUEST, supportRole)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        postAction(action, BANK_REQUEST, " ")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertStatusUnchanged(BindingStatus.ACTIVO);
        assertNoLifecycleAudit();
    }

    @Test
    @DisplayName("block por BANCO queda auditado con actor BANCO y el vínculo guarda el actor")
    void bankBlockIsAuditedWithBankActor() throws Exception {
        saveBinding(BindingStatus.ACTIVO);
        String bankPolicy = bankChannelProperties.getInternalRoles().getBankPolicy();

        postAction("block", BANK_REQUEST, bankPolicy)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADO"));

        Binding stored = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(stored.getActor()).isEqualTo("BANCO");

        List<AuditEntry> entries = lifecycleAudit();
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditAction.BINDING_BLOCKED);
            assertThat(entry.getActor()).isEqualTo("BANCO");
            assertThat(entry.getBindingId()).isEqualTo(BINDING_ID);
            assertThat(entry.getDetail()).contains("BLOQUEADO").contains("Politica de riesgo");
        });
    }

    @Test
    @DisplayName("unbind desde BLOQUEADO por BANCO → 200 y GET siguiente devuelve DESVINCULADO")
    void bankUnbindFromBlockedCutsAccessImmediately() throws Exception {
        saveBinding(BindingStatus.BLOQUEADO);
        String bankPolicy = bankChannelProperties.getInternalRoles().getBankPolicy();

        postAction("unbind", BANK_REQUEST, bankPolicy)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingId").value(BINDING_ID.toString()))
                .andExpect(jsonPath("$.status").value("DESVINCULADO"))
                .andExpect(jsonPath("$.credentialRevocationRequired").value(true));

        mockMvc.perform(get("/api/v1/bindings/{n}", NUMBER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESVINCULADO"));

        assertThat(lifecycleAudit()).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditAction.BINDING_UNBOUND);
            assertThat(entry.getActor()).isEqualTo("BANCO");
        });
    }

    @Test
    @DisplayName("Las respuestas de block/unbind no exponen clientId ni el motivo interno")
    void responsesDoNotLeakClientIdOrReason() throws Exception {
        saveBinding(BindingStatus.ACTIVO);

        postAction("block", CLIENT_CONFIRMED, null)
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(CLIENT_ID.toString()))))
                .andExpect(content().string(not(containsString("Pedido del cliente"))))
                .andExpect(jsonPath("$.message", not(containsString(BINDING_ID.toString()))));

        postAction("unbind", CLIENT_CONFIRMED, null)
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(CLIENT_ID.toString()))))
                .andExpect(jsonPath("$.message", not(containsString(BINDING_ID.toString()))))
                .andExpect(jsonPath("$.message", not(containsString("token"))));
    }

    private ResultActions postAction(
            String action, String body, String internalRole) throws Exception {
        var request = post("/api/v1/bindings/{n}/" + action, NUMBER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (internalRole != null) {
            request.header(BankInternalAuthFilter.HEADER_NAME, internalRole);
        }
        return mockMvc.perform(request);
    }

    private void assertStatusUnchanged(BindingStatus expected) {
        assertThat(bindingRepository.findByWhatsappNumber(NUMBER))
                .get()
                .extracting(Binding::getStatus)
                .isEqualTo(expected);
    }

    private void assertNoLifecycleAudit() {
        assertThat(lifecycleAudit()).isEmpty();
    }

    private List<AuditEntry> lifecycleAudit() {
        return auditRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.BINDING_BLOCKED
                        || entry.getAction() == AuditAction.BINDING_UNBOUND)
                .toList();
    }

    private Binding saveBinding(BindingStatus status) {
        Instant now = Instant.parse("2026-11-27T12:00:00Z");
        Binding binding = new Binding();
        binding.setId(BINDING_ID);
        binding.setWhatsappNumber(NUMBER);
        binding.setClientId(CLIENT_ID);
        binding.setStatus(status);
        binding.setTermsAcceptedAt(Instant.parse("2026-11-27T10:00:00Z"));
        binding.setVerifiedAt(status == BindingStatus.PENDIENTE_VERIFICACION
                ? null
                : Instant.parse("2026-11-27T11:00:00Z"));
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setActor("CLIENTE");
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        return bindingRepository.save(binding);
    }
}
