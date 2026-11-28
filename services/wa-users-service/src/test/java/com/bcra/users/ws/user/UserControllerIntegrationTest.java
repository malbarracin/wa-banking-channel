package com.bcra.users.ws.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditRepository;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("Actualiza y audita el campo autorizado y rechaza solicitudes inválidas sin cambios")
    void updatesAndAuditsOnlyAllowedFields() throws Exception {
        String body = "{\"uniquenessValue\":\"" + UUID.randomUUID() + "\",\"displayName\":\"Ana\"}";
        String created = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(created).get("id").asText());
        var previous = users.findById(id).orElseThrow().getUpdatedAt();
        mvc.perform(patch("/api/v1/users/{id}", id).header("X-Actor", "operator")
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Juan\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Juan"));
        var user = users.findById(id).orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("Juan");
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(previous);
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(id)).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditAction.MODIFICACION);
            assertThat(entry.getResult()).isEqualTo(AuditResult.OK);
            assertThat(entry.getChangedFields()).isEqualTo("displayName");
            assertThat(entry.getActor()).isEqualTo("operator");
        });
        mvc.perform(patch("/api/v1/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Otro\",\"uniquenessValue\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("uniquenessValue"));
        mvc.perform(patch("/api/v1/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactEmail\":\"invalid\",\"contactPhone\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").exists());
        assertThat(users.findById(id).orElseThrow().getDisplayName()).isEqualTo("Juan");
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(id)).hasSize(2);
        mvc.perform(patch("/api/v1/users/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Juan\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Crea y audita al cliente, protege sus datos y rechaza el duplicado")
    void createsAuditsAndFiltersCustomerAndRejectsDuplicate() throws Exception {
        String unique = UUID.randomUUID().toString();
        String body = "{\"uniquenessValue\":\"" + unique + "\",\"displayName\":\"Ana\"}";
        String result = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").exists()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(result).get("id").asText();
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(UUID.fromString(id))).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AuditAction.ALTA);
                    assertThat(entry.getResult()).isEqualTo(AuditResult.OK);
                    assertThat(entry.getOccurredAt()).isNotNull();
                });
        mvc.perform(get("/api/v1/users/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.uniquenessValue").doesNotExist()).andExpect(jsonPath("$.status").value("ACTIVE"));
        long count = users.count();
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.message").value("DUPLICADO"))
                .andExpect(jsonPath("$.details[0].field").value("uniquenessValue"));
        assertThat(users.count()).isEqualTo(count);
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(UUID.fromString(id))).hasSize(1);
    }

    @Test
    @DisplayName("Rechaza datos incompletos y devuelve el contrato de error para clientes inexistentes")
    void rejectsMissingFieldsAndUnknownCustomer() throws Exception {
        long count = users.count();
        long auditCount = audits.count();
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").exists()).andExpect(jsonPath("$.timestamp").exists());
        assertThat(users.count()).isEqualTo(count);
        assertThat(audits.count()).isEqualTo(auditCount);
        mvc.perform(get("/api/v1/users/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Informa errores de formato y campos obligatorios juntos")
    void reportsInvalidAndMissingFieldsTogether() throws Exception {
        long count = users.count();
        long auditCount = audits.count();
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactEmail\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'contactEmail')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'displayName')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'uniquenessValue')]").exists());
        assertThat(users.count()).isEqualTo(count);
        assertThat(audits.count()).isEqualTo(auditCount);
    }

    @Test
    @DisplayName("Devuelve el estado de clientes suspendidos y dados de baja")
    void returnsNonActiveCustomerStatus() throws Exception {
        String body = "{\"uniquenessValue\":\"" + UUID.randomUUID() + "\",\"displayName\":\"Ana\"}";
        String response = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(response).get("id").asText());

        for (UserStatus customerStatus : new UserStatus[] {UserStatus.SUSPENDED, UserStatus.INACTIVE}) {
            var user = users.findById(id).orElseThrow();
            user.setStatus(customerStatus);
            users.saveAndFlush(user);

            mvc.perform(get("/api/v1/users/{id}", id)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(customerStatus.name()))
                    .andExpect(jsonPath("$.uniquenessValue").doesNotExist());
        }
    }
}
