package com.bcra.users.ws.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "bank.users.allowed-transitions.ACTIVE=SUSPENDED,INACTIVE",
        "bank.users.allowed-transitions.SUSPENDED=ACTIVE,INACTIVE",
        "bank.users.allowed-transitions.INACTIVE=ACTIVE",
        "spring.datasource.url=jdbc:h2:mem:alternatestatus;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlternateStatusPolicyTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("Con mapa alternativo se puede reactivar desde INACTIVE")
    void allowsReactivationFromInactiveWhenConfigured() throws Exception {
        String body = "{\"uniquenessValue\":\"" + UUID.randomUUID() + "\",\"displayName\":\"Ana\"}";
        String response = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();

        mvc.perform(patch("/api/v1/users/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\",\"actor\":\"operador\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mvc.perform(patch("/api/v1/users/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\",\"actor\":\"operador\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
