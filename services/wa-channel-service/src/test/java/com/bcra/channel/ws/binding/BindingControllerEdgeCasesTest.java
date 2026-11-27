package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.repository.BindingRepository;

@SpringBootTest
@AutoConfigureMockMvc
class BindingControllerEdgeCasesTest {

    private static final String NUMBER = "+5491112345678";
    private static final UUID CLIENT_ID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-1234567890ab");
    private static final UUID BINDING_ID = UUID.fromString("d4e5f6a7-b8c9-0123-def0-123456789abc");
    private static final Instant CREATED_AT = Instant.parse("2026-11-27T12:00:00Z");

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
    }

    @Test
    @DisplayName("POST /terms con número inválido → 400 BAD_REQUEST y no persiste vínculo")
    void acceptTermsWithInvalidNumberReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/terms", "abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(bindingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /terms sin accepted → 400 VALIDATION_ERROR con detalle del campo")
    void acceptTermsWithoutAcceptedReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("accepted"));

        assertThat(bindingRepository.findAll()).isEmpty();
        assertThat(auditRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /terms con cuerpo ilegible → 400 BAD_REQUEST sin persistir")
    void acceptTermsWithMalformedBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertThat(bindingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /terms sobre PENDIENTE_VERIFICACION → 409 CONFLICT (transición no permitida) sin cambios")
    void acceptTermsOnPendingReturnsConflict() throws Exception {
        saveBinding(BindingStatus.PENDIENTE_VERIFICACION, null);

        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        Binding unchanged = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(BindingStatus.PENDIENTE_VERIFICACION);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(auditRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /terms sobre ACTIVO → 409 sin modificar el vínculo ni auditar TERMS_ACCEPTED")
    void acceptTermsOnActivoKeepsBindingUntouched() throws Exception {
        saveBinding(BindingStatus.ACTIVO, CLIENT_ID);

        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El número ya tiene un vínculo activo"));

        Binding unchanged = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(BindingStatus.ACTIVO);
        assertThat(unchanged.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(unchanged.getVerifiedAt()).isNotNull();
        assertThat(auditRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /terms accepted=false sobre un vínculo DESVINCULADO no lo modifica")
    void rejectTermsOnDesvinculadoKeepsBinding() throws Exception {
        saveBinding(BindingStatus.DESVINCULADO, CLIENT_ID);

        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        Binding unchanged = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(BindingStatus.DESVINCULADO);
        assertThat(auditRepository.findAll())
                .extracting(AuditEntry::getAction)
                .containsExactly(AuditAction.TERMS_REJECTED);
    }

    @Test
    @DisplayName("POST /terms accepted=true registra el bindingId en la auditoría TERMS_ACCEPTED")
    void acceptTermsAuditReferencesBinding() throws Exception {
        saveBinding(BindingStatus.DESVINCULADO, CLIENT_ID);

        mockMvc.perform(post("/api/v1/bindings/{n}/terms", NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").doesNotExist());

        assertThat(auditRepository.findAll())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AuditAction.TERMS_ACCEPTED);
                    assertThat(entry.getBindingId()).isEqualTo(BINDING_ID);
                    assertThat(entry.getActor()).isEqualTo("CLIENTE");
                });
        Binding updated = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(updated.getActor()).isEqualTo("CLIENTE");
        assertThat(updated.getUpdatedAt()).isAfter(CREATED_AT);
    }

    @Test
    @DisplayName("GET de un vínculo existente audita STATUS_QUERIED con su bindingId")
    void getStatusAuditsBindingId() throws Exception {
        saveBinding(BindingStatus.PENDIENTE_VERIFICACION, null);

        mockMvc.perform(get("/api/v1/bindings/{n}", NUMBER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.bindingId").value(BINDING_ID.toString()))
                .andExpect(jsonPath("$.clientId").doesNotExist());

        assertThat(auditRepository.findAll())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AuditAction.STATUS_QUERIED);
                    assertThat(entry.getBindingId()).isEqualTo(BINDING_ID);
                });
    }

    @Test
    @DisplayName("GET con número demasiado largo → 400 BAD_REQUEST sin auditar")
    void getStatusWithTooLongNumberReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/bindings/{n}", "+1234567890123456").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertThat(auditRepository.findAll()).isEmpty();
    }

    private void saveBinding(BindingStatus status, UUID clientId) {
        Binding binding = new Binding();
        binding.setId(BINDING_ID);
        binding.setWhatsappNumber(NUMBER);
        binding.setClientId(clientId);
        binding.setStatus(status);
        binding.setTermsAcceptedAt(Instant.parse("2026-11-20T10:00:00Z"));
        binding.setVerifiedAt(clientId == null ? null : Instant.parse("2026-11-20T11:00:00Z"));
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setActor("BANCO");
        binding.setCreatedAt(CREATED_AT);
        binding.setUpdatedAt(CREATED_AT);
        bindingRepository.save(binding);
    }
}
