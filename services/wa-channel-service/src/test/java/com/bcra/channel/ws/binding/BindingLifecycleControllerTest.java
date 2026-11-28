package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.audit.service.AuditService;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.config.BankChannelProperties;
import com.bcra.channel.ws.config.BankInternalAuthFilter;
import com.bcra.channel.ws.config.ChannelMessages;

@SpringBootTest
@AutoConfigureMockMvc
class BindingLifecycleControllerTest {

    private static final String NUMBER = "+5491112345678";
    private static final UUID CLIENT_ID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-1234567890ab");
    private static final UUID BINDING_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BindingRepository bindingRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private ChannelMessages channelMessages;

    @Autowired
    private BankChannelProperties bankChannelProperties;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        bindingRepository.deleteAll();
    }

    @Test
    @DisplayName("block CLIENTE confirmado desde ACTIVO → 200 BLOQUEADO y GET siguiente BLOQUEADO")
    void blockByClienteFromActivo() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO);

        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Solicitud del cliente","confirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingId").value(BINDING_ID.toString()))
                .andExpect(jsonPath("$.status").value("BLOQUEADO"))
                .andExpect(jsonPath("$.credentialRevocationRequired").value(true))
                .andExpect(jsonPath("$.message").value(channelMessages.blocked()));

        mockMvc.perform(get("/api/v1/bindings/{n}", NUMBER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADO"));
    }

    @Test
    @DisplayName("block BANCO sin header → 403; con bank-policy → 200")
    void blockByBancoRequiresBankPolicyRole() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO);
        String bankPolicy = bankChannelProperties.getInternalRoles().getBankPolicy();

        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"BANCO","reason":"Politica de riesgo"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(BankInternalAuthFilter.HEADER_NAME, "rol-incorrecto")
                        .content("""
                                {"actor":"BANCO","reason":"Politica de riesgo"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(BankInternalAuthFilter.HEADER_NAME, bankPolicy)
                        .content("""
                                {"actor":"BANCO","reason":"Politica de riesgo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADO"))
                .andExpect(jsonPath("$.credentialRevocationRequired").value(true));
    }

    @Test
    @DisplayName("unbind BANCO sin rol válido → 403; con bank-policy → 200")
    void unbindByBancoRequiresBankPolicyRole() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO);
        String bankPolicy = bankChannelProperties.getInternalRoles().getBankPolicy();

        mockMvc.perform(post("/api/v1/bindings/{n}/unbind", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"BANCO","reason":"Cierre por politica"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/bindings/{n}/unbind", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(BankInternalAuthFilter.HEADER_NAME, bankPolicy)
                        .content("""
                                {"actor":"BANCO","reason":"Cierre por politica"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESVINCULADO"))
                .andExpect(jsonPath("$.credentialRevocationRequired").value(true));
    }

    @Test
    @DisplayName("CLIENTE sin confirmación o sin actor → 400; inexistente → 404; estados inválidos → 409")
    void validationNotFoundAndConflict() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO);

        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Sin confirmar","confirmed":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Sin actor","confirmed":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/bindings/{n}/block", "+5491199999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"No existe","confirmed":true}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        Binding bloqueado = saveBinding(NUMBER, BindingStatus.BLOQUEADO);
        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Ya bloqueado","confirmed":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        bloqueado.setStatus(BindingStatus.DESVINCULADO);
        bindingRepository.save(bloqueado);
        mockMvc.perform(post("/api/v1/bindings/{n}/unbind", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Ya desvinculado","confirmed":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("unbind desde ACTIVO y desde BLOQUEADO → 200 DESVINCULADO")
    void unbindFromActivoAndBloqueado() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO);

        mockMvc.perform(post("/api/v1/bindings/{n}/unbind", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Cierre voluntario","confirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESVINCULADO"))
                .andExpect(jsonPath("$.message").value(channelMessages.unbound()))
                .andExpect(jsonPath("$.credentialRevocationRequired").value(true));

        bindingRepository.deleteAll();
        auditRepository.deleteAll();
        saveBinding(NUMBER, BindingStatus.BLOQUEADO);

        mockMvc.perform(post("/api/v1/bindings/{n}/unbind", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Cierre tras bloqueo","confirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESVINCULADO"));
    }

    @Test
    @DisplayName("Bloqueo y desvinculación quedan auditados con actor, fecha y estado, sin datos de verificación")
    void auditRecordsActorStatusAndReasonWithoutSecrets() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO);

        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Motivo bloqueo","confirmed":true}
                                """))
                .andExpect(status().isOk());

        List<AuditEntry> blockedEntries = auditRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.BINDING_BLOCKED)
                .toList();
        assertThat(blockedEntries).hasSize(1);
        AuditEntry blocked = blockedEntries.getFirst();
        assertThat(blocked.getActor()).isEqualTo("CLIENTE");
        assertThat(blocked.getOccurredAt()).isNotNull();
        assertThat(blocked.getResult()).isEqualTo(AuditService.RESULT_OK);
        assertThat(blocked.getDetail()).contains("BLOQUEADO").contains("Motivo bloqueo");
        assertThat(blocked.getDetail()).doesNotContain("token", "secret", "codigo", "verification");

        mockMvc.perform(post("/api/v1/bindings/{n}/unbind", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Motivo desvinculo","confirmed":true}
                                """))
                .andExpect(status().isOk());

        List<AuditEntry> unboundEntries = auditRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.BINDING_UNBOUND)
                .toList();
        assertThat(unboundEntries).hasSize(1);
        AuditEntry unbound = unboundEntries.getFirst();
        assertThat(unbound.getActor()).isEqualTo("CLIENTE");
        assertThat(unbound.getOccurredAt()).isNotNull();
        assertThat(unbound.getDetail()).contains("DESVINCULADO").contains("Motivo desvinculo");
        assertThat(unbound.getDetail()).doesNotContain("token", "secret", "codigo");
    }

    @Test
    @DisplayName("Mensajes 8.10 de bloqueo y desvinculación no contienen bindingId, token ni secretos")
    void messagesDoNotLeakSecrets() throws Exception {
        String blocked = channelMessages.blocked();
        String unbound = channelMessages.unbound();

        assertThat(blocked).doesNotContain(BINDING_ID.toString(), "token", "secret", "bindingId");
        assertThat(unbound).doesNotContain(BINDING_ID.toString(), "token", "secret", "bindingId");

        saveBinding(NUMBER, BindingStatus.ACTIVO);
        mockMvc.perform(post("/api/v1/bindings/{n}/block", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"CLIENTE","reason":"Chequeo mensaje","confirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(blocked))
                .andExpect(jsonPath("$.message", not(containsString(BINDING_ID.toString()))))
                .andExpect(jsonPath("$.message", not(containsString("token"))));
    }

    private Binding saveBinding(String whatsappNumber, BindingStatus status) {
        Instant now = Instant.parse("2026-11-27T12:00:00Z");
        Binding binding = new Binding();
        binding.setId(BINDING_ID);
        binding.setWhatsappNumber(whatsappNumber);
        binding.setClientId(CLIENT_ID);
        binding.setStatus(status);
        binding.setTermsAcceptedAt(Instant.parse("2026-11-27T10:00:00Z"));
        binding.setVerifiedAt(Instant.parse("2026-11-27T11:00:00Z"));
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setActor("CLIENTE");
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        return bindingRepository.save(binding);
    }
}
