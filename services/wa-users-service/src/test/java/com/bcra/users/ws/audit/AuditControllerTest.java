package com.bcra.users.ws.audit;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bcra.users.ws.audit.controller.AuditController;
import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditEntry;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.mapper.AuditMapperImpl;
import com.bcra.users.ws.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditController.class)
@Import(AuditMapperImpl.class)
class AuditControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AuditService service;

    @Test
    void returnsOrderedHistoryAndEmptyHistory() throws Exception {
        UUID id = UUID.randomUUID();
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        when(service.history(id)).thenReturn(List.of(
                new AuditEntry(id, AuditAction.ALTA, AuditResult.OK, null, null, null, "actor", time),
                new AuditEntry(id, AuditAction.MODIFICACION, AuditResult.OK, null, null, "displayName", "actor",
                        time.plusSeconds(1))));
        mvc.perform(get("/api/v1/users/{id}/audit", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("ALTA"))
                .andExpect(jsonPath("$[1].action").value("MODIFICACION"))
                .andExpect(jsonPath("$[1].changedFields").value("displayName"));
        mvc.perform(get("/api/v1/users/{id}/audit", UUID.randomUUID())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/users/not-a-uuid/audit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
