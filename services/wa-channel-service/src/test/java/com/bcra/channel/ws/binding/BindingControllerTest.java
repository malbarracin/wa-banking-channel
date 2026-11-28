package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.bcra.channel.ws.config.ChannelMessages;

@SpringBootTest
@AutoConfigureMockMvc
class BindingControllerTest {

    private static final String NUMBER = "+5491112345678";
    private static final String OTHER_NUMBER = "+5491198765432";
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

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        bindingRepository.deleteAll();
    }

    @Test
    @DisplayName("GET sin registro → 200 SIN_VINCULO sin clientId ni bindingId")
    void getStatusWithoutBindingReturnsSinVinculo() throws Exception {
        mockMvc.perform(get("/api/v1/bindings/{n}", NUMBER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").value(NUMBER))
                .andExpect(jsonPath("$.status").value("SIN_VINCULO"))
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.bindingId").doesNotExist());

        assertThat(auditRepository.findAll())
                .extracting(AuditEntry::getAction)
                .containsExactly(AuditAction.STATUS_QUERIED);
    }

    @Test
    @DisplayName("GET ACTIVO → 200 con clientId y bindingId")
    void getStatusActivoReturnsIds() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO, CLIENT_ID, Instant.parse("2026-11-27T10:00:00Z"));

        mockMvc.perform(get("/api/v1/bindings/{n}", NUMBER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.bindingId").value(BINDING_ID.toString()));
    }

    @Test
    @DisplayName("GET BLOQUEADO → 200 status BLOQUEADO")
    void getStatusBloqueadoReturnsBloqueado() throws Exception {
        saveBinding(NUMBER, BindingStatus.BLOQUEADO, CLIENT_ID, Instant.parse("2026-11-27T10:00:00Z"));

        mockMvc.perform(get("/api/v1/bindings/{n}", NUMBER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADO"))
                .andExpect(jsonPath("$.bindingId").value(BINDING_ID.toString()));
    }

    @Test
    @DisplayName("Número con formato inválido → 400 BAD_REQUEST")
    void invalidNumberReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/bindings/{n}", "5491112345678").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("formato inválido")))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /terms accepted=true crea PENDIENTE_VERIFICACION y audita TERMS_ACCEPTED")
    void acceptTermsCreatesPendingBinding() throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.bindingId").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());

        Binding saved = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
        assertThat(saved.getTermsAcceptedAt()).isNotNull();
        assertThat(saved.getClientId()).isNull();
        assertThat(saved.getVerifiedAt()).isNull();

        assertThat(auditRepository.findAll())
                .extracting(AuditEntry::getAction)
                .containsExactly(AuditAction.TERMS_ACCEPTED);
        assertThat(auditRepository.findAll().getFirst().getResult()).isEqualTo(AuditService.RESULT_OK);
    }

    @Test
    @DisplayName("POST /terms accepted=false → 400 mensaje 8.2 y no persiste vínculo")
    void rejectTermsReturnsMessageAndDoesNotPersist() throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(channelMessages.termsRejected()))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(bindingRepository.findAll()).isEmpty();
        assertThat(auditRepository.findAll())
                .extracting(AuditEntry::getAction)
                .containsExactly(AuditAction.TERMS_REJECTED);
        assertThat(auditRepository.findAll().getFirst().getResult()).isEqualTo(AuditService.RESULT_ERROR);
    }

    @Test
    @DisplayName("POST /terms sobre ACTIVO → 409 CONFLICT")
    void acceptTermsOnActivoReturnsConflict() throws Exception {
        saveBinding(NUMBER, BindingStatus.ACTIVO, CLIENT_ID, Instant.parse("2026-11-27T10:00:00Z"));

        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /terms sobre DESVINCULADO vuelve a PENDIENTE_VERIFICACION con clientId y verifiedAt limpios")
    void acceptTermsOnDesvinculadoResetsIdentity() throws Exception {
        Binding existing = saveBinding(
                OTHER_NUMBER, BindingStatus.DESVINCULADO, CLIENT_ID, Instant.parse("2026-11-20T10:00:00Z"));
        existing.setVerifiedAt(Instant.parse("2026-11-20T11:00:00Z"));
        bindingRepository.save(existing);

        mockMvc.perform(post("/api/v1/bindings/{n}/terms", OTHER_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.bindingId").value(BINDING_ID.toString()));

        Binding updated = bindingRepository.findByWhatsappNumber(OTHER_NUMBER).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
        assertThat(updated.getClientId()).isNull();
        assertThat(updated.getVerifiedAt()).isNull();
        assertThat(updated.getTermsAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST /terms sobre BLOQUEADO vuelve a PENDIENTE_VERIFICACION con clientId y verifiedAt limpios")
    void acceptTermsOnBloqueadoResetsIdentity() throws Exception {
        Binding existing = saveBinding(
                OTHER_NUMBER, BindingStatus.BLOQUEADO, CLIENT_ID, Instant.parse("2026-11-20T10:00:00Z"));
        existing.setVerifiedAt(Instant.parse("2026-11-20T11:00:00Z"));
        bindingRepository.save(existing);

        mockMvc.perform(post("/api/v1/bindings/{n}/terms", OTHER_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE_VERIFICACION"));

        Binding updated = bindingRepository.findByWhatsappNumber(OTHER_NUMBER).orElseThrow();
        assertThat(updated.getClientId()).isNull();
        assertThat(updated.getVerifiedAt()).isNull();
        assertThat(updated.getTermsAcceptedAt()).isNotNull();
    }

    private Binding saveBinding(
            String whatsappNumber, BindingStatus status, UUID clientId, Instant termsAcceptedAt) {
        Instant now = Instant.parse("2026-11-27T12:00:00Z");
        Binding binding = new Binding();
        binding.setId(BINDING_ID);
        binding.setWhatsappNumber(whatsappNumber);
        binding.setClientId(clientId);
        binding.setStatus(status);
        binding.setTermsAcceptedAt(termsAcceptedAt);
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
