package com.bcra.users.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditEventRepository;
import com.bcra.users.ws.exception.InvalidStatusTransitionException;
import com.bcra.users.ws.user.dto.ChangeStatusRequest;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.repository.UserRepository;
import com.bcra.users.ws.support.GatewayJwtTestConfig;
import com.bcra.users.ws.support.GatewayJwtTokens;
import com.bcra.users.ws.user.service.StatusChangedPublisher;
import com.bcra.users.ws.user.service.UserStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GatewayJwtTestConfig.class)
class UserStatusTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuditEventRepository audits;
    @Autowired TransactionTemplate transaction;
    @Autowired UserStatusService service;
    @MockitoSpyBean StatusChangedPublisher publisher;

    @Test
    void transitionsAuditAuthorizationAndCommit() throws Exception {
        String document = UUID.randomUUID().toString().substring(0, 8);
        var creation = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users")
                .header("Authorization", GatewayJwtTokens.bearer("actor", "OPERACIONES"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"tipoDocumento":"DNI","numeroDocumento":"%s","nombreCompleto":"Ejemplo",
                        "fechaNacimiento":"1990-01-01","email":"test@example.invalid","telefono":"00000000"}
                        """.formatted(document))).andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(creation.getResponse().getContentAsString()).get("id").asText());
        change(id, "SUSPENDIDO", "OPERACIONES").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"));
        verify(publisher, times(1)).publish(any());
        var audit = audits.findByUserIdOrderByCreatedAtDesc(id).getFirst();
        assertThat(audit.getAction()).isEqualTo(AuditAction.CAMBIO_ESTADO);
        assertThat(audit.getResultado()).isEqualTo(AuditResult.EXITOSO);
        assertThat(audit.getCorrelationId()).isEqualTo("corr-test");
        change(id, "SUSPENDIDO", "OPERACIONES").andExpect(status().isConflict());
        assertThat(audits.findByUserIdOrderByCreatedAtDesc(id).getFirst().getResultado())
                .isEqualTo(AuditResult.RECHAZADO);
        change(id, "ACTIVO", "SOPORTE").andExpect(status().isForbidden());
        change(UUID.randomUUID(), "ACTIVO", "OPERACIONES").andExpect(status().isNotFound());
        change(id, "ACTIVO", "OPERACIONES").andExpect(status().isOk());
        change(id, "BAJA_LOGICA", "OPERACIONES").andExpect(status().isOk());
        change(id, "ACTIVO", "OPERACIONES").andExpect(status().isConflict());
        change(id, "SUSPENDIDO", "OPERACIONES").andExpect(status().isConflict());
        verify(publisher, times(3)).publish(any());
    }

    @Test
    void transactionRollbackDoesNotPublish() throws Exception {
        String document = UUID.randomUUID().toString().substring(0, 8);
        var creation = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users")
                .header("Authorization", GatewayJwtTokens.bearer("actor", "OPERACIONES"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"tipoDocumento":"DNI","numeroDocumento":"%s","nombreCompleto":"Ejemplo",
                        "fechaNacimiento":"1990-01-01","email":"test@example.invalid","telefono":"00000000"}
                        """.formatted(document))).andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(creation.getResponse().getContentAsString()).get("id").asText());
        reset(publisher);
        transaction.executeWithoutResult(status -> {
            try {
                change(id, "SUSPENDIDO", "OPERACIONES").andExpect(status().isOk());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            verify(publisher, never()).publish(any());
            status.setRollbackOnly();
        });
        verify(publisher, never()).publish(any());
        assertThat(users.findById(id).orElseThrow().getEstado()).isEqualTo(UserStatus.ACTIVO);
    }

    private org.springframework.test.web.servlet.ResultActions change(UUID id, String state, String role)
            throws Exception {
        return mvc.perform(patch("/api/v1/users/{id}/status", id)
                .header("Authorization", GatewayJwtTokens.bearer("actor", role))
                .header("X-Correlation-Id", "corr-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nuevoEstado\":\"" + state + "\",\"motivo\":\"prueba\"}"));
    }
}
