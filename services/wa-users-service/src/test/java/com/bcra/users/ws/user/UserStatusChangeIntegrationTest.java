package com.bcra.users.ws.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditRepository;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:userstatus;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserStatusChangeIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private AuditRepository audits;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("ACTIVE→SUSPENDED, SUSPENDED→ACTIVE y ACTIVE/SUSPENDED→INACTIVE con auditoría y consulta")
    void appliesAllowedTransitionsAndAuditsThem() throws Exception {
        UUID id = createActiveCustomer();

        patchStatus(id, "SUSPENDED", "operador-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/users/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertLastStatusAudit(id, UserStatus.ACTIVE, UserStatus.SUSPENDED, "operador-1");

        patchStatus(id, "ACTIVE", "operador-2").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertLastStatusAudit(id, UserStatus.SUSPENDED, UserStatus.ACTIVE, "operador-2");

        patchStatus(id, "INACTIVE", "operador-3").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(get("/api/v1/users/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertLastStatusAudit(id, UserStatus.ACTIVE, UserStatus.INACTIVE, "operador-3");

        UUID suspended = createActiveCustomer();
        patchStatus(suspended, "SUSPENDED", "operador-4").andExpect(status().isOk());
        patchStatus(suspended, "INACTIVE", "operador-5").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(get("/api/v1/users/{id}", suspended)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertLastStatusAudit(suspended, UserStatus.SUSPENDED, UserStatus.INACTIVE, "operador-5");

        mvc.perform(get("/api/v1/users/{id}/audit", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'CAMBIO_ESTADO')]").isArray())
                .andExpect(jsonPath("$[?(@.action == 'CAMBIO_ESTADO' && @.result == 'OK')]").exists());
    }

    @Test
    @DisplayName("INACTIVE→ACTIVE con política por defecto se rechaza sin cambiar ni auditar OK")
    void rejectsReactivationFromInactiveByDefault() throws Exception {
        UUID id = createActiveCustomer();
        patchStatus(id, "INACTIVE", "operador").andExpect(status().isOk());
        long auditCount = audits.count();
        UserStatus statusBefore = users.findById(id).orElseThrow().getStatus();

        patchStatus(id, "ACTIVE", "operador")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Transición de estado no permitida"))
                .andExpect(jsonPath("$.details[?(@.field == 'previousStatus')].message").value("INACTIVE"))
                .andExpect(jsonPath("$.details[?(@.field == 'newStatus')].message").value("ACTIVE"))
                .andExpect(jsonPath("$.details[?(@.field == 'reason')].message").value("TRANSICION_NO_PERMITIDA"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo(statusBefore);
        assertThat(audits.count()).isEqualTo(auditCount);
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(id).stream()
                .filter(entry -> entry.getAction() == AuditAction.CAMBIO_ESTADO
                        && entry.getResult() == AuditResult.OK
                        && entry.getNewStatus() == UserStatus.ACTIVE)
                .toList()).isEmpty();
    }

    @Test
    @DisplayName("Status inexistente → 400; id inexistente → 404")
    void rejectsUnknownStatusAndMissingCustomer() throws Exception {
        UUID id = createActiveCustomer();

        mvc.perform(patch("/api/v1/users/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNKNOWN\",\"actor\":\"operador\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        mvc.perform(patch("/api/v1/users/{id}/status", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"actor\":\"operador\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private UUID createActiveCustomer() throws Exception {
        String body = "{\"uniquenessValue\":\"" + UUID.randomUUID() + "\",\"displayName\":\"Ana\"}";
        String response = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions patchStatus(UUID id, String status, String actor)
            throws Exception {
        return mvc.perform(patch("/api/v1/users/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"actor\":\"" + actor + "\"}"));
    }

    private void assertLastStatusAudit(UUID id, UserStatus previous, UserStatus next, String actor) {
        var entries = audits.findByUserIdOrderByOccurredAtAsc(id);
        var last = entries.get(entries.size() - 1);
        assertThat(last.getAction()).isEqualTo(AuditAction.CAMBIO_ESTADO);
        assertThat(last.getResult()).isEqualTo(AuditResult.OK);
        assertThat(last.getPreviousStatus()).isEqualTo(previous);
        assertThat(last.getNewStatus()).isEqualTo(next);
        assertThat(last.getActor()).isEqualTo(actor);
        assertThat(last.getOccurredAt()).isNotNull();
    }
}
