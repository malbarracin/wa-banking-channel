package com.bcra.users.ws.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.bcra.users.ws.audit.repository.AuditRepository;
import com.bcra.users.ws.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:usercontroller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private AuditRepository audits;
    @Autowired private ObjectMapper json;

    @Test
    void createsAuditsAndFiltersCustomerAndRejectsDuplicate() throws Exception {
        String unique = UUID.randomUUID().toString();
        String body = "{\"uniquenessValue\":\"" + unique + "\",\"displayName\":\"Ana\"}";
        String result = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").exists()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(result).get("id").asText();
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(UUID.fromString(id))).hasSize(1);
        mvc.perform(get("/api/v1/users/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.uniquenessValue").doesNotExist()).andExpect(jsonPath("$.status").value("ACTIVE"));
        long count = users.count();
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("DUPLICADO"))
                .andExpect(jsonPath("$.details[0].field").value("uniquenessValue"));
        assertThat(users.count()).isEqualTo(count);
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(UUID.fromString(id))).hasSize(1);
    }

    @Test
    void rejectsMissingFieldsAndUnknownCustomer() throws Exception {
        long count = users.count();
        long auditCount = audits.count();
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").exists());
        assertThat(users.count()).isEqualTo(count);
        assertThat(audits.count()).isEqualTo(auditCount);
        mvc.perform(get("/api/v1/users/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
