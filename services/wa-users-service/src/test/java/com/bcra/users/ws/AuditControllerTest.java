package com.bcra.users.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditEvent;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.mapper.AuditMapper;
import com.bcra.users.ws.audit.repository.AuditEventRepository;
import com.bcra.users.ws.user.entity.DocumentType;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private AuditEventRepository events;
    @Autowired private AuditMapper mapper;

    @Test
    void historyIsDescendingAndContainsNoPersonalValues() throws Exception {
        User user = new User();
        user.setTipoDocumento(DocumentType.DNI);
        user.setNumeroDocumento("12345678");
        user.setNombreCompleto("Ejemplo");
        user.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        user.setEmail("test@example.invalid");
        user.setTelefono("00000000");
        user.setEstado(UserStatus.ACTIVO);
        UUID id = users.saveAndFlush(user).getId();
        AuditEvent old = new AuditEvent(id, AuditAction.ALTA, AuditResult.EXITOSO,
                List.of("numeroDocumento", "email", "telefono"), "corr-1", "actor");
        AuditEvent recent = new AuditEvent(id, AuditAction.CAMBIO_ESTADO, AuditResult.EXITOSO,
                List.of("estado"), "corr-2", "actor");
        ReflectionTestUtils.setField(old, "createdAt", Instant.parse("2024-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(recent, "createdAt", Instant.parse("2024-01-02T00:00:00Z"));
        events.save(old);
        events.save(recent);
        String body = mvc.perform(get("/api/v1/users/{id}/audit", id).header("X-Actor-Id", "actor")
                .header("X-Actor-Role", "SOPORTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("CAMBIO_ESTADO"))
                .andExpect(jsonPath("$[0].fecha").value("2024-01-02T00:00:00Z"))
                .andExpect(jsonPath("$[0].correlationId").value("corr-2"))
                .andExpect(jsonPath("$[0].actor").value("actor"))
                .andExpect(jsonPath("$[1].action").value("ALTA"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("12345678", "test@example.invalid", "00000000");
        assertThat(mapper.safeFields(List.of("email", "test@example.invalid", "12345678")))
                .containsExactly("email");
        assertThat(mapper.safeFields(null)).isEmpty();
        mvc.perform(get("/api/v1/users/{id}/audit", id).header("X-Actor-Id", "actor")
                .header("X-Actor-Role", "OPERACIONES")).andExpect(status().isOk());
    }

    @Test
    void missingUserReturnsErrorAndInternalServiceIsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/users/{id}/audit", id).header("X-Actor-Id", "actor")
                .header("X-Actor-Role", "SOPORTE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString());
        mvc.perform(get("/api/v1/users/{id}/audit", id).header("X-Actor-Id", "actor")
                .header("X-Actor-Role", "SERVICIO_INTERNO")).andExpect(status().isForbidden());
    }

    @Test
    void auditHasNoMutationEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(put("/api/v1/users/{id}/audit", id).header("X-Actor-Id", "actor")
                .header("X-Actor-Role", "OPERACIONES")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/users/{id}/audit", id).header("X-Actor-Id", "actor")
                .header("X-Actor-Role", "OPERACIONES")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/{id}/audit", id).header("X-Actor-Id", "actor")
                .header("X-Actor-Role", "OPERACIONES")).andExpect(status().isForbidden());
    }
}
