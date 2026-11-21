package com.bcra.users.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditEventRepository;
import com.bcra.users.ws.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.bcra.users.ws.support.GatewayJwtTestConfig;
import com.bcra.users.ws.support.GatewayJwtTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GatewayJwtTestConfig.class)
@Transactional
class UserApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private AuditEventRepository events;
    @Autowired private ObjectMapper mapper;

    @Test
    void createsQueriesAndAuditsWithoutExposingDocument() throws Exception {
        String body = """
                {"tipoDocumento":"DNI","numeroDocumento":"12345678","nombreCompleto":"Ejemplo",
                 "fechaNacimiento":"1990-01-01","email":"test@example.invalid","telefono":"00000000"}
                """;
        var result = mvc.perform(post("/api/v1/users").header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")).header("X-Correlation-Id", "corr-test")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.numeroDocumentoEnmascarado").value("***5678"))
                .andReturn();
        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("12345678");
        UUID id = UUID.fromString(mapper.readTree(response).get("id").asText());
        assertThat(result.getResponse().getHeader("Location")).endsWith(id.toString());
        var event = events.findByUserIdOrderByCreatedAtDesc(id).getFirst();
        assertThat(event.getAction()).isEqualTo(AuditAction.ALTA);
        assertThat(event.getResultado()).isEqualTo(AuditResult.EXITOSO);
        assertThat(event.getCorrelationId()).isEqualTo("corr-test");
        assertThat(event.getActor()).contains("test-actor");
        mvc.perform(get("/api/v1/users/{id}", id).header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("12345678"))));
        mvc.perform(get("/api/v1/users/by-document").param("tipo", "DNI").param("numero", "12345678")
                .header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.numeroDocumentoEnmascarado").value("***5678"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("12345678"))));
        mvc.perform(post("/api/v1/users").header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("El usuario ya existe"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("12345678"))));
    }

    @Test
    void updatesAllowedFieldsAndAuditsOnlyChangedNames() throws Exception {
        String body = """
                {"tipoDocumento":"DNI","numeroDocumento":"87654321","nombreCompleto":"Ejemplo",
                 "fechaNacimiento":"1990-01-01","email":"old@example.invalid","telefono":"00000000"}
                """;
        var created = mvc.perform(post("/api/v1/users").header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        String update = """
                {"nombreTratamiento":"Cliente","email":"new@example.invalid",
                 "telefono":"11111111","preferencias":"ejemplo"}
                """;
        mvc.perform(patch("/api/v1/users/{id}", id).header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")).header("X-Correlation-Id", "corr-update")
                .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.invalid"));
        users.flush();
        var user = users.findById(id).orElseThrow();
        assertThat(user.getNombreTratamiento()).isEqualTo("Cliente");
        assertThat(user.getEmail()).isEqualTo("new@example.invalid");
        assertThat(user.getTelefono()).isEqualTo("11111111");
        assertThat(user.getPreferencias()).isEqualTo("ejemplo");
        var event = events.findByUserIdOrderByCreatedAtDesc(id).stream()
                .filter(item -> item.getAction() == AuditAction.ACTUALIZACION).findFirst().orElseThrow();
        assertThat(event.getResultado()).isEqualTo(AuditResult.EXITOSO);
        assertThat(event.getCorrelationId()).isEqualTo("corr-update");
        assertThat(event.getDetalle()).containsExactly("nombreTratamiento", "email", "telefono", "preferencias");
        mvc.perform(patch("/api/v1/users/{id}", id).header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other@example.invalid\",\"numeroDocumento\":\"99999999\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(users.findById(id).orElseThrow().getEmail()).isEqualTo("new@example.invalid");
        mvc.perform(patch("/api/v1/users/{id}", id).header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        for (String role : java.util.List.of("SOPORTE", "SERVICIO_INTERNO")) {
            mvc.perform(patch("/api/v1/users/{id}", id)
                    .header("Authorization", GatewayJwtTokens.bearer("test-actor", role))
                    .contentType(MediaType.APPLICATION_JSON).content(update))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/v1/users/{id}", UUID.randomUUID()).header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsIncompleteAndMissingUsers() throws Exception {
        long before = users.count();
        mvc.perform(post("/api/v1/users").header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").exists());
        assertThat(users.count()).isEqualTo(before);
        mvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()).header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/by-document").param("tipo", "DNI").header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/by-document").param("tipo", "DNI").param("numero", "absent")
                .header("Authorization", GatewayJwtTokens.bearer("test-actor", "OPERACIONES")))
                .andExpect(status().isNotFound());
    }
}
