package com.bcra.channel.ws.audit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.bcra.channel.ws.config.BankInternalAuthFilter;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

    private static final String NUMBER = "+5491112345678";
    private static final String OTHER_NUMBER = "+5491199999999";
    private static final UUID BINDING_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditRepository auditRepository;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
    }

    @Test
    @DisplayName("GET history con rol support devuelve 200 ordenado por fecha descendente")
    void historyReturnsEntriesOrderedByDateDesc() throws Exception {
        saveEntry(AuditAction.STATUS_QUERIED, Instant.parse("2026-11-27T10:00:00Z"), "SISTEMA", "Consulta");
        saveEntry(AuditAction.TERMS_ACCEPTED, Instant.parse("2026-11-27T11:00:00Z"), "CLIENTE", "Aceptó");

        mockMvc.perform(get("/api/v1/bindings/{n}/history", NUMBER)
                        .header(BankInternalAuthFilter.HEADER_NAME, "support")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action").value("TERMS_ACCEPTED"))
                .andExpect(jsonPath("$[1].action").value("STATUS_QUERIED"))
                .andExpect(jsonPath("$[0].result").value(AuditService.RESULT_OK))
                .andExpect(jsonPath("$[0].actor").value("CLIENTE"));
    }

    @Test
    @DisplayName("GET history sin header X-Internal-Role → 400 BAD_REQUEST")
    void historyWithoutHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/bindings/{n}/history", NUMBER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET history con rol inválido → 403 FORBIDDEN")
    void historyWithInvalidRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/bindings/{n}/history", NUMBER)
                        .header(BankInternalAuthFilter.HEADER_NAME, "otro-rol")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET history sin entradas → 404 NOT_FOUND")
    void historyWithoutEntriesReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/bindings/{n}/history", OTHER_NUMBER)
                        .header(BankInternalAuthFilter.HEADER_NAME, "support")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private void saveEntry(AuditAction action, Instant occurredAt, String actor, String detail) {
        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.randomUUID());
        entry.setBindingId(BINDING_ID);
        entry.setWhatsappNumber(NUMBER);
        entry.setAction(action);
        entry.setResult(AuditService.RESULT_OK);
        entry.setActor(actor);
        entry.setDetail(detail);
        entry.setOccurredAt(occurredAt);
        auditRepository.save(entry);
    }
}
